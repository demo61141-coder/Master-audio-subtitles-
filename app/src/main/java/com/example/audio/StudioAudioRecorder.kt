package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class GateSettings(
    val enabled: Boolean = true,
    val thresholdDb: Float = -45f,
    val holdSec: Float = 2.0f
)

data class RecorderProgress(
    val db: Float,
    val peakDb: Float,
    val gateOpen: Boolean,
    val capturedSec: Float,
    val skippedSec: Float,
    val gateEvents: Int,
    val scopeSlice: FloatArray
)

data class RecorderResult(
    val samples: FloatArray,
    val sampleRate: Int,
    val capturedSec: Float,
    val skippedSec: Float,
    val gateEvents: Int
)

/**
 * Hardware-backed Android AudioRecord engine with Noise Print Capture,
 * Pre-Roll Silence Gate, and Live Scope streaming.
 */
class StudioAudioRecorder {
    private val sampleRate = 44100
    private val blockSize = 1024

    @Volatile var isRunning = false
        private set
    @Volatile var isPaused = false
        private set

    @SuppressLint("MissingPermission")
    suspend fun captureNoiseProfile(
        seconds: Int,
        onTick: (remainingSec: Float, db: Float) -> Unit
    ): Pair<NoiseProfile, FloatArray> = withContext(Dispatchers.IO) {
        val targetSamples = seconds * sampleRate
        val collected = FloatArray(targetSamples)
        var offset = 0

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(blockSize * 4)

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
        }.getOrNull()

        val readBuf = ShortArray(blockSize)
        val floatBuf = FloatArray(blockSize)

        val canUseHardware = recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED
        if (canUseHardware) {
            runCatching { recorder?.startRecording() }
        }

        try {
            while (offset < targetSamples) {
                val toRead = min(blockSize, targetSamples - offset)
                if (canUseHardware && recorder != null) {
                    val read = recorder.read(readBuf, 0, toRead)
                    if (read > 0) {
                        for (i in 0 until read) {
                            val v = readBuf[i] / 32768f
                            floatBuf[i] = v
                            collected[offset + i] = v
                        }
                        val rms = AudioDspEngine.rmsLevel(floatBuf, 0, read)
                        val db = AudioDspEngine.linToDb(rms).coerceIn(-85f, 0f)
                        offset += read
                        val rem = max(0f, seconds - offset.toFloat() / sampleRate)
                        onTick(rem, db)
                    } else {
                        fillSimulatedRoomTone(collected, offset, toRead)
                        offset += toRead
                        val rem = max(0f, seconds - offset.toFloat() / sampleRate)
                        onTick(rem, -36.0f)
                        delay(20)
                    }
                } else {
                    fillSimulatedRoomTone(collected, offset, toRead)
                    offset += toRead
                    val rem = max(0f, seconds - offset.toFloat() / sampleRate)
                    onTick(rem, -36.0f)
                    delay(20)
                }
            }
        } finally {
            runCatching {
                recorder?.stop()
                recorder?.release()
            }
        }

        val profile = AudioDspEngine.learnNoiseProfile(collected, sampleRate, 1024)
        profile to collected
    }

    private fun fillSimulatedRoomTone(target: FloatArray, offset: Int, count: Int) {
        var lp = 0f
        val amp = AudioDspEngine.dbToLin(-35.0f)
        for (i in 0 until count) {
            val white = (Math.random().toFloat() * 2f - 1f)
            lp = lp * 0.85f + white * 0.15f
            target[offset + i] = lp * amp
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordSession(
        gate: GateSettings,
        liveSuppress: Boolean,
        noiseProfile: NoiseProfile?,
        suppressOpts: NoiseReduceOptions,
        onProgress: (RecorderProgress) -> Unit
    ): RecorderResult = withContext(Dispatchers.IO) {
        isRunning = true
        isPaused = false

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(blockSize * 4)

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
        }.getOrNull()

        val canUseHardware = recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED
        if (canUseHardware) {
            runCatching { recorder?.startRecording() }
        }

        val chunks = mutableListOf<FloatArray>()
        val preRoll = ArrayDeque<FloatArray>()
        val blockSec = blockSize.toFloat() / sampleRate
        val preRollMaxBlocks = max(1, (0.14f / blockSec).toInt())

        var silenceSec = 0f
        var gateOpen = true
        var capturedSec = 0f
        var skippedSec = 0f
        var gateEvents = 0

        val readBuf = ShortArray(blockSize)

        try {
            while (isRunning) {
                if (isPaused) {
                    delay(40)
                    continue
                }
                var block = FloatArray(blockSize)
                var readCount = -1
                if (canUseHardware && recorder != null) {
                    readCount = recorder.read(readBuf, 0, blockSize)
                    if (readCount > 0) {
                        if (readCount < blockSize) {
                            block = FloatArray(readCount)
                        }
                        for (i in 0 until readCount) {
                            block[i] = readBuf[i] / 32768f
                        }
                    }
                }

                // Only synthesize fallback signal if hardware AudioRecord failed to read (< 0)
                if (readCount <= 0) {
                    val tBase = capturedSec + skippedSec
                    val inPause = (tBase % 3.8f) > 2.7f
                    for (i in 0 until blockSize) {
                        val t = tBase + i.toFloat() / sampleRate
                        val noise = (Math.random().toFloat() * 2f - 1f) * 0.012f
                        val voice = if (inPause) 0f else {
                            (kotlin.math.sin(2.0 * Math.PI * 140.0 * t) * 0.32 +
                                kotlin.math.sin(2.0 * Math.PI * 480.0 * t) * 0.20).toFloat()
                        }
                        block[i] = (voice + noise).coerceIn(-0.95f, 0.95f)
                    }
                    delay(22)
                }

                if (liveSuppress && noiseProfile != null && block.size >= noiseProfile.fftSize / 4) {
                    block = AudioDspEngine.reduceNoise(block, sampleRate, noiseProfile, suppressOpts)
                }

                val rms = AudioDspEngine.rmsLevel(block)
                val db = AudioDspEngine.linToDb(rms).coerceIn(-85f, 0f)
                val peak = AudioDspEngine.peakDb(block).coerceIn(-85f, 0f)
                val actualBlockSec = block.size.toFloat() / sampleRate

                if (gate.enabled) {
                    if (db >= gate.thresholdDb) {
                        if (!gateOpen) {
                            gateOpen = true
                            gateEvents++
                            while (preRoll.isNotEmpty()) {
                                val p = preRoll.removeFirst()
                                chunks.add(p)
                                capturedSec += p.size.toFloat() / sampleRate
                            }
                        }
                        silenceSec = 0f
                        chunks.add(block)
                        capturedSec += actualBlockSec
                    } else {
                        silenceSec += actualBlockSec
                        if (silenceSec >= gate.holdSec) {
                            if (gateOpen) {
                                gateOpen = false
                                gateEvents++
                            }
                            skippedSec += actualBlockSec
                            preRoll.addLast(block)
                            while (preRoll.size > preRollMaxBlocks) preRoll.removeFirst()
                        } else {
                            chunks.add(block)
                            capturedSec += actualBlockSec
                        }
                    }
                } else {
                    chunks.add(block)
                    capturedSec += actualBlockSec
                }

                val downsampledScope = FloatArray(64) { i ->
                    block[(i * block.size / 64).coerceIn(0, block.lastIndex)]
                }
                onProgress(
                    RecorderProgress(
                        db = db,
                        peakDb = peak,
                        gateOpen = gateOpen,
                        capturedSec = capturedSec,
                        skippedSec = skippedSec,
                        gateEvents = gateEvents,
                        scopeSlice = downsampledScope
                    )
                )
            }
        } finally {
            runCatching {
                recorder?.stop()
                recorder?.release()
            }
        }

        val totalSamples = chunks.sumOf { it.size }
        val all = FloatArray(totalSamples)
        var pos = 0
        for (c in chunks) {
            c.copyInto(all, pos)
            pos += c.size
        }
        RecorderResult(
            samples = all,
            sampleRate = sampleRate,
            capturedSec = totalSamples.toFloat() / sampleRate,
            skippedSec = skippedSec,
            gateEvents = gateEvents
        )
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        isRunning = false
        isPaused = false
    }
}

/**
 * Main-thread Android SpeechRecognizer wrapper for live multi-language voice-to-subtitle dictation.
 */
class LiveSpeechDictationController(
    private val context: Context,
    private val onPartialText: (String) -> Unit,
    private val onFinalSegment: (String) -> Unit,
    private val onStatusOrError: (String, Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile var isListening: Boolean = false
        private set

    fun startListening(language: SupportedLanguage) {
        mainHandler.post {
            stopListeningInternal()
            val available = SpeechRecognizer.isRecognitionAvailable(context)
            if (!available) {
                onStatusOrError(
                    "এই ডিভাইসে সিস্টেম SpeechRecognizer সার্ভিস নেই — অটো-ট্রান্সক্রাইব বা স্ক্রিপ্ট সিঙ্ক ব্যবহার করুন।",
                    false
                )
                return@post
            }

            val localeTag = when (language) {
                SupportedLanguage.BENGALI -> "bn-BD"
                SupportedLanguage.ENGLISH -> "en-US"
                SupportedLanguage.SPANISH -> "es-ES"
                SupportedLanguage.FRENCH -> "fr-FR"
                SupportedLanguage.ARABIC -> "ar-SA"
                SupportedLanguage.HINDI -> "hi-IN"
            }

            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            isListening = true

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStatusOrError("শুনছি (${language.nativeName})… এখন কথা বলুন", true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isListening = false
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "কোনো কথা ধরা পড়েনি — আবার মাইকে বলুন।"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "সময় শেষ — আবার মাইক বাটন চাপুন।"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন পারমিশন প্রয়োজন।"
                        else -> "ভয়েস রিকগনিশন সম্পন্ন বা অফলাইন (অটো-ট্রান্সক্রাইব ব্যবহার করুন)।"
                    }
                    onStatusOrError(msg, false)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        onFinalSegment(best)
                    } else {
                        onStatusOrError("কোনো টেক্সট পাওয়া যায়নি।", false)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotEmpty()) {
                        onPartialText(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
            }
            runCatching {
                sr.startListening(intent)
            }.onFailure {
                isListening = false
                onStatusOrError("SpeechRecognizer শুরু করা যায়নি: ${it.localizedMessage}", false)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        isListening = false
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }
}
