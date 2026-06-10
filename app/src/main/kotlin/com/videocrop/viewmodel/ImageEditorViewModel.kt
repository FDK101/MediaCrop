package com.videocrop.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videocrop.processor.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImageSaveState {
    object Idle : ImageSaveState()
    object Saving : ImageSaveState()
    data class Success(val filePath: String) : ImageSaveState()
    data class Failure(val error: String) : ImageSaveState()
}

class ImageEditorViewModel(app: Application) : AndroidViewModel(app) {

    var imageUri by mutableStateOf<Uri?>(null)
        private set
    var imageBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var imageWidth by mutableIntStateOf(0)
        private set
    var imageHeight by mutableIntStateOf(0)
        private set

    var cropRect by mutableStateOf(CropRect())
        private set

    var screenAspectRatio by mutableFloatStateOf(9f / 16f)

    var saveState by mutableStateOf<ImageSaveState>(ImageSaveState.Idle)
        private set

    fun setScreenAspectRatio(width: Int, height: Int) {
        screenAspectRatio = width.toFloat() / height.toFloat()
    }

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@launch

            imageUri = uri
            imageBitmap = bitmap
            imageWidth = bitmap.width
            imageHeight = bitmap.height
            cropRect = calculateInitialCropRect(bitmap.width, bitmap.height)
        }
    }

    private fun calculateInitialCropRect(imgW: Int, imgH: Int): CropRect {
        val screenAspect = screenAspectRatio
        val imageAspect = imgW.toFloat() / imgH.toFloat()

        val cropWidthFraction: Float
        val cropHeightFraction: Float

        if (screenAspect <= imageAspect) {
            cropHeightFraction = 1f
            cropWidthFraction = (screenAspect / imageAspect).coerceAtMost(1f)
        } else {
            cropWidthFraction = 1f
            cropHeightFraction = (imageAspect / screenAspect).coerceAtMost(1f)
        }

        val left = (1f - cropWidthFraction) / 2f
        val top = (1f - cropHeightFraction) / 2f
        return CropRect(left, top, left + cropWidthFraction, top + cropHeightFraction)
    }

    fun updateCropRect(rect: CropRect) {
        cropRect = rect.clampedTo()
    }

    fun saveImage(context: Context) {
        val bitmap = imageBitmap ?: return
        saveState = ImageSaveState.Saving

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ImageProcessor.cropAndSave(context, bitmap, cropRect)
            }
            saveState = result.fold(
                onSuccess = { ImageSaveState.Success(it) },
                onFailure = { ImageSaveState.Failure(it.message ?: "Save failed") }
            )
        }
    }

    fun dismissSaveResult() {
        saveState = ImageSaveState.Idle
    }

    fun reset() {
        imageUri = null
        imageBitmap = null
        imageWidth = 0
        imageHeight = 0
        cropRect = CropRect()
        saveState = ImageSaveState.Idle
    }
}
