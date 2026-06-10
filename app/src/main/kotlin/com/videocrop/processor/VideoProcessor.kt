package com.videocrop.processor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.videocrop.viewmodel.CropRect
import com.videocrop.viewmodel.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val fileName = "VideoCrop_$timestamp.mp4"
        val tempFile = File(context.cacheDir, fileName)

        val (outputWidth, outputHeight) = calculateOutputSize(videoInfo, cropRect)

        val leftNdc = cropRect.left * 2f - 1f
        val rightNdc = cropRect.right * 2f - 1f
        val topNdc = 1f - cropRect.top * 2f
        val bottomNdc = 1f - cropRect.bottom * 2f

        val videoEffects = mutableListOf<Effect>()
        videoEffects.add(Crop(leftNdc, rightNdc, bottomNdc, topNdc))
        videoEffects.add(
            Presentation.createForWidthAndHeight(
                outputWidth, outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        )
        val effects = Effects(emptyList(), videoEffects)

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(videoInfo.uri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(effects)
            .build()

        var transformer: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val savedPath = saveToPublicStorage(context, tempFile, fileName)
                tempFile.delete()
                trySend(ProcessingState.Completed(savedPath))
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                tempFile.delete()
                trySend(ProcessingState.Failed(exportException.message ?: "Export failed"))
                close()
            }
        }

        withContext(Dispatchers.Main) {
            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener)
                .build()
                .also { t ->
                    t.start(editedMediaItem, tempFile.absolutePath)
                }
        }

        launch {
            val progressHolder = ProgressHolder()
            while (!isClosedForSend) {
                delay(200)
                transformer?.getProgress(progressHolder)
                val p = progressHolder.progress
                if (p >= 0) {
                    trySend(ProcessingState.Progress(p / 100f))
                }
            }
        }

        awaitClose {
            transformer?.cancel()
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
            android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)
            destFile.absolutePath
        }
    }

    private fun calculateOutputSize(videoInfo: VideoInfo, cropRect: CropRect): Pair<Int, Int> {
        val cropW = ((cropRect.right - cropRect.left) * videoInfo.displayWidth).toInt()
        val cropH = ((cropRect.bottom - cropRect.top) * videoInfo.displayHeight).toInt()

        val maxDim = 1920
        val scale = minOf(1f, minOf(maxDim.toFloat() / cropW, maxDim.toFloat() / cropH))
        val outW = (cropW * scale).toInt().roundToEven()
        val outH = (cropH * scale).toInt().roundToEven()
        return outW to outH
    }

    private fun Int.roundToEven() = if (this % 2 == 0) this else this + 1
}
