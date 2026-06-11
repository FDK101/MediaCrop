package com.videocrop.processor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videocrop.viewmodel.CropRect
import com.videocrop.viewmodel.VideoInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed class ProcessingState {
    data class Progress(val progress: Float) : ProcessingState()
    data class Completed(val outputPath: String) : ProcessingState()
    data class Failed(val message: String) : ProcessingState()
}

object VideoProcessor {

    fun process(
        context: Context,
        videoInfo: VideoInfo,
        cropRect: CropRect,
        startMs: Long,
        endMs: Long
    ): Flow<ProcessingState> = callbackFlow {
        val timestamp = System.currentTimeMillis()
        val fileName = "MediaCrop_$timestamp.mp4"
        val tempFile = File(context.cacheDir, fileName)
        val durationMs = (endMs - startMs).coerceAtLeast(1L)

        val pfd: ParcelFileDescriptor? = try {
            context.contentResolver.openFileDescriptor(videoInfo.uri, "r")
        } catch (e: Exception) {
            trySend(ProcessingState.Failed("Cannot open video: ${e.message}"))
            close()
            return@callbackFlow
        }
        if (pfd == null) {
            trySend(ProcessingState.Failed("Cannot open video file"))
            close()
            return@callbackFlow
        }

        // /proc/self/fd/<n> gives FFmpeg a real file path for any content URI
        val inputPath = "/proc/self/fd/${pfd.fd}"

        val cropX = (cropRect.left * videoInfo.displayWidth).toInt()
        val cropY = (cropRect.top * videoInfo.displayHeight).toInt()
        val cropW = ((cropRect.right - cropRect.left) * videoInfo.displayWidth).toInt().roundToEven()
        val cropH = ((cropRect.bottom - cropRect.top) * videoInfo.displayHeight).toInt().roundToEven()

        val startSec = startMs / 1000.0
        val durationSec = durationMs / 1000.0

        // libx264 CRF 20 = visually lossless, hardware-independent quality
        val args = arrayOf(
            "-y",
            "-ss", startSec.toString(),
            "-i", inputPath,
            "-t", durationSec.toString(),
            "-vf", "crop=$cropW:$cropH:$cropX:$cropY",
            "-c:v", "libx264",
            "-crf", "20",
            "-preset", "veryfast",
            "-an",
            "-movflags", "+faststart",
            tempFile.absolutePath
        )

        val session = FFmpegKit.executeWithArgumentsAsync(
            args,
            { completed ->
                pfd.close()
                if (ReturnCode.isSuccess(completed.returnCode)) {
                    val savedPath = saveToPublicStorage(context, tempFile, fileName)
                    tempFile.delete()
                    trySend(ProcessingState.Completed(savedPath))
                } else {
                    tempFile.delete()
                    trySend(ProcessingState.Failed(
                        completed.failStackTrace ?: "Export failed"
                    ))
                }
                close()
            },
            null,
            { stats ->
                val progress = (stats.time.toFloat() / durationMs).coerceIn(0f, 1f)
                trySend(ProcessingState.Progress(progress))
            }
        )

        awaitClose {
            session?.cancel()
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    private fun saveToPublicStorage(context: Context, tempFile: File, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Cropped")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri: Uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return tempFile.absolutePath
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri.toString()
        } else {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Cropped"
            )
            moviesDir.mkdirs()
            val destFile = File(moviesDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null
            )
            destFile.absolutePath
        }
    }

    private fun Int.roundToEven() = if (this % 2 == 0) this else this + 1
}
