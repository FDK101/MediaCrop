package com.videocrop.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.videocrop.ui.theme.*
import com.videocrop.viewmodel.ImageEditorViewModel
import com.videocrop.viewmodel.ImageSaveState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    viewModel: ImageEditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = viewModel.imageBitmap ?: return

    var imageContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val saveState = viewModel.saveState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Image Crop", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        text = "${viewModel.imageWidth}×${viewModel.imageHeight}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnBackground,
                    navigationIconContentColor = OnBackground
                )
            )
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Image preview with crop overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Background)
                    .onSizeChanged { imageContainerSize = it }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                if (imageContainerSize.width > 0) {
                    CropOverlay(
                        videoDisplayWidth = viewModel.imageWidth,
                        videoDisplayHeight = viewModel.imageHeight,
                        cropRect = viewModel.cropRect,
                        screenAspectRatio = viewModel.screenAspectRatio,
                        onCropRectChanged = { viewModel.updateCropRect(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Crop dimensions badge
                val cw = ((viewModel.cropRect.right - viewModel.cropRect.left) * viewModel.imageWidth).toInt()
                val ch = ((viewModel.cropRect.bottom - viewModel.cropRect.top) * viewModel.imageHeight).toInt()
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${cw}×${ch}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Save button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Saved to Pictures / Cropped",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Button(
                    onClick = { viewModel.saveImage(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    enabled = saveState !is ImageSaveState.Saving
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Cropped Image", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // Saving indicator
    AnimatedVisibility(
        visible = saveState is ImageSaveState.Saving,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
    }

    // Success dialog
    if (saveState is ImageSaveState.Success) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveResult() },
            icon = {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(36.dp))
            },
            title = { Text("Saved") },
            text = { Text("Image saved to your Pictures / Cropped folder.") },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = runCatching { android.net.Uri.parse(saveState.filePath) }
                            .getOrNull()
                            ?.let { parsed ->
                                if (parsed.scheme == "content") parsed
                                else androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    File(saveState.filePath)
                                )
                            }
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        viewModel.dismissSaveResult()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("View")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveResult() }) {
                    Text("Dismiss")
                }
            },
            containerColor = Surface
        )
    }

    // Failure dialog
    if (saveState is ImageSaveState.Failure) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveResult() },
            icon = {
                Icon(Icons.Rounded.Error, contentDescription = null, tint = Error, modifier = Modifier.size(36.dp))
            },
            title = { Text("Save Failed") },
            text = { Text(saveState.error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSaveResult() }) {
                    Text("OK")
                }
            },
            containerColor = Surface
        )
    }
}
