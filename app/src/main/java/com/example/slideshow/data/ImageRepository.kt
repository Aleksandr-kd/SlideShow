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

    // Размер порции при стриминге обхода дерева: каждые N найденных фото
    // список публикуется в поток, чтобы UI наполнялся постепенно, а не
    // «замирал» до конца обхода флешки.
    private val batchSize = 40

    // Поток изменений списка: экраны подписываются, чтобы не держать кэш
    // и всегда видеть актуальный набор картинок (слайдшоу в т.ч.).
    // extraBufferCapacity больше 1, чтобы порции при стриминге не терялись.
    private val _uris = MutableSharedFlow<List<Uri>>(replay = 1, extraBufferCapacity = 64)
    val uris: Flow<List<Uri>> = _uris.asSharedFlow()

    // Однократная очистка «сиротских» tmp-файлов от прерванных копий (процесс
    // убит между созданием tmp и атомарным renameTo) — иначе они копятся навсегда.
    init {
        runCatching {
            File(context.filesDir, "slideshow").listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".tmp")) f.delete()
            }
        }
    }

    // Расширения видео, которые надо исключать в любом случае. Нужны как fallback,
    // когда провайдер не отдаёт MIME (тип = null или application/octet-stream):
    // охватывают популярные контейнеры, чтобы видео не просочилось в слайд-шоу.
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "mov", "avi", "wmv", "flv", "webm", "3gp", "ts", "m2ts",
        "mpg", "mpeg", "m4p", "ogv", "vob"
    )

    // Возвращает true, если на входе видео. Проверяем и MIME, и расширение —
    // надёжнее, чем только MIME (у части провайдеров тип может быть null).
    private fun isVideo(fileName: String?, mime: String?): Boolean {
        if (!mime.isNullOrEmpty() && mime.startsWith("video/")) return true
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return ext in videoExtensions
    }

    // Возвращает true, если запись — изображение. MIME image/* либо, при его
    // отсутствии, расширение из известного списка (Video в любом случае не
    // проходит — см. isVideo). Служебные/скрытые файлы (AppleDouble «._*»,
    // «.DS_Store» и пр.) не считаются изображениями, даже если провайдер
    // отдаёт image/* по расширению имени.
    private fun isImage(fileName: String?, mime: String?): Boolean {
        val name = fileName?.substringAfterLast('/').orEmpty()
        if (name.startsWith("._") || name.startsWith(".")) return false
        if (isVideo(fileName, mime)) return false
        if (!mime.isNullOrEmpty() && mime.startsWith("image/")) return true
        if (mime.isNullOrEmpty() || mime == "application/octet-stream") {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in imageExtensions) return true
        }
        return false
    }

    // Расширения изображений для fallback, когда MIME недоступен.
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "avif",
        "heic", "heif", "jfif", "tif", "tiff"
    )

    // Emits current list to any collector that subscribes later.
    fun observeUris(): Flow<List<Uri>> = uris.flowOn(Dispatchers.IO)

    // Adds a file or a tree. If it is a directory (tree URI), walks it recursively
    // and adds only images (MIME image/*), skipping video. The walk streams the
    // growing list in batches (batchSize), so the UI updates progressively instead
    // of waiting for the whole tree before showing anything.
    suspend fun addSource(uri: Uri): Int {
        val existing = getUris().toMutableSet()
        val added = mutableListOf<Uri>()

        if (isTreeUri(uri)) {
            // Продлеваем (persistable) доступ к дереву до перезапуска, иначе после
            // перезапуска приложения URI детей из флэшки окажутся невалидными.
            persistTreeGrant(uri)
            val doc = DocumentFile.fromTreeUri(context, uri)
            var sinceLastSave = 0
            val collected = collectImages(doc, maxCollectedFiles) { child ->
                if (existing.add(child.uri)) {
                    added.add(child.uri)
                    sinceLastSave++
                }
                // Публикуем промежуточные результаты порциями, чтобы главный экран
                // наполнялся по мере обхода, а не одним куском в самом конце.
                if (sinceLastSave >= batchSize) {
                    saveUris(existing.toList())
                    sinceLastSave = 0
                }
            }
            if (collected >= maxCollectedFiles) {
                // Обход упёрся в лимит — сообщаем в лог, чтобы обрезка не была тихой.
                Log.w(TAG, "Достигнут лимит $maxCollectedFiles файлов при обходе дерева: $uri")
            }
        } else {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (isImage(queryDisplayName(uri), mime)) {
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
    // Расширение из одних цифр ("photo.2023") тоже не валидно — Coil не
    // распознает такой файл как изображение.
    private fun validExtension(displayName: String): String {
        val lastDot = displayName.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == displayName.length - 1) {
            Log.w(TAG, "Нет понятного расширения в «$displayName», fallback jpg")
            return "jpg"
        }
        val ext = displayName.substring(lastDot + 1)
        return if (ext.matches(Regex("[a-zA-Z0-9]{1,5}")) && ext.any { it.isLetter() }) {
            ext.lowercase()
        } else {
            Log.w(TAG, "Невалидное расширение «$ext» в «$displayName», fallback jpg")
            "jpg"
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
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

    // Запрашивает persistable-доступ к дереву. Если грант уже есть или система
    // его не даёт (например, на некоторых TV), безмолвно пропускаем — дети дерева
    // всё равно могут быть доступны на время сессии.
    private fun persistTreeGrant(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    // Итеративный BFS-обход дерева: не рекурсия и не материализация всей
    // директории разом. Очередь хранит только ещё не пройденные папки (а не файлы),
    // что резко снижает пик памяти на каталогах с десятками тысяч файлов.
    private fun collectImages(root: DocumentFile?, maxImages: Int, sink: (DocumentFile) -> Unit): Int {
        if (root == null) return 0
        val queue = ArrayDeque<Pair<DocumentFile, Int>>()
        queue.addLast(root to 0)
        var collected = 0
        while (queue.isNotEmpty()) {
            if (collected >= maxImages) return collected
            val (doc, depth) = queue.removeFirst()
            if (doc.isDirectory) {
                if (depth + 1 > maxDepth) continue
                runCatching { doc.listFiles() }.getOrNull()
                    ?.forEach { queue.addLast(it to depth + 1) }
            } else if (doc.isFile) {
                val mime = doc.type ?: ""
                // image/* includes HEIF/HEIC, WebP, AVIF, GIF, BMP, SVG; video is skipped.
                // isImage дополнительно отсекает видео по расширению, если MIME пуст.
                if (isImage(doc.name, mime)) {
                    sink(doc)
                    collected++
                }
            }
        }
        return collected
    }

    fun getUris(): List<Uri> = loadUris()

    // Removes URIs whose access grant is gone (e.g. expired Photo Picker grants
    // after a reinstall). Otherwise the list would keep dead "✕" tiles.
    suspend fun retainReadableUris(): List<Uri> = withContext(Dispatchers.IO) {
        val current = getUris()
        // Дерево (tree URI) даёт грант на ВСЕХ детей сразу: если доступен корень
        // дерева, валидны и все его дети. Проверяем по одному представителю на
        // дерево, а не каждый URI отдельно (для 500+ фото это 1 binder-запрос
        // вместо 1000+).
        val treeChecks = HashMap<String, Boolean>()
        val valid = current.filter { uri ->
            // Служебные файлы (AppleDouble «._*», dotfiles) — не изображения:
            // они не декодируются, а только «пустят» плитки в сетке.
            val name = uri.lastPathSegment
            if (name != null && (name.startsWith("._") || name.startsWith("."))) return@filter false
            if (isInternalCopy(uri)) {
                // Внутренние копии всегда доступны, если файл существует локально.
                uri.path?.let { File(it).exists() } == true
            } else if (isTreeChild(uri)) {
                val prefix = treeChildPrefix(uri)
                treeChecks.getOrPut(prefix) { checkIsReadableImage(uri) }
            } else {
                checkIsReadableImage(uri)
            }
        }
        if (valid.size != current.size) saveUris(valid)
        valid
    }

    // Дети дерева имеют вид content://authority/tree/<id>/document/<path>.
    private fun isTreeChild(uri: Uri): Boolean {
        val s = uri.toString()
        return s.contains("/tree/") && s.contains("/document/")
    }

    // Ключ дерева — всё до первого /document/: children одного дерева обходят
    // его одинаково, достаточно проверить одного представителя.
    private fun treeChildPrefix(uri: Uri): String = uri.toString().substringBefore("/document/")

    // Проверяет доступность URI и то, что это изображение (MIME image/* без видео).
    private fun checkIsReadableImage(uri: Uri): Boolean = try {
        val mime = context.contentResolver.getType(uri).orEmpty()
        isImage(queryDisplayName(uri), mime.ifEmpty { null })
    } catch (_: Exception) {
        false
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
            val path = uri.path
            if (path != null && File(path).exists() && !File(path).delete()) {
                Log.w(TAG, "Не удалось удалить внутреннюю копию: $path")
            }
        }
    }

    private fun loadUris(): List<Uri> {
        val raw = prefs.getString("uris", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    try {
                        val parsed = Uri.parse(arr.getString(i))
                        if (parsed.isValidUri()) add(parsed)
                    } catch (_: Exception) {
                        // Пропускаем битые записи.
                    }
                }
            }
        } catch (_: Exception) {
            // Фолбэк на старый формат (перенос строки) при обновлении приложения.
            raw.split("\n").filter { it.isNotBlank() && Uri.parse(it).isValidUri() }.map { Uri.parse(it) }
        }
    }

    // Допустимы только ссылки на файловые провайдеры (content://) либо нашу
    // внутреннюю копию (file://). Всё прочее (случайные строки после валидации
    // UI не пройдут) отбрасываем молча.
    private fun Uri.isValidUri(): Boolean =
        (scheme == "content" || (scheme == "file" && isInternalCopy(this)))

    private fun saveUris(uris: List<Uri>) {
        val json = JSONArray().apply {
            uris.forEach { put(it.toString()) }
        }
        prefs.edit { putString("uris", json.toString()) }
        _uris.tryEmit(uris)
    }
}
