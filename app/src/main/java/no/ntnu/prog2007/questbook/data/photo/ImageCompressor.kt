package no.ntnu.prog2007.questbook.data.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thrown when a photo is still too large for Firestore after the strongest compression. */
class PhotoTooLargeException : IOException("Photo is too large to store, even after compression")

/**
 * Turns a photo picked by the user into a small JPEG that fits in a Firestore document.
 *
 * The image is scaled down so its longest side is at most [PhotoSizing.MAX_SIDE_PX],
 * then compressed as JPEG. If it is still too large, the quality is lowered step by step.
 * All work runs on [Dispatchers.IO], so the UI thread is never blocked.
 *
 * @param contentResolver used to open the image behind a content [Uri].
 */
class ImageCompressor(private val contentResolver: ContentResolver) {

    /**
     * Reads, scales down and compresses the image at [uri].
     *
     * @return JPEG bytes, typically 150-300 KB.
     * @throws IOException if the image cannot be read.
     * @throws PhotoTooLargeException if the image does not fit even at the lowest quality.
     */
    suspend fun compress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri)
        try {
            var quality = START_QUALITY
            var jpeg = bitmap.toJpeg(quality)
            while (!PhotoSizing.fitsInFirestore(jpeg.size) && quality > MIN_QUALITY) {
                quality -= QUALITY_STEP
                jpeg = bitmap.toJpeg(quality)
            }
            if (!PhotoSizing.fitsInFirestore(jpeg.size)) throw PhotoTooLargeException()
            jpeg
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(uri)
        } else {
            decodeWithBitmapFactory(uri)
        }

    /** Android 9+: ImageDecoder scales while decoding and turns the photo the right way up. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = PhotoSizing.scaleFactor(width, height)
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1)
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /**
     * Android 8 (API 26-27): reads a down-sampled copy with BitmapFactory, then scales it.
     * Note: the EXIF rotation is not applied here, so some photos may appear sideways.
     */
    private fun decodeWithBitmapFactory(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Could not read image")

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= PhotoSizing.MAX_SIDE_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not read image")

        val scale = PhotoSizing.scaleFactor(sampled.width, sampled.height)
        if (scale >= 1f) return sampled
        val scaled = Bitmap.createScaledBitmap(
            sampled,
            (sampled.width * scale).roundToInt().coerceAtLeast(1),
            (sampled.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        sampled.recycle()
        return scaled
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            this.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private companion object {
        const val START_QUALITY = 80
        const val MIN_QUALITY = 40
        const val QUALITY_STEP = 10
    }
}
