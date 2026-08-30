package com.example.slideshow.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("images", Context.MODE_PRIVATE)

    // Adds a file or a tree. If it is a directory (tree URI), walks it recursively
    // and adds only images (MIME image/*), skipping video.
    fun addSource(uri: Uri): Int {
        val existing = getUris().toMutableSet()
        val added = mutableListOf<Uri>()

        if (isTreeUri(uri)) {
            val doc = DocumentFile.fromTreeUri(context, uri)
            collectImages(doc) { child ->
                if (existing.add(child.uri)) added.add(child.uri)
            }
        } else {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime.startsWith("image/") && existing.add(uri)) {
                added.add(uri)
            }
        }
        saveUris(existing.toList())
        return added.size
    }

    private fun isTreeUri(uri: Uri): Boolean {
        return try {
            DocumentsContract.isTreeUri(uri)
        } catch (_: Exception) {
            false
        }
    }

    private fun collectImages(doc: DocumentFile?, sink: (DocumentFile) -> Unit) {
        if (doc == null) return
        if (doc.isDirectory) {
            doc.listFiles().forEach { collectImages(it, sink) }
        } else if (doc.isFile) {
            val mime = doc.type ?: ""
            // image/* includes HEIF/HEIC, WebP, AVIF, GIF, BMP, SVG; video is skipped
            if (mime.startsWith("image/")) sink(doc)
        }
    }

    fun getUris(): List<Uri> = loadUris()

    fun removeUri(uri: Uri) {
        saveUris(getUris().filterNot { it == uri })
    }

    fun clear() {
        saveUris(emptyList())
    }

    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else uri.lastPathSegment ?: ""
        } ?: uri.lastPathSegment ?: ""
    }

    private fun loadUris(): List<Uri> {
        val raw = prefs.getString("uris", "") ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.map { Uri.parse(it) }
    }

    private fun saveUris(uris: List<Uri>) {
        prefs.edit { putString("uris", uris.joinToString("\n") { it.toString() }) }
    }
}
