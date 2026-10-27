package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AudioExportFormat(
    val id: String,
    val label: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    WAV_16("wav16", "WAV 16-bit (Standard)", "wav", "audio/wav", "লসলেস স্ট্যান্ডার্ড CD কোয়ালিটি অডিও"),
    WAV_24("wav24", "WAV 24-bit (Studio Master)", "wav", "audio/wav", "হাই-ডাইনামিক রেঞ্জ প্রো স্টুডিও মাস্টার"),
    M4A_AAC("m4a", "M4A / AAC (Compressed)", "m4a", "audio/mp4", "ছোট ফাইল সাইজ, মোবাইল ও সোশ্যাল মিডিয়ার জন্য সেরা"),
    WAV_8K_VOICE("wav8k", "WAV 16kHz (Voice/ASR Optimized)", "wav", "audio/wav", "সাবটাইটেল ও ভয়েস আর্কাইভের জন্য হালকা ফরম্যাট")
}

object AudioExporter {

    fun encodeWavBytes(samples: FloatArray, sampleRate: Int, bitDepth: Int = 16): ByteArray {
        val bytesPerSample = bitDepth / 8
        val dataSize = samples.size * bytesPerSample
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * bytesPerSample)
        buffer.putShort(bytesPerSample.toShort())
        buffer.putShort(bitDepth.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        if (bitDepth == 16) {
            for (s in samples) {
                val clamped = s.coerceIn(-1f, 1f)
                val v = if (clamped < 0f) (clamped * 32768f).toInt() else (clamped * 32767f).toInt()
                buffer.putShort(v.coerceIn(-32768, 32767).toShort())
            }
        } else {
            for (s in samples) {
                val clamped = s.coerceIn(-1f, 1f)
                val v = if (clamped < 0f) (clamped * 8388608f).roundToInt() else (clamped * 8388607f).roundToInt()
                buffer.put((v and 0xFF).toByte())
                buffer.put(((v shr 8) and 0xFF).toByte())
                buffer.put(((v shr 16) and 0xFF).toByte())
            }
        }
        return buffer.array()
    }

    private fun resampleLinear(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || samples.isEmpty()) return samples
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = max(1, (samples.size / ratio).toInt())
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val next = min(samples.lastIndex, idx + 1)
            val frac = (srcPos - idx).toFloat()
            out[i] = samples[idx] * (1f - frac) + samples[next] * frac
        }
        return out
    }

    suspend fun exportAudioFile(
        context: Context,
        samples: FloatArray,
        sampleRate: Int,
        baseName: String,
        format: AudioExportFormat
    ): File = withContext(Dispatchers.IO) {
        val safeName = baseName.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_").ifBlank { "subcraft_audio" }
        val outDir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileName = "${safeName}_${System.currentTimeMillis() % 100000}.${format.extension}"
        val outFile = File(outDir, fileName)

        when (format) {
            AudioExportFormat.WAV_16 -> {
                FileOutputStream(outFile).use { it.write(encodeWavBytes(samples, sampleRate, 16)) }
            }
            AudioExportFormat.WAV_24 -> {
                FileOutputStream(outFile).use { it.write(encodeWavBytes(samples, sampleRate, 24)) }
            }
            AudioExportFormat.WAV_8K_VOICE -> {
                val resampled = resampleLinear(samples, sampleRate, 16000)
                FileOutputStream(outFile).use { it.write(encodeWavBytes(resampled, 16000, 16)) }
            }
            AudioExportFormat.M4A_AAC -> {
                val encodedOk = runCatching {
                    encodeAacM4a(samples, sampleRate, outFile)
                }.getOrDefault(false)
                if (!encodedOk) {
                    FileOutputStream(outFile).use { it.write(encodeWavBytes(samples, sampleRate, 16)) }
                }
            }
        }

        // Also copy to public Downloads/SubCraftStudio so the user can find it immediately in their device's Files app
        runCatching {
            SubtitleEngine.saveBytesToPublicDownloads(
                context = context,
                fileName = fileName,
                mimeType = format.mimeType,
                bytes = outFile.readBytes()
            )
        }
        outFile
    }

    private fun encodeAacM4a(samples: FloatArray, sampleRate: Int, outFile: File): Boolean {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var sampleIndex = 0
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inBufIndex = codec.dequeueInputBuffer(10000)
                    if (inBufIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inBufIndex)!!
                        inBuf.clear()
                        val maxSamples = min(inBuf.remaining() / 2, samples.size - sampleIndex)
                        if (maxSamples <= 0) {
                            val ptsUs = (sampleIndex.toLong() * 1_000_000L) / sampleRate
                            codec.queueInputBuffer(inBufIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (i in 0 until maxSamples) {
                                val s = samples[sampleIndex + i].coerceIn(-1f, 1f)
                                val pcm16 = (s * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                                inBuf.put((pcm16.toInt() and 0xFF).toByte())
                                inBuf.put(((pcm16.toInt() shr 8) and 0xFF).toByte())
                            }
                            val ptsUs = (sampleIndex.toLong() * 1_000_000L) / sampleRate
                            sampleIndex += maxSamples
                            codec.queueInputBuffer(inBufIndex, 0, maxSamples * 2, ptsUs, 0)
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    else -> {
                        if (inputDone) outputDone = true
                    }
                }
            }
            return true
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    suspend fun exportChunksZip(
        context: Context,
        samples: FloatArray,
        sampleRate: Int,
        ranges: List<TimeRange>,
        baseName: String
    ): File = withContext(Dispatchers.IO) {
        val safeName = baseName.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_").ifBlank { "chunks" }
        val outDir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileName = "${safeName}_chunks_${ranges.size}.zip"
        val zipFile = File(outDir, fileName)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            ranges.take(100).forEachIndexed { idx, r ->
                val slice = AudioDspEngine.extractRange(samples, sampleRate, r, 4)
                val wavBytes = encodeWavBytes(slice, sampleRate, 16)
                val entry = ZipEntry("${safeName}_part_${"%02d".format(idx + 1)}.wav")
                zos.putNextEntry(entry)
                zos.write(wavBytes)
                zos.closeEntry()
            }
        }
        runCatching {
            SubtitleEngine.saveBytesToPublicDownloads(
                context = context,
                fileName = fileName,
                mimeType = "application/zip",
                bytes = zipFile.readBytes()
            )
        }
        zipFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String = "ফাইল শেয়ার / সেভ করুন") {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    suspend fun decodeAudioUri(context: Context, uri: Uri): Pair<FloatArray, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
                    return@withContext parseWavBytes(bytes)
                }
            }
        }

        runCatching {
            val extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return@runCatching null

            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) {
                extractor.release()
                return@runCatching null
            }

            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var isEOS = false

            while (!isEOS) {
                val inIdx = codec.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val read = extractor.readSampleData(buf, 0)
                    if (read < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    val chunk = ByteArray(info.size)
                    outBuf.get(chunk)
                    outBuf.clear()
                    pcmOut.write(chunk)
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && extractor.sampleTime < 0) {
                    isEOS = true
                }
            }
            codec.stop()
            codec.release()
            extractor.release()

            val rawBytes = pcmOut.toByteArray()
            val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val totalFrames = shortBuf.remaining() / channels
            val mono = FloatArray(totalFrames)
            for (i in 0 until totalFrames) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += shortBuf.get() / 32768f
                }
                mono[i] = (sum / channels).coerceIn(-1f, 1f)
            }
            mono to sampleRate
        }.getOrNull()
    }

    fun parseWavBytes(bytes: ByteArray): Pair<FloatArray, Int>? {
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buf.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = buf.getInt(24).coerceIn(8000, 96000)
        val bitsPerSample = buf.getShort(34).toInt()
        var dataOffset = 44
        for (i in 12 until min(bytes.size - 8, 256)) {
            if (bytes[i] == 'd'.code.toByte() && bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 'a'.code.toByte()
            ) {
                dataOffset = i + 8
                break
            }
        }
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(2)
        val frameSize = bytesPerSample * channels
        val totalFrames = (bytes.size - dataOffset) / frameSize
        if (totalFrames <= 0) return null
        val mono = FloatArray(totalFrames)
        var pos = dataOffset
        for (i in 0 until totalFrames) {
            var sum = 0f
            for (c in 0 until channels) {
                if (pos + 2 <= bytes.size) {
                    val s = buf.getShort(pos) / 32768f
                    sum += s
                }
                pos += bytesPerSample
            }
            mono[i] = (sum / channels).coerceIn(-1f, 1f)
        }
        return mono to sampleRate
    }
}

/**
 * Low-latency PCM audio player with live seeking while playing, range selection playback, and looping.
 */
class PcmSamplePlayer {
    private var track: AudioTrack? = null
    private val pendingSeekSample = AtomicInteger(-1)
    private var currentSampleRate = 44100

    @Volatile var isPlaying: Boolean = false
        private set

    fun seekWhilePlaying(sec: Float) {
        if (isPlaying && currentSampleRate > 0) {
            val targetIdx = (sec * currentSampleRate).roundToInt().coerceAtLeast(0)
            pendingSeekSample.set(targetIdx)
        }
    }

    fun stop() {
        isPlaying = false
        pendingSeekSample.set(-1)
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }

    suspend fun playRange(
        samples: FloatArray,
        sampleRate: Int,
        startSec: Float,
        endSec: Float,
        loop: Boolean,
        onPositionSec: (Float) -> Unit,
        onComplete: () -> Unit
    ) = withContext(Dispatchers.IO) {
        stop()
        if (samples.isEmpty()) {
            onComplete()
            return@withContext
        }
        currentSampleRate = sampleRate
        val startIdx = (startSec * sampleRate).roundToInt().coerceIn(0, samples.lastIndex)
        val endIdx = (endSec * sampleRate).roundToInt().coerceIn(startIdx + 1, samples.size)
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        isPlaying = true
        audioTrack.play()

        val chunkFrames = 1024
        val pcm16 = ShortArray(chunkFrames)

        try {
            do {
                var cursor = startIdx
                while (isPlaying && cursor < endIdx) {
                    val requestedSeek = pendingSeekSample.getAndSet(-1)
                    if (requestedSeek >= 0) {
                        cursor = requestedSeek.coerceIn(0, samples.lastIndex)
                    }
                    val count = min(chunkFrames, endIdx - cursor)
                    if (count <= 0) break
                    for (i in 0 until count) {
                        val v = samples[cursor + i].coerceIn(-1f, 1f)
                        pcm16[i] = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    audioTrack.write(pcm16, 0, count)
                    cursor += count
                    onPositionSec(cursor.toFloat() / sampleRate)
                }
            } while (isPlaying && loop)
        } finally {
            val finishedNaturally = isPlaying
            stop()
            if (finishedNaturally) onComplete()
        }
    }
}
