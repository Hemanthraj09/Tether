package com.tether.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.tether.app.domain.Proof
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Turns a camera/gallery photo into a small proof JPEG, on the device:
 * subsampled decode (never loads a 12 MP image into memory), EXIF rotation
 * applied, scaled to ≤ 720 px, re-encoded until it fits in ~120 KB.
 * Re-encoding also drops all EXIF metadata, including GPS location.
 */
object ProofImage {

    suspend fun compress(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = Proof.sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null

            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val (w, h) = Proof.scaledSize(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
                if (rotation != 0f) postRotate(rotation)
            }
            val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (scaled !== decoded) decoded.recycle()

            var quality = 80
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
                quality -= 10
            } while (bytes.size > Proof.MAX_BYTES && quality >= 30)
            scaled.recycle()
            bytes.takeIf { it.size <= Proof.MAX_BYTES }
        }.getOrNull()
    }
}
