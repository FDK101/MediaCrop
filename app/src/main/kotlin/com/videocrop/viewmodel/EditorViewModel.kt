package com.videocrop.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videocrop.processor.VideoProcessor
import com.videocrop.processor.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class VideoInfo(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int = 0
) {
    val displayWidth get() = if (rotation == 90 || rotation == 270) height else width
    val displayHeight get() = if (rotation == 90 || rotation == 270) width else height
    val displayAspect get() = displayWidth.toFloat() / displayHeight.toFloat()
}

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width get() = right - left
    val height get() = bottom - top
    val aspectRatio get() = width / height

    fun clampedTo(
        minLeft: Float = 0f, minTop: Float = 0f,
        maxRight: Float = 1f, maxBottom: Float = 1f
    ): CropRect {
        val w = right - left
        val h = bottom - top
        val l = left.coerceIn(minLeft, maxRight - w)
        val t = top.coerceIn(minTop, maxBottom - h)
        return copy(left = l, top = t, right = l + w, bottom = t + h)
    }
}

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val progress: Float) : ExportState()
    data class Success(val filePath: String) : ExportState()
    data class Failure(val error: String) : ExportState()
}

private const val PREFS_NAME = "video_crop_prefs"
private const val KEY_DEFAULT_START_MS = "default_start_ms"

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var _defaultStartMs by mutableLongStateOf(prefs.getLong(KEY_DEFAULT_START_MS, 0L))
    val defaultStartMs: Long get() = _defaultStartMs

    var videoInfo by mutableStateOf<VideoInfo?>(null)
        private set

    var cropRect by mutableStateOf(CropRect())
        private set

    var trimStartMs by mutableLongStateOf(0L)
        private set

    var trimEndMs by mutableLongStateOf(0L)
        private set

    var currentPositionMs by mutableLongStateOf(0L)

    var isPlaying by mutableStateOf(false)

    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    var screenAspectRatio by mutableFloatStateOf(9f / 16f)

    fun setDefaultStartMs(ms: Long) {
        _defaultStartMs = ms.coerceAtLeast(0L)
        prefs.edit().putLong(KEY_DEFAULT_START_MS, _defaultStartMs).apply()
    }

    fun setScreenAspectRatio(width: Int, height: Int) {
        screenAspectRatio = width.toFloat() / height.toFloat()
        videoInfo?.let { cropRect = calculateInitialCropRect(it) }
    }

    fun loadVideo(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

                val info = VideoInfo(uri, rawWidth, rawHeight, duration, rotation)
                videoInfo = info
                trimStartMs = defaultStartMs.coerceAtMost((duration - 33L).coerceAtLeast(0L))
                trimEndMs = duration
                currentPositionMs = trimStartMs
                cropRect = calculateInitialCropRect(info)
            } finally {
                retriever.release()
            }
        }
    }

    private fun calculateInitialCropRect(info: VideoInfo): CropRect {
        val screenAspect = screenAspectRatio
        val videoAspect = info.displayAspect

        val cropWidthFraction: Float
        val cropHeightFraction: Float

        if (screenAspect <= videoAspect) {
            cropHeightFraction = 1f
            cropWidthFraction = (screenAspect / videoAspect).coerceAtMost(1f)
        } else {
            cropWidthFraction = 1f
            cropHeightFraction = (videoAspect / screenAspect).coerceAtMost(1f)
        }

        val left = (1f - cropWidthFraction) / 2f
        val top = (1f - cropHeightFraction) / 2f
        return CropRect(left, top, left + cropWidthFraction, top + cropHeightFraction)
    }

    fun updateCropRect(rect: CropRect) {
        cropRect = rect.clampedTo()
    }

    fun setTrimStart(ms: Long) {
        trimStartMs = ms.coerceIn(0L, trimEndMs - 33L)
    }

    fun setTrimEnd(ms: Long) {
        val info = videoInfo ?: return
        trimEndMs = ms.coerceIn(trimStartMs + 33L, info.durationMs)
    }

    fun adjustTrimStart(deltaMs: Long) = setTrimStart(trimStartMs + deltaMs)
    fun adjustTrimEnd(deltaMs: Long) = setTrimEnd(trimEndMs + deltaMs)

    fun exportVideo(context: Context) {
        val info = videoInfo ?: return
        exportState = ExportState.Exporting(0f)

        viewModelScope.launch(Dispatchers.IO) {
            VideoProcessor.process(
                context = context,
                videoInfo = info,
                cropRect = cropRect,
                startMs = trimStartMs,
                endMs = trimEndMs
            ).collect { state ->
                when (state) {
                    is ProcessingState.Progress -> exportState = ExportState.Exporting(state.progress)
                    is ProcessingState.Completed -> exportState = ExportState.Success(state.outputPath)
                    is ProcessingState.Failed -> exportState = ExportState.Failure(state.message)
                }
            }
        }
    }

    fun dismissExportResult() {
        exportState = ExportState.Idle
    }

    fun reset() {
        videoInfo = null
        cropRect = CropRect()
        trimStartMs = 0L
        trimEndMs = 0L
        currentPositionMs = 0L
        exportState = ExportState.Idle
    }
}
