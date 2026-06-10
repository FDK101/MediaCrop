package com.videocrop.ui

import android.content.Intent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videocrop.ui.theme.*
import com.videocrop.viewmodel.EditorViewModel
import com.videocrop.viewmodel.ExportState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val info = viewModel.videoInfo ?: return

    // ExoPlayer setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(info.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }

    // Sync trim start position when play begins
    LaunchedEffect(viewModel.trimStartMs) {
        if (!exoPlayer.isPlaying) {
            exoPlayer.seekTo(viewModel.trimStartMs)
        }
    }

    // Track playback position
    LaunchedEffect(exoPlayer) {
        while (true) {
            viewModel.currentPositionMs = exoPlayer.currentPosition
            viewModel.isPlaying = exoPlayer.isPlaying
            kotlinx.coroutines.delay(100)
        }
    }

    // Stop at trim end
    LaunchedEffect(viewModel.isPlaying, viewModel.currentPositionMs, viewModel.trimEndMs) {
        if (viewModel.isPlaying && viewModel.currentPositionMs >= viewModel.trimEndMs) {
            exoPlayer.pause()
            exoPlayer.seekTo(viewModel.trimStartMs)
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    var videoContainerSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Video Editor", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        text = "${info.displayWidth}×${info.displayHeight}",
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
            // Video preview with crop overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .background(Background)
                    .onSizeChanged { videoContainerSize = it }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exoPlayer
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (videoContainerSize.width > 0) {
                    CropOverlay(
                        videoDisplayWidth = info.displayWidth,
                        videoDisplayHeight = info.displayHeight,
                        cropRect = viewModel.cropRect,
                        screenAspectRatio = viewModel.screenAspectRatio,
                        onCropRectChanged = { viewModel.updateCropRect(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Crop info badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    val cw = ((viewModel.cropRect.right - viewModel.cropRect.left) * info.displayWidth).toInt()
                    val ch = ((viewModel.cropRect.bottom - viewModel.cropRect.top) * info.displayHeight).toInt()
                    Text(
                        text = "${cw}×${ch}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Muted indicator
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = null,
                            tint = Error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "MUTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Error
                        )
                    }
                }
            }

            // Controls area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(Background)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Playback controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo(viewModel.trimStartMs)
                            viewModel.currentPositionMs = viewModel.trimStartMs
                        }
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "To start", tint = OnSurface)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                if (viewModel.currentPositionMs >= viewModel.trimEndMs) {
                                    exoPlayer.seekTo(viewModel.trimStartMs)
                                }
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Primary
                        )
                    ) {
                        Icon(
                            imageVector = if (viewModel.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            exoPlayer.seekTo(viewModel.trimEndMs)
                            viewModel.currentPositionMs = viewModel.trimEndMs
                        }
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "To end", tint = OnSurface)
                    }
                }

                // Set IN / Set OUT at current position
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            val pos = viewModel.currentPositionMs
                                .coerceIn(0L, (viewModel.trimEndMs - 33L).coerceAtLeast(0L))
                            viewModel.setTrimStart(pos)
                            exoPlayer.seekTo(pos)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(14.dp), tint = Secondary)
                        Spacer(Modifier.width(2.dp))
                        Text("Set IN here", style = MaterialTheme.typography.labelSmall, color = Secondary)
                    }
                    TextButton(
                        onClick = {
                            val pos = viewModel.currentPositionMs
                                .coerceAtLeast(viewModel.trimStartMs + 33L)
                                .coerceAtMost(info.durationMs)
                            viewModel.setTrimEnd(pos)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Set OUT here", style = MaterialTheme.typography.labelSmall, color = Secondary)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = Secondary)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = SurfaceVariant
                )

                // Trim controls
                TrimControls(
                    durationMs = info.durationMs,
                    trimStartMs = viewModel.trimStartMs,
                    trimEndMs = viewModel.trimEndMs,
                    currentPositionMs = viewModel.currentPositionMs,
                    onTrimStartChanged = { ms ->
                        viewModel.setTrimStart(ms)
                        exoPlayer.seekTo(ms)
                    },
                    onTrimEndChanged = { ms ->
                        viewModel.setTrimEnd(ms)
                    },
                    onSeek = { ms ->
                        exoPlayer.seekTo(ms)
                        viewModel.currentPositionMs = ms
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = SurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Export button
                Button(
                    onClick = { viewModel.exportVideo(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    enabled = viewModel.exportState !is ExportState.Exporting
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Video", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Export progress overlay
    val exportState = viewModel.exportState
    AnimatedVisibility(
        visible = exportState is ExportState.Exporting,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Surface,
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Exporting...",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBackground
                    )
                    Spacer(Modifier.height(16.dp))
                    val progress = (exportState as? ExportState.Exporting)?.progress ?: 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                        trackColor = SurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }

    // Export success dialog
    if (exportState is ExportState.Success) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportResult() },
            icon = {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(36.dp))
            },
            title = { Text("Export Complete") },
            text = {
                Column {
                    Text("Video saved to your Movies folder.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = runCatching { android.net.Uri.parse(exportState.filePath) }
                            .getOrNull()
                            ?.let { parsed ->
                                if (parsed.scheme == "content") parsed
                                else androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    File(exportState.filePath)
                                )
                            }
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        viewModel.dismissExportResult()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Play")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExportResult() }) {
                    Text("Dismiss")
                }
            },
            containerColor = Surface
        )
    }

    // Export failure dialog
    if (exportState is ExportState.Failure) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportResult() },
            icon = {
                Icon(Icons.Rounded.Error, contentDescription = null, tint = Error, modifier = Modifier.size(36.dp))
            },
            title = { Text("Export Failed") },
            text = { Text(exportState.error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissExportResult() }) {
                    Text("OK")
                }
            },
            containerColor = Surface
        )
    }
}
