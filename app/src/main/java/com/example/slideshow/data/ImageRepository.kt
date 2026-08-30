package com.example.slideshow.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ImageRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("images", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ImageRepository"
    }

    // Максимальная глубина обхода дерева, чтобы защититься от StackOverflow
    // на глубоких/битых файловых системах.
    private val maxDepth = 32

    // Ограничение количества собираемых файлов из одного дерева, чтобы не
    // выгрузить всю флешку в память и не зависнуть на гигантских каталогах.
    private val maxCollectedFiles = 5000

    // Поток изменений списка: экраны подписываются, чтобы не держать кэш
    // и всегда видеть актуальный набор картинок (слайдшоу в т.ч.).
    private val _uris = MutableSharedFlow<List<Uri>>(replay = 1, extraBufferCapacity = 1)
    val uris: Flow<List<Uri>> = _uris.asSharedFlow()

    // Emits current list to any collector that subscribes later.
    fun observeUris(): Flow<List<Uri>> = uris.flowOn(Dispatchers.IO)

    // Adds a file or a tree. If it is a directory (tree URI), walks it recursively
    // and adds only images (MIME image/*), skipping video.
    fun addSource(uri: Uri): Int {
        val existing = getUris().toMutableSet()
        val added = mutableListOf<Uri>()

        if (isTreeUri(uri)) {
            val doc = DocumentFile.fromTreeUri(context, uri)
            val quota = intArrayOf(maxCollectedFiles)
            collectImages(doc, 0, quota) { child ->
                quota[0]--
                if (quota[0] < 0) return@collectImages
                if (existing.add(child.uri)) added.add(child.uri)
            }
            if (quota[0] <= 0) {
                // Обход упёрся в лимит — сообщаем в лог, чтобы обрезка не была тихой.
                Log.w(TAG, "Достигнут лимит $maxCollectedFiles файлов при обходе дерева: $uri")
            }
        } else {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime.startsWith("image/")) {
                val stored = makePersistent(uri)
                // Дубли: одна и та же картинка дважды не добавляется (URI стабилен).
                if (existing.add(stored)) added.add(stored)
            }
        }
        saveUris(existing.toList())
        return added.size
    }

    // Photo Picker grants a temporary Uri that is lost after a restart. Fix:
    // try to take a persistable grant; if that fails (Photo Picker), copy the
    // file into internal storage and return the internal Uri.
    private fun makePersistent(uri: Uri): Uri {
        // Внутренние копии уже персистентны.
        if (uri.scheme == "file" && isInternalCopy(uri)) return uri
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uri
        } catch (e: SecurityException) {
            copyToInternal(uri)
        }
    }

    private fun copyToInternal(uri: Uri): Uri {
        val input = try {
            context.contentResolver.openInputStream(uri) ?: return uri
        } catch (_: Exception) {
            return uri
        }
        val ext = validExtension(queryDisplayName(uri))
        // Стабильное имя из SHA-256 исходного URI: повторное добавление той
        // же картинки даёт тот же файл и отсеивается как дубликат; коллизии
        // практически невозможны (в отличие от hashCode).
        val name = "${sha256(uri.toString()).substring(0, 24)}.$ext"
        val file = File(context.filesDir, "slideshow/$name")
        if (file.exists()) {
            input.close()
            return Uri.fromFile(file)
        }
        file.parentFile?.mkdirs()
        // Пишем во временный файл и публикуем атомарным rename: если копия
        // оборвётся, в списке не останется «полузаписанного» битого файла.
        val tmp = File(file.parentFile, "$name.tmp")
        return try {
            tmp.outputStream().use { outs -> input.copyTo(outs) }
            if (tmp.renameTo(file)) {
                Uri.fromFile(file)
            } else {
                tmp.delete()
                uri
            }
        } catch (_: Exception) {
            tmp.delete()
            uri
        } finally {
            input.close()
        }
    }

    // Возвращает только «чистое» расширение (латиница/цифры, длина ≤ 5),
    // иначе фолбэк на "jpg". Не даёт "photo.v1" дать расширение "v1".
    private fun validExtension(displayName: String): String {
        val lastDot = displayName.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == displayName.length - 1) return "jpg"
        val ext = displayName.substring(lastDot + 1)
        return if (ext.matches(Regex("[a-zA-Z0-9]{1,5}"))) ext.lowercase() else "jpg"
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Non-suspend имя файла (для использования в синхронных путях).
    private fun queryDisplayName(uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)
                ?: uri.lastPathSegment ?: ""
            else uri.lastPathSegment ?: ""
        } ?: uri.lastPathSegment ?: ""

    private fun isInternalCopy(uri: Uri): Boolean =
        uri.scheme == "file" && uri.path?.startsWith(context.filesDir.path) == true

    private fun isTreeUri(uri: Uri): Boolean {
        return try {
            DocumentsContract.isTreeUri(uri)
        } catch (_: Exception) {
            false
        }
    }

    private fun collectImages(doc: DocumentFile?, depth: Int, quota: IntArray, sink: (DocumentFile) -> Unit) {
        if (doc == null || quota[0] <= 0) return
        if (depth > maxDepth) return
        if (doc.isDirectory) {
            doc.listFiles().forEach { collectImages(it, depth + 1, quota, sink) }
        } else if (doc.isFile) {
            val mime = doc.type ?: ""
            // image/* includes HEIF/HEIC, WebP, AVIF, GIF, BMP, SVG; video is skipped
            if (mime.startsWith("image/")) sink(doc)
        }
    }

    fun getUris(): List<Uri> = loadUris()

    // Removes URIs whose access grant is gone (e.g. expired Photo Picker grants
    // after a reinstall). Otherwise the list would keep dead "✕" tiles.
    suspend fun retainReadableUris(): List<Uri> = withContext(Dispatchers.IO) {
        val current = getUris()
        val valid = current.filter { uri ->
            if (isInternalCopy(uri)) {
                // Внутренние копии всегда доступны, если файл существует локально.
                uri.path?.let { File(it).exists() } == true
            } else {
                try {
                    // Для внешних URI getType быстрее, чем полное открытие потока;
                    // для просроченного гранта доступа вернёт null или бросит.
                    !context.contentResolver.getType(uri).isNullOrEmpty()
                } catch (_: Exception) {
                    false
                }
            }
        }
        if (valid.size != current.size) saveUris(valid)
        valid
    }

    fun removeUri(uri: Uri) {
        deleteInternalCopy(uri)
        saveUris(getUris().filterNot { it == uri })
    }

    fun clear() {
        getUris().forEach { deleteInternalCopy(it) }
        saveUris(emptyList())
    }

    private fun deleteInternalCopy(uri: Uri) {
        if (isInternalCopy(uri)) {
            uri.path?.let { File(it).delete() }
        }
    }

    private fun loadUris(): List<Uri> {
        val raw = prefs.getString("uris", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    try {
                        add(Uri.parse(arr.getString(i)))
                    } catch (_: Exception) {
                        // Пропускаем битые записи.
                    }
                }
            }
        } catch (_: Exception) {
            // Фолбэк на старый формат (перенос строки) при обновлении приложения.
            raw.split("\n").filter { it.isNotBlank() }.map { Uri.parse(it) }
        }
    }

    private fun saveUris(uris: List<Uri>) {
        val json = JSONArray().apply {
            uris.forEach { put(it.toString()) }
        }
        prefs.edit { putString("uris", json.toString()) }
        _uris.tryEmit(uris)
    }
}
