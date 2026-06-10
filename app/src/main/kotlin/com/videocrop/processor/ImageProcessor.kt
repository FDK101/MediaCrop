package com.videocrop.processor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.videocrop.viewmodel.CropRect
import java.io.File

object ImageProcessor {

    fun cropAndSave(context: Context, bitmap: Bitmap, cropRect: CropRect): Result<String> {
        return runCatching {
            val x = (cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val w = ((cropRect.right - cropRect.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val h = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)

            val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
            val fileName = "Cropped_${System.currentTimeMillis()}.jpg"

            saveToPublicStorage(context, cropped, fileName)
        }
    }

    private fun saveToPublicStorage(context: Context, bitmap: Bitmap, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cropped")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore entry")
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Cropped"
            )
            dir.mkdirs()
            val file = File(dir, fileName)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
            )
            file.absolutePath
        }
    }
}
