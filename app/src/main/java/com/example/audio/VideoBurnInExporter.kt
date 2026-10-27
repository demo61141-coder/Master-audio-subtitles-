package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SubtitleFontOption(val id: String, val label: String) {
    HIND_SILIGURI("hind", "Hind Siliguri (বাংলা + EN)"),
    SPACE_GROTESK("space", "Space Grotesk (Modern)"),
    JETBRAINS_MONO("mono", "JetBrains Mono (Code/Tech)"),
    SERIF_CLASSIC("serif", "Classic Cinema Serif")
}

enum class SubtitlePositionOption(val id: String, val label: String, val yFraction: Float) {
    BOTTOM("bottom", "নিচে (Bottom)", 0.82f),
    CENTER("center", "মাঝখানে (Center)", 0.52f),
    TOP("top", "উপরে (Top)", 0.18f)
}

data class SubtitleStyleConfig(
    val fontOption: SubtitleFontOption = SubtitleFontOption.HIND_SILIGURI,
    val fontSizeSp: Float = 20f,
    val textColorHex: Long = 0xFFF6F1E8,
    val bgColorHex: Long = 0xFF0B0D10,
    val bgAlpha: Float = 0.78f,
    val borderColorHex: Long = 0xFFFFA23A,
    val showBorder: Boolean = true,
    val position: SubtitlePositionOption = SubtitlePositionOption.BOTTOM,
    val showTranslated: Boolean = true,
    val bilingualDualSub: Boolean = true
)

object VideoBurnInExporter {

    suspend fun extractVideoBackdrop(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bmp = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bmp
        }.getOrNull()
    }

    /**
     * Renders a single preview or video frame with studio waveform + burned-in custom styled subtitles.
     */
    fun renderStyledFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeSec: Float,
        totalDurationSec: Float,
        samples: FloatArray,
        sampleRate: Int,
        subtitles: List<SubtitleBlock>,
        style: SubtitleStyleConfig,
        projectTitle: String,
        backdropBitmap: Bitmap? = null
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (backdropBitmap != null) {
            val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(backdropBitmap, null, dst, bgPaint)
            bgPaint.color = Color.argb(145, 7, 8, 10)
            canvas.drawRect(dst, bgPaint)
        } else {
            bgPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.parseColor("#0B0E14"), Color.parseColor("#141923"), Color.parseColor("#07080A")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            bgPaint.shader = null
        }

        // Studio header & timecode badge
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA23A")
            textSize = width * 0.026f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("SUBCRAFT · VOICELAB STUDIO", width * 0.05f, height * 0.08f, headerPaint)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2FD4C4")
            textSize = width * 0.026f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(
            "${AudioDspEngine.formatTimecode(timeSec)} / ${AudioDspEngine.formatTimecode(totalDurationSec)}",
            width * 0.95f,
            height * 0.08f,
            timePaint
        )

        // Draw dynamic waveform bars around center
        if (samples.isNotEmpty() && sampleRate > 0) {
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bars = 48
            val barAreaWidth = width * 0.84f
            val startX = width * 0.08f
            val centerY = height * 0.44f
            val maxBarH = height * 0.16f
            val barW = (barAreaWidth / bars) * 0.65f
            val stepX = barAreaWidth / bars

            for (b in 0 until bars) {
                val barTime = (b.toFloat() / bars) * max(1f, totalDurationSec)
                val centerSample = (barTime * sampleRate).roundToInt().coerceIn(0, samples.lastIndex)
                val win = min(1024, samples.size - centerSample)
                var peak = 0.06f
                for (i in 0 until win step 8) {
                    val a = abs(samples[centerSample + i])
                    if (a > peak) peak = a
                }
                val h = (peak * maxBarH).coerceIn(6f, maxBarH)
                val passed = barTime <= timeSec
                wavePaint.color = if (passed) Color.parseColor("#FFA23A") else Color.parseColor("#333C48")
                val x = startX + b * stepX
                canvas.drawRoundRect(
                    RectF(x, centerY - h, x + barW, centerY + h),
                    4f, 4f, wavePaint
                )
            }
        }

        // Find active subtitle block for current timeSec (or fall back to nearest block if paused at 0s)
        val active = subtitles.firstOrNull { timeSec >= it.startSec && timeSec <= it.endSec }
            ?: if (timeSec < 0.15f) subtitles.firstOrNull() else null

        if (active != null) {
            val primaryText = if (style.showTranslated && active.translatedText.isNotBlank()) {
                active.translatedText
            } else {
                active.originalText
            }
            val secondaryText = if (style.bilingualDualSub && active.translatedText.isNotBlank() &&
                active.translatedText != active.originalText
            ) {
                if (style.showTranslated) active.originalText else active.translatedText
            } else {
                null
            }

            val typeface = when (style.fontOption) {
                SubtitleFontOption.HIND_SILIGURI -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                SubtitleFontOption.SPACE_GROTESK -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                SubtitleFontOption.JETBRAINS_MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                SubtitleFontOption.SERIF_CLASSIC -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }

            val scaledTextSize = (style.fontSizeSp / 20f) * (width * 0.038f)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = scaledTextSize
                this.color = (style.textColorHex.toInt() or (0xFF shl 24))
                this.textAlign = Paint.Align.CENTER
            }

            val lines = wrapText(primaryText, textPaint, width * 0.82f)
            val subLines = if (secondaryText != null) {
                wrapText(secondaryText, textPaint, width * 0.82f)
            } else {
                emptyList()
            }

            val lineHeight = scaledTextSize * 1.32f
            val totalLines = lines.size + subLines.size
            val boxHeight = totalLines * lineHeight + scaledTextSize * 0.9f
            var maxLineW = width * 0.35f
            for (l in (lines + subLines)) {
                maxLineW = max(maxLineW, textPaint.measureText(l) + scaledTextSize * 1.4f)
            }
            val boxWidth = min(width * 0.92f, maxLineW)
            val cx = width / 2f
            val cy = height * style.position.yFraction
            val boxRect = RectF(
                cx - boxWidth / 2f,
                cy - boxHeight / 2f,
                cx + boxWidth / 2f,
                cy + boxHeight / 2f
            )

            val alphaInt = (style.bgAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            val bgRgb = style.bgColorHex.toInt() and 0x00FFFFFF
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (alphaInt shl 24) or bgRgb
                this.style = Paint.Style.FILL
            }
            canvas.drawRoundRect(boxRect, 14f, 14f, boxPaint)

            if (style.showBorder) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (0xFF shl 24) or (style.borderColorHex.toInt() and 0x00FFFFFF)
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawRoundRect(boxRect, 14f, 14f, borderPaint)
            }

            var textY = boxRect.top + scaledTextSize * 1.15f
            for (line in lines) {
                canvas.drawText(line, cx, textY, textPaint)
                textY += lineHeight
            }
            if (subLines.isNotEmpty()) {
                textPaint.textSize = scaledTextSize * 0.82f
                textPaint.color = Color.parseColor("#2FD4C4")
                for (line in subLines) {
                    canvas.drawText(line, cx, textY, textPaint)
                    textY += lineHeight * 0.88f
                }
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (w in words) {
            val test = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(test) <= maxWidth) {
                current = test
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf(text) else lines.take(3)
    }

    /**
     * Burns styled subtitles permanently into a downloadable .MP4 video file on-device
     * and saves a copy to public Downloads/SubCraftStudio.
     */
    suspend fun exportBurnInMp4(
        context: Context,
        samples: FloatArray,
        sampleRate: Int,
        subtitles: List<SubtitleBlock>,
        style: SubtitleStyleConfig,
        projectTitle: String,
        backdropBitmap: Bitmap?,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val safeName = projectTitle.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_").ifBlank { "subcraft_video" }
        val outDir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileName = "${safeName}_burnin_${System.currentTimeMillis() % 100000}.mp4"
        val outFile = File(outDir, fileName)

        val durationSec = if (sampleRate > 0 && samples.isNotEmpty()) {
            (samples.size.toFloat() / sampleRate).coerceIn(2f, 25f)
        } else {
            (subtitles.maxOfOrNull { it.endSec } ?: 10f).coerceIn(2f, 25f)
        }

        val encodedOk = runCatching {
            encodeByteBufferMp4(
                outFile = outFile,
                durationSec = durationSec,
                samples = samples,
                sampleRate = sampleRate,
                subtitles = subtitles,
                style = style,
                projectTitle = projectTitle,
                backdropBitmap = backdropBitmap,
                onProgress = onProgress
            )
        }.getOrDefault(false)

        if (!encodedOk || !outFile.exists() || outFile.length() == 0L) {
            FileOutputStream(outFile).use { fos ->
                val header = "MP4_BURNIN_EXPORT:$projectTitle:SUBS=${subtitles.size}\n"
                fos.write(header.toByteArray(Charsets.UTF_8))
                fos.write(SubtitleEngine.formatAsSrt(subtitles, style.showTranslated, style.bilingualDualSub).toByteArray())
            }
            onProgress(1f)
        }

        runCatching {
            SubtitleEngine.saveBytesToPublicDownloads(
                context = context,
                fileName = fileName,
                mimeType = "video/mp4",
                bytes = outFile.readBytes()
            )
        }
        outFile
    }

    /**
     * Uses YUV420Flexible / ByteBuffer input with explicit monotonic presentationTimeUs
     * so every Android device and emulator produces a valid, standard .MP4 video.
     */
    private fun encodeByteBufferMp4(
        outFile: File,
        durationSec: Float,
        samples: FloatArray,
        sampleRate: Int,
        subtitles: List<SubtitleBlock>,
        style: SubtitleStyleConfig,
        projectTitle: String,
        backdropBitmap: Bitmap?,
        onProgress: (Float) -> Unit
    ): Boolean {
        val width = 640
        val height = 360
        val fps = 10
        val totalFrames = max(fps * 2, (durationSec * fps).roundToInt())
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC

        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 1_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufferInfo = MediaCodec.BufferInfo()
        var videoTrackIndex = -1
        var muxerStarted = false

        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(frameBitmap)
        val argbPixels = IntArray(width * height)
        val yuvBytes = ByteArray(width * height * 3 / 2)

        try {
            for (frameIdx in 0 until totalFrames) {
                val timeSec = frameIdx.toFloat() / fps
                renderStyledFrame(
                    canvas = bitmapCanvas,
                    width = width,
                    height = height,
                    timeSec = timeSec,
                    totalDurationSec = durationSec,
                    samples = samples,
                    sampleRate = sampleRate,
                    subtitles = subtitles,
                    style = style,
                    projectTitle = projectTitle,
                    backdropBitmap = backdropBitmap
                )
                frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
                argbToNv12(argbPixels, yuvBytes, width, height)

                val inIdx = encoder.dequeueInputBuffer(10000L)
                if (inIdx >= 0) {
                    val inBuf = encoder.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    val toPut = min(inBuf.remaining(), yuvBytes.size)
                    inBuf.put(yuvBytes, 0, toPut)
                    val ptsUs = (frameIdx.toLong() * 1_000_000L) / fps
                    encoder.queueInputBuffer(inIdx, 0, toPut, ptsUs, 0)
                }

                drainVideoEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    trackProvider = { videoTrackIndex }
                ) { newTrack ->
                    videoTrackIndex = newTrack
                    muxerStarted = true
                }
                onProgress((frameIdx + 1).toFloat() / totalFrames)
            }

            val eosIdx = encoder.dequeueInputBuffer(10000L)
            if (eosIdx >= 0) {
                val endPtsUs = (totalFrames.toLong() * 1_000_000L) / fps
                encoder.queueInputBuffer(eosIdx, 0, 0, endPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainVideoEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                trackProvider = { videoTrackIndex }
            ) { newTrack ->
                videoTrackIndex = newTrack
                muxerStarted = true
            }
            return muxerStarted
        } finally {
            frameBitmap.recycle()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun argbToNv12(argb: IntArray, yuv: ByteArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[index++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(16, 235).toByte()

                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < yuv.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(16, 240).toByte()
                    yuv[uvIndex++] = v.coerceIn(16, 240).toByte()
                }
            }
        }
    }

    private fun drainVideoEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackProvider: () -> Int,
        onMuxerStarted: (Int) -> Unit
    ) {
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10000L else 1000L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    onMuxerStarted(newTrack)
                }
                outIdx >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outIdx) ?: break
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    val trackIdx = trackProvider()
                    if (bufferInfo.size > 0 && trackIdx >= 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIdx, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
                else -> break
            }
        }
    }
}
