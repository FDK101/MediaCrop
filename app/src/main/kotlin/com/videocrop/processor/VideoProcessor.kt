package com.videocrop.processor

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import com.videocrop.viewmodel.CropRect
import com.videocrop.viewmodel.VideoInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

sealed class ProcessingState {
    data class Progress(val progress: Float) : ProcessingState()
    data class Completed(val outputPath: String) : ProcessingState()
    data class Failed(val message: String) : ProcessingState()
}

object VideoProcessor {

    private const val TIMEOUT_US = 10_000L
    private const val MIN_BITRATE = 8_000_000

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
        val durationUs = (endMs - startMs).coerceAtLeast(1L) * 1000L
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        try {
            // ── Input ────────────────────────────────────────────────────────
            val extractor = MediaExtractor()
            extractor.setDataSource(context, videoInfo.uri, null)

            var videoTrack = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrack = i
                    inputFormat = fmt
                    break
                }
            }
            if (videoTrack == -1 || inputFormat == null) {
                trySend(ProcessingState.Failed("No video track found"))
                close(); return@callbackFlow
            }
            extractor.selectTrack(videoTrack)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val srcW = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
            val srcH = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val srcFps = if (inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
                inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
            val srcBitrate = if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE))
                inputFormat.getInteger(MediaFormat.KEY_BIT_RATE) else MIN_BITRATE

            // ── Crop dimensions ──────────────────────────────────────────────
            val cropW = ((cropRect.right - cropRect.left) * videoInfo.displayWidth)
                .toInt().roundToEven().coerceIn(2, videoInfo.displayWidth)
            val cropH = ((cropRect.bottom - cropRect.top) * videoInfo.displayHeight)
                .toInt().roundToEven().coerceIn(2, videoInfo.displayHeight)

            // ── Encoder ──────────────────────────────────────────────────────
            val encFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, cropW, cropH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, maxOf(srcBitrate, MIN_BITRATE))
                setInteger(MediaFormat.KEY_FRAME_RATE, srcFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encSurface = encoder.createInputSurface()
            encoder.start()

            // ── EGL ──────────────────────────────────────────────────────────
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(eglDisplay, null, 0, null, 0)
            val cfgAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val eglCfgs = arrayOfNulls<android.opengl.EGLConfig>(1)
            EGL14.eglChooseConfig(eglDisplay, cfgAttribs, 0, eglCfgs, 0, 1, intArrayOf(0), 0)
            val eglCtx = EGL14.eglCreateContext(
                eglDisplay, eglCfgs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            val winAttribs = intArrayOf(EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
            val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglCfgs[0], encSurface, winAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglCtx)

            // ── GL texture + SurfaceTexture ───────────────────────────────────
            val texId = intArrayOf(0)
            GLES20.glGenTextures(1, texId, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val st = SurfaceTexture(texId[0])
            val decoderSurface = Surface(st)

            // ── Frame-available sync ──────────────────────────────────────────
            val lock = ReentrantLock()
            val cond = lock.newCondition()
            var frameReady = false
            val callbackThread = HandlerThread("st-cb").apply { start() }
            st.setOnFrameAvailableListener({
                lock.lock(); frameReady = true; cond.signal(); lock.unlock()
            }, Handler(callbackThread.looper))

            // ── GL shader for crop ────────────────────────────────────────────
            val program = buildCropProgram()
            val aPos = GLES20.glGetAttribLocation(program, "aPos")
            val aUV  = GLES20.glGetAttribLocation(program, "aUV")
            val uST  = GLES20.glGetUniformLocation(program, "uSTMatrix")

            // Full-screen quad (TRIANGLE_STRIP: bl, br, tl, tr)
            val quadVerts = buf(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))

            // UV coords map the crop region (display-space 0-1) onto the quad
            val u0 = cropRect.left;  val u1 = cropRect.right
            val v0 = cropRect.top;   val v1 = cropRect.bottom
            val quadUV = buf(floatArrayOf(u0, v1, u1, v1, u0, v0, u1, v0))

            // ── Decoder ───────────────────────────────────────────────────────
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, decoderSurface, null, 0)
            decoder.start()

            // ── Muxer ─────────────────────────────────────────────────────────
            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxTrack = -1
            var muxStarted = false

            // ── Processing loop ───────────────────────────────────────────────
            var decoderEOS = false
            var encoderEOS = false
            val encInfo = MediaCodec.BufferInfo()
            val decInfo = MediaCodec.BufferInfo()
            val stMatrix = FloatArray(16)
            var firstPts = Long.MIN_VALUE

            while (!encoderEOS) {
                // Feed compressed frames into decoder
                if (!decoderEOS) {
                    val idx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val sampleTime = extractor.sampleTime
                        if (sampleTime < 0 || sampleTime > endUs) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderEOS = true
                        } else {
                            val buf = decoder.getInputBuffer(idx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                decoderEOS = true
                            } else {
                                decoder.queueInputBuffer(idx, 0, size, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Pull decoded frames and render to SurfaceTexture
                val dIdx = decoder.dequeueOutputBuffer(decInfo, TIMEOUT_US)
                if (dIdx >= 0) {
                    val isEOS = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val skip  = decInfo.presentationTimeUs < startUs

                    if (!isEOS && !skip) {
                        if (firstPts == Long.MIN_VALUE) firstPts = decInfo.presentationTimeUs
                        val outPts = decInfo.presentationTimeUs - firstPts

                        decoder.releaseOutputBuffer(dIdx, true)     // render to st

                        lock.lock()
                        while (!frameReady) cond.await(500, TimeUnit.MILLISECONDS)
                        frameReady = false
                        lock.unlock()

                        st.updateTexImage()
                        st.getTransformMatrix(stMatrix)

                        // Draw crop to encoder surface
                        GLES20.glViewport(0, 0, cropW, cropH)
                        GLES20.glUseProgram(program)
                        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadVerts)
                        GLES20.glEnableVertexAttribArray(aPos)
                        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, quadUV)
                        GLES20.glEnableVertexAttribArray(aUV)
                        GLES20.glUniformMatrix4fv(uST, 1, false, stMatrix, 0)
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0])
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, outPts * 1000L)
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                        trySend(ProcessingState.Progress((outPts.toFloat() / durationUs).coerceIn(0f, 0.99f)))
                    } else if (isEOS) {
                        decoder.releaseOutputBuffer(dIdx, false)
                        encoder.signalEndOfInputStream()
                    } else {
                        decoder.releaseOutputBuffer(dIdx, false)
                    }
                }

                // Pull encoded packets into muxer
                val eIdx = encoder.dequeueOutputBuffer(encInfo, TIMEOUT_US)
                when {
                    eIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxStarted = true
                    }
                    eIdx >= 0 -> {
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encoderEOS = true
                        val isConfig = (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (muxStarted && encInfo.size > 0 && !isConfig) {
                            val outBuf = encoder.getOutputBuffer(eIdx)!!
                            outBuf.position(encInfo.offset)
                            outBuf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxTrack, outBuf, encInfo)
                        }
                        encoder.releaseOutputBuffer(eIdx, false)
                    }
                }
            }

            // ── Teardown ──────────────────────────────────────────────────────
            decoder.stop(); decoder.release()
            encoder.stop(); encoder.release()
            muxer.stop();   muxer.release()
            extractor.release()
            decoderSurface.release()
            st.release()
            callbackThread.quitSafely()
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglCtx)
            EGL14.eglTerminate(eglDisplay)
            encSurface.release()

            val savedPath = saveToPublicStorage(context, tempFile, fileName)
            tempFile.delete()
            trySend(ProcessingState.Completed(savedPath))

        } catch (e: Exception) {
            tempFile.delete()
            trySend(ProcessingState.Failed(e.message ?: "Processing failed"))
        }
        close()

        awaitClose { tempFile.delete() }
    }

    private fun buildCropProgram(): Int {
        val vs = """
            attribute vec4 aPos;
            attribute vec2 aUV;
            uniform mat4 uSTMatrix;
            varying vec2 vUV;
            void main() {
                gl_Position = aPos;
                vUV = (uSTMatrix * vec4(aUV, 0.0, 1.0)).xy;
            }
        """.trimIndent()
        val fs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUV;
            uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, vUV); }
        """.trimIndent()
        fun shader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    private fun buf(data: FloatArray) =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

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
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Cropped")
            dir.mkdirs()
            val dest = File(dir, fileName)
            tempFile.copyTo(dest, overwrite = true)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
            dest.absolutePath
        }
    }

    private fun Int.roundToEven() = if (this % 2 == 0) this else this + 1
}
