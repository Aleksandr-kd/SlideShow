package com.example.slideshow.image

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import coil.size.Size
import com.aliyun.libheif.HeifInfo
import com.aliyun.libheif.HeifNative
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Кастомный Coil-декодер для статичных HEIC/HEIF.
 *
 * Зачем: системный путь (ImageDecoder/BitmapFactory) на физических устройствах
 * часто уходит на аппаратный HEVC-кодек, который на ряде прошивок выдаёт битый
 * chroma (фиолетово-синий оттенок). На эмуляторе дефекта нет — там software-путь.
 * Здесь HEIC декодируется нативно через libheif (Aliyun) → libde265 — чистый
 * CPU-декодер, одинаковый на любой версии Android («как на эмуляторе»).
 *
 * Декодер захватывает ТОЛЬКО HEIC/HEIF. Прочие форматы (включая AVIF) идут
 * стандартным путём Coil и не затрагиваются.
 */
class HeicDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult = withContext(Dispatchers.Default) {
        val inputBytes = source.source().readByteArray()
        try {
            if (inputBytes.isEmpty()) throw IllegalStateException("HEIC empty input")
            decodeWithLibheif(inputBytes)
        } finally {
            source.close()
        }
    }

    private fun decodeWithLibheif(inputBytes: ByteArray): DecodeResult {
        if (!HeifNative.isHeic(inputBytes.size.toLong(), inputBytes)) {
            throw IllegalStateException("Not a HEIC container")
        }

        val info = HeifInfo()
        if (!HeifNative.getInfo(info, inputBytes.size.toLong(), inputBytes)) {
            throw IllegalStateException("Unable to read HEIC info")
        }
        val frames = info.frameList
        if (frames.isEmpty()) throw IllegalStateException("HEIC has no frames")
        val srcWidth = frames[0].width
        val srcHeight = frames[0].height
        if (srcWidth <= 0 || srcHeight <= 0) throw IllegalStateException("HEIC invalid size")

        // Запрашиваемый размер: целевой декод. libheif декодирует в полный размер,
        // поэтому декодируем full-size, затем смасштабируем до target, чтобы в кэш
        // не попадал гигантский full-res битмап.
        val targetWidth = options.size.resolveWidth(srcWidth)
        val targetHeight = options.size.resolveHeight(srcHeight)
        val decodeScale = if (targetWidth >= srcWidth && targetHeight >= srcHeight) 1f else {
            minOf(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
        }

        // Декодируем в требуемый масштаб: если нужно уменьшить — сначала full-res,
        // затем scale. (libheif не умеет сэмплить при декоде.)
        val fullBitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
        val ok = HeifNative.toRgba(inputBytes.size.toLong(), inputBytes, fullBitmap)
        if (!ok) {
            fullBitmap.recycle()
            throw IllegalStateException("libheif decode failed")
        }

        val result = if (decodeScale < 1f) {
            val newW = (srcWidth * decodeScale).roundToInt().coerceAtLeast(1)
            val newH = (srcHeight * decodeScale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(
                fullBitmap, newW, newH, /* filter = */ true
            )
            fullBitmap.recycle()
            scaled
        } else {
            fullBitmap
        }

        val sampled = result.width != srcWidth || result.height != srcHeight
        return DecodeResult(BitmapDrawable(options.context.resources, result), isSampled = sampled)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val ln = result.source.fileOrNull()?.name
                ?: result.mimeType
                ?: ""
            if (ln.lowercase(Locale.US).contains(".heic") ||
                ln.lowercase(Locale.US).contains(".heif") ||
                ln.lowercase(Locale.US).startsWith("image/heic") ||
                ln.lowercase(Locale.US).startsWith("image/heif")
            ) {
                return HeicDecoder(result.source, options)
            }
            return null
        }
    }
}

private fun Size.resolveWidth(original: Int): Int = when (val w = width) {
    is Dimension.Pixels -> w.px
    Dimension.Undefined -> original
}

private fun Size.resolveHeight(original: Int): Int = when (val h = height) {
    is Dimension.Pixels -> h.px
    Dimension.Undefined -> original
}