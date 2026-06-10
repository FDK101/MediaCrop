package com.videocrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videocrop.ui.EditorScreen
import com.videocrop.ui.HomeScreen
import com.videocrop.ui.ImageEditorScreen
import com.videocrop.ui.theme.VideoCropTheme
import com.videocrop.viewmodel.EditorViewModel
import com.videocrop.viewmodel.ImageEditorViewModel

private enum class Screen { HOME, VIDEO_EDITOR, IMAGE_EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoCropTheme {
                val editorViewModel: EditorViewModel = viewModel()
                val imageEditorViewModel: ImageEditorViewModel = viewModel()
                var screen by remember { mutableStateOf(Screen.HOME) }

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        defaultStartMs = editorViewModel.defaultStartMs,
                        onDefaultStartChanged = { editorViewModel.setDefaultStartMs(it) },
                        onVideoSelected = { uri ->
                            val dm = resources.displayMetrics
                            editorViewModel.setScreenAspectRatio(dm.widthPixels, dm.heightPixels)
                            editorViewModel.loadVideo(this, uri)
                            screen = Screen.VIDEO_EDITOR
                        },
                        onImageSelected = { uri ->
                            val dm = resources.displayMetrics
                            imageEditorViewModel.setScreenAspectRatio(dm.widthPixels, dm.heightPixels)
                            imageEditorViewModel.loadImage(this, uri)
                            screen = Screen.IMAGE_EDITOR
                        }
                    )

                    Screen.VIDEO_EDITOR -> EditorScreen(
                        viewModel = editorViewModel,
                        onBack = {
                            screen = Screen.HOME
                            editorViewModel.reset()
                        }
                    )

                    Screen.IMAGE_EDITOR -> ImageEditorScreen(
                        viewModel = imageEditorViewModel,
                        onBack = {
                            screen = Screen.HOME
                            imageEditorViewModel.reset()
                        }
                    )
                }
            }
        }
    }
}
