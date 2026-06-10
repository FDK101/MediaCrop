package com.videocrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.videocrop.viewmodel.CropRect

private enum class DragMode { NONE, MOVE, TL, TR, BL, BR, T, B, L, R }

@Composable
fun CropOverlay(
    videoDisplayWidth: Int,
    videoDisplayHeight: Int,
    cropRect: CropRect,
    screenAspectRatio: Float,
    onCropRectChanged: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleSizePx = with(density) { 24.dp.toPx() }
    val touchTargetPx = with(density) { 32.dp.toPx() }

    // Use rememberUpdatedState so the pointerInput lambda always reads the latest values
    // WITHOUT the pointerInput block restarting (which would cancel an in-progress drag)
    val currentCrop by rememberUpdatedState(cropRect)
    val currentAspect by rememberUpdatedState(screenAspectRatio)

    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(videoDisplayWidth, videoDisplayHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    // Hit-test at original touch position before any movement
                    val vbHit = computeVideoBounds(
                        size.width.toFloat(), size.height.toFloat(),
                        videoDisplayWidth, videoDisplayHeight
                    )
                    val crop0 = currentCrop
                    val cL = vbHit.left + crop0.left  * vbHit.width
                    val cT = vbHit.top  + crop0.top   * vbHit.height
                    val cR = vbHit.left + crop0.right  * vbHit.width
                    val cB = vbHit.top  + crop0.bottom * vbHit.height
                    val mode = hitTest(down.position, cL, cT, cR, cB, touchTargetPx)

                    if (mode == DragMode.NONE) return@awaitEachGesture

                    // 200ms timeout kills long-presses before the system fires haptic (~500ms).
                    // Returns null on timeout, tap, or system cancel → no crop change.
                    val firstDrag = withTimeoutOrNull(200L) {
                        awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }
                    }
                    if (firstDrag == null) return@awaitEachGesture

                    dragMode = mode

                    drag(firstDrag.id) { change ->
                        val delta = change.positionChange()
                        change.consume()
                        val vb = computeVideoBounds(
                            size.width.toFloat(), size.height.toFloat(),
                            videoDisplayWidth, videoDisplayHeight
                        )
                        val dx = delta.x / vb.width
                        val dy = delta.y / vb.height
                        val crop = currentCrop
                        val aspect = currentAspect
                        val newRect = when (mode) {
                            DragMode.MOVE -> moveCrop(crop, dx, dy)
                            DragMode.BR   -> resizeFromCorner(crop,  dx, aspect, fixedLeft = true,  fixedTop = true)
                            DragMode.BL   -> resizeFromCorner(crop, -dx, aspect, fixedLeft = false, fixedTop = true)
                            DragMode.TR   -> resizeFromCorner(crop,  dx, aspect, fixedLeft = true,  fixedTop = false)
                            DragMode.TL   -> resizeFromCorner(crop, -dx, aspect, fixedLeft = false, fixedTop = false)
                            DragMode.R    -> resizeEdgeH(crop,  dx, aspect, fixedLeft = true)
                            DragMode.L    -> resizeEdgeH(crop, -dx, aspect, fixedLeft = false)
                            DragMode.B    -> resizeEdgeV(crop,  dy, aspect, fixedTop = true)
                            DragMode.T    -> resizeEdgeV(crop, -dy, aspect, fixedTop = false)
                            DragMode.NONE -> crop
                        }
                        onCropRectChanged(newRect)
                    }

                    dragMode = DragMode.NONE
                }
            }
    ) {
        val vb = computeVideoBounds(size.width, size.height, videoDisplayWidth, videoDisplayHeight)

        val cL = vb.left + currentCrop.left * vb.width
        val cT = vb.top  + currentCrop.top  * vb.height
        val cR = vb.left + currentCrop.right  * vb.width
        val cB = vb.top  + currentCrop.bottom * vb.height

        drawOverlay(cL, cT, cR, cB, vb, handleSizePx)
    }
}

// ---------------------------------------------------------------------------
// Crop math — all coordinates are 0..1 fractions of the video dimensions
// ---------------------------------------------------------------------------

private fun moveCrop(crop: CropRect, dx: Float, dy: Float): CropRect {
    val w = crop.width
    val h = crop.height
    val newL = (crop.left + dx).coerceIn(0f, 1f - w)
    val newT = (crop.top  + dy).coerceIn(0f, 1f - h)
    return CropRect(newL, newT, newL + w, newT + h)
}

/**
 * Resize by pulling the moving corner toward/away from the fixed corner.
 * [dw] is the signed change in width (positive = growing).
 * Aspect ratio is strictly maintained; the whole rect is clamped into [0,1].
 */
private fun resizeFromCorner(
    crop: CropRect,
    dw: Float,
    aspect: Float,
    fixedLeft: Boolean,
    fixedTop: Boolean
): CropRect {
    val minSize = 0.05f
    val fixedX = if (fixedLeft) crop.left  else crop.right
    val fixedY = if (fixedTop)  crop.top   else crop.bottom

    var newW = (crop.width + dw).coerceAtLeast(minSize)
    var newH = newW / aspect

    // Clamp so the moving edges stay inside [0,1]
    val maxW = if (fixedLeft) 1f - fixedX else fixedX
    val maxH = if (fixedTop)  1f - fixedY else fixedY
    if (newW > maxW) { newW = maxW; newH = newW / aspect }
    if (newH > maxH) { newH = maxH; newW = newH * aspect }

    val left   = if (fixedLeft)  fixedX          else fixedX - newW
    val top    = if (fixedTop)   fixedY          else fixedY - newH
    return CropRect(left, top, left + newW, top + newH)
}

/** Resize by pulling a vertical edge (T or B). Width follows from height × aspect. */
private fun resizeEdgeV(
    crop: CropRect,
    dh: Float,
    aspect: Float,
    fixedTop: Boolean
): CropRect {
    val minSize = 0.05f
    val fixedY  = if (fixedTop) crop.top else crop.bottom

    var newH = (crop.height + dh).coerceAtLeast(minSize)
    var newW = newH * aspect

    val maxH = if (fixedTop) 1f - fixedY else fixedY
    val maxW = 1f
    if (newH > maxH) { newH = maxH; newW = newH * aspect }
    if (newW > maxW) { newW = maxW; newH = newW / aspect }

    val centerX = (crop.left + crop.right) / 2f
    val left = (centerX - newW / 2f).coerceIn(0f, 1f - newW)
    val top  = if (fixedTop) fixedY else fixedY - newH
    return CropRect(left, top, left + newW, top + newH)
}

/** Resize by pulling a horizontal edge (L or R). Height follows from width / aspect. */
private fun resizeEdgeH(
    crop: CropRect,
    dw: Float,
    aspect: Float,
    fixedLeft: Boolean
): CropRect {
    val minSize = 0.05f
    val fixedX  = if (fixedLeft) crop.left else crop.right

    var newW = (crop.width + dw).coerceAtLeast(minSize)
    var newH = newW / aspect

    val maxW = if (fixedLeft) 1f - fixedX else fixedX
    val maxH = 1f
    if (newW > maxW) { newW = maxW; newH = newW / aspect }
    if (newH > maxH) { newH = maxH; newW = newH * aspect }

    val left    = if (fixedLeft) fixedX else fixedX - newW
    val centerY = (crop.top + crop.bottom) / 2f
    val top     = (centerY - newH / 2f).coerceIn(0f, 1f - newH)
    return CropRect(left, top, left + newW, top + newH)
}

// ---------------------------------------------------------------------------
// Hit testing
// ---------------------------------------------------------------------------

private fun hitTest(
    offset: Offset,
    cL: Float, cT: Float, cR: Float, cB: Float,
    hitPx: Float
): DragMode {
    val x = offset.x; val y = offset.y
    fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < hitPx
    fun inX() = x in (cL - hitPx)..(cR + hitPx)
    fun inY() = y in (cT - hitPx)..(cB + hitPx)
    return when {
        near(x, cL) && near(y, cT) -> DragMode.TL
        near(x, cR) && near(y, cT) -> DragMode.TR
        near(x, cL) && near(y, cB) -> DragMode.BL
        near(x, cR) && near(y, cB) -> DragMode.BR
        near(y, cT) && inX()        -> DragMode.T
        near(y, cB) && inX()        -> DragMode.B
        near(x, cL) && inY()        -> DragMode.L
        near(x, cR) && inY()        -> DragMode.R
        x in cL..cR && y in cT..cB  -> DragMode.MOVE
        else                         -> DragMode.NONE
    }
}

// ---------------------------------------------------------------------------
// Geometry helpers
// ---------------------------------------------------------------------------

private fun computeVideoBounds(canvasW: Float, canvasH: Float, vidW: Int, vidH: Int): Rect {
    val va = vidW.toFloat() / vidH.toFloat()
    val ca = canvasW / canvasH
    return if (va > ca) {
        val h = canvasW / va
        Rect(0f, (canvasH - h) / 2f, canvasW, (canvasH + h) / 2f)
    } else {
        val w = canvasH * va
        Rect((canvasW - w) / 2f, 0f, (canvasW + w) / 2f, canvasH)
    }
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

private fun DrawScope.drawOverlay(
    cL: Float, cT: Float, cR: Float, cB: Float,
    vb: Rect,
    handlePx: Float
) {
    val overlay = Color(0xBB000000)
    val white   = Color.White
    val grid    = Color.White.copy(alpha = 0.25f)
    val thin    = 1.5.dp.toPx()
    val thick   = 3.dp.toPx()
    val hLen    = handlePx

    // Dimming bars
    drawRect(overlay, Offset(vb.left, vb.top),  Size(vb.width,    cT - vb.top))
    drawRect(overlay, Offset(vb.left, cB),       Size(vb.width,    vb.bottom - cB))
    drawRect(overlay, Offset(vb.left, cT),       Size(cL - vb.left, cB - cT))
    drawRect(overlay, Offset(cR,      cT),       Size(vb.right - cR, cB - cT))

    val cW = cR - cL; val cH = cB - cT

    // Rule-of-thirds
    for (i in 1..2) {
        drawLine(grid, Offset(cL + cW * i / 3f, cT), Offset(cL + cW * i / 3f, cB), strokeWidth = thin)
        drawLine(grid, Offset(cL, cT + cH * i / 3f), Offset(cR, cT + cH * i / 3f), strokeWidth = thin)
    }

    // Border
    drawRect(white, Offset(cL, cT), Size(cW, cH), style = Stroke(width = thin))

    // Corner handles (L-shaped, thick)
    fun corner(x1: Float, y1: Float, xMid: Float, yMid: Float, x2: Float, y2: Float) {
        drawLine(white, Offset(x1, y1), Offset(xMid, yMid), strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(white, Offset(xMid, yMid), Offset(x2, y2), strokeWidth = thick, cap = StrokeCap.Round)
    }
    corner(cL, cT + hLen, cL, cT, cL + hLen, cT)          // TL
    corner(cR - hLen, cT, cR, cT, cR, cT + hLen)          // TR
    corner(cL, cB - hLen, cL, cB, cL + hLen, cB)          // BL
    corner(cR - hLen, cB, cR, cB, cR, cB - hLen)          // BR

    // Edge mid handles
    val mid = hLen * 0.55f
    drawLine(white, Offset(cL + cW / 2f - mid, cT), Offset(cL + cW / 2f + mid, cT), strokeWidth = thick, cap = StrokeCap.Round)
    drawLine(white, Offset(cL + cW / 2f - mid, cB), Offset(cL + cW / 2f + mid, cB), strokeWidth = thick, cap = StrokeCap.Round)
    drawLine(white, Offset(cL, cT + cH / 2f - mid), Offset(cL, cT + cH / 2f + mid), strokeWidth = thick, cap = StrokeCap.Round)
    drawLine(white, Offset(cR, cT + cH / 2f - mid), Offset(cR, cT + cH / 2f + mid), strokeWidth = thick, cap = StrokeCap.Round)
}
