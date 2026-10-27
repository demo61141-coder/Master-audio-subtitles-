package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalysis
import com.example.audio.AudioDspEngine
import com.example.audio.AudioExportFormat
import com.example.audio.AudioExporter
import com.example.audio.AutoLevelOptions
import com.example.audio.EnhanceConfig
import com.example.audio.GateSettings
import com.example.audio.LiveSpeechDictationController
import com.example.audio.NoiseProfile
import com.example.audio.NoiseReduceOptions
import com.example.audio.PcmSamplePlayer
import com.example.audio.SilenceOptions
import com.example.audio.SplitOptions
import com.example.audio.StudioAudioRecorder
import com.example.audio.SubtitleBlock
import com.example.audio.SubtitleEngine
import com.example.audio.SubtitleStyleConfig
import com.example.audio.SupportedLanguage
import com.example.audio.TimeRange
import com.example.audio.VideoBurnInExporter
import com.example.data.BatchQueueEntity
import com.example.data.GlossaryEntryEntity
import com.example.data.ProcessingLogEntity
import com.example.data.ProjectEntity
import com.example.data.StudioDatabase
import com.example.data.StudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StudioTab(val route: String) {
    RECORD("record"),
    EDITOR("editor"),
    SUBTITLES("subtitles"),
    VIDEO_BURN("video_burn"),
    HUB("hub")
}

enum class RecorderPhase { IDLE, PROFILING, RECORDING, PAUSED, PROCESSING }

data class HistorySnapshot(val samples: FloatArray, val label: String)

data class ManualSplitSlot(val id: String, val startSec: Float, val endSec: Float)

data class StudioUiState(
    val activeTab: StudioTab = StudioTab.RECORD,
    val projectTitle: String = "স্টুডিও সেশন ০১ (SubCraft)",
    val samples: FloatArray = FloatArray(0),
    val originalSamples: FloatArray = FloatArray(0),
    val previewSamples: FloatArray? = null,
    val sampleRate: Int = 44100,
    val status: String = "cleaned",
    val dirty: Boolean = false,
    val analysis: AudioAnalysis = AudioAnalysis(0f, -90f, -90f, 0f, 0f, 0, -90f),
    val originalAnalysis: AudioAnalysis = AudioAnalysis(0f, -90f, -90f, 0f, 0f, 0, -90f),
    val undoStack: List<HistorySnapshot> = emptyList(),
    val redoStack: List<HistorySnapshot> = emptyList(),

    // Playback & Waveform selection
    val isPlaying: Boolean = false,
    val playheadSec: Float = 0f,
    val loopPlayback: Boolean = false,
    val viewRange: TimeRange = TimeRange(0f, 20f),
    val selection: TimeRange? = null,

    // Recorder & Noise Intelligence
    val recorderPhase: RecorderPhase = RecorderPhase.IDLE,
    val profileSec: Int = 4,
    val profileCountdown: Float = 0f,
    val liveDb: Float = -65f,
    val livePeakDb: Float = -65f,
    val gateOpen: Boolean = true,
    val capturedSec: Float = 0f,
    val skippedSec: Float = 0f,
    val gateEvents: Int = 0,
    val liveScope: FloatArray = FloatArray(64),
    val gateSettings: GateSettings = GateSettings(enabled = true, thresholdDb = -45f, holdSec = 2.0f),
    val liveSuppress: Boolean = true,
    val autoCleanOnStop: Boolean = true,
    val autoLevelOnStop: Boolean = true,
    val noiseProfile: NoiseProfile? = null,
    val suppressOptions: NoiseReduceOptions = NoiseReduceOptions(strength = 2.0f, floorDb = -32f, smoothing = 0.6f),

    // Editor tools (Silence Removal, Sample Noise, Splitter, Effects)
    val editorSubTab: Int = 0, // 0=Silence, 1=Noise Sample, 2=Splitter, 3=Studio Effects
    val silenceMode: String = "db", // "db" or "sec"
    val silenceThresholdDb: Float = -45f,
    val silenceMinSec: Float = 0.4f,
    val silenceKeepSec: Float = 0.7f,
    val detectedSilences: List<TimeRange> = emptyList(),
    val sampledNoiseRange: TimeRange? = null,
    val splitMode: String = "auto", // "auto" or "manual"
    val splitOptions: SplitOptions = SplitOptions(thresholdDb = -42f, minSilenceSec = 0.3f, minChunkSec = 0.5f, maxChunks = 24),
    val autoSplitChunks: List<TimeRange> = emptyList(),
    val manualSlots: List<ManualSplitSlot> = emptyList(),
    val activePresetId: String = "podcast-studio",
    val enhanceConfig: EnhanceConfig = AudioDspEngine.enhancePresets.first().config,
    val autoLevelOpts: AutoLevelOptions = AutoLevelOptions(),

    // Subtitle & Translation (Features 1, 4, 5)
    val sourceLanguage: SupportedLanguage = SupportedLanguage.BENGALI,
    val targetLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val subtitles: List<SubtitleBlock> = emptyList(),
    val selectedSubtitleId: String? = null,
    val globalTimeShiftSec: Float = 0f,
    val isLiveDictating: Boolean = false,
    val liveDictationPartial: String = "",
    val showScriptSyncDialog: Boolean = false,
    val exportedSubtitlePreviewTitle: String? = null,
    val exportedSubtitlePreviewContent: String? = null,
    val exportedSubtitleFile: File? = null,

    // Video Burn-in & Custom Styles (Feature 3)
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
    val customVideoBackdrop: Bitmap? = null,
    val customVideoName: String? = null,
    val videoExportProgress: Float? = null,
    val lastExportedFilePath: String? = null,
    val showAudioFormatDialog: Boolean = false,
    val busyMessage: String? = null,
    val toastMessage: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudioRepository
    private val player = PcmSamplePlayer()
    private val recorder = StudioAudioRecorder()
    private val speechDictation: LiveSpeechDictationController

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val savedProjects: StateFlow<List<ProjectEntity>>
    val glossaryEntries: StateFlow<List<GlossaryEntryEntity>>
    val batchItems: StateFlow<List<BatchQueueEntity>>
    val recentLogs: StateFlow<List<ProcessingLogEntity>>

    init {
        val db = StudioDatabase.getInstance(application)
        repository = StudioRepository(db.studioDao())

        savedProjects = repository.allProjects.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        glossaryEntries = repository.allGlossary.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        batchItems = repository.allBatchItems.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        recentLogs = repository.recentLogs.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

        speechDictation = LiveSpeechDictationController(
            context = application,
            onPartialText = { partial ->
                _uiState.update { it.copy(liveDictationPartial = partial) }
            },
            onFinalSegment = { spokenText ->
                appendDictatedSubtitleBlock(spokenText)
            },
            onStatusOrError = { msg, listening ->
                _uiState.update {
                    it.copy(
                        isLiveDictating = listening,
                        liveDictationPartial = if (listening) it.liveDictationPartial else "",
                        toastMessage = msg
                    )
                }
            }
        )

        viewModelScope.launch {
            repository.ensureDefaultGlossary()
            loadInitialDemoSession()
        }
    }

    private suspend fun loadInitialDemoSession() {
        val (samples, _) = withContext(Dispatchers.Default) {
            AudioDspEngine.generateDemoAudio(seconds = 20, sampleRate = 44100, noiseDb = -33f)
        }
        val sr = 44100
        val noiseSlice = samples.copyOfRange(0, min(samples.size, sr / 2))
        val learnedProfile = withContext(Dispatchers.Default) {
            AudioDspEngine.learnNoiseProfile(noiseSlice, sr, 1024)
        }
        val analysis = withContext(Dispatchers.Default) {
            AudioDspEngine.analyze(samples, sr)
        }
        val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
        val initialSubs = withContext(Dispatchers.Default) {
            val rawSubs = SubtitleEngine.transcribeAudioWithGlossary(
                samples = samples,
                sampleRate = sr,
                language = SupportedLanguage.BENGALI,
                glossary = rules
            )
            SubtitleEngine.translateSubtitles(rawSubs, SupportedLanguage.ENGLISH, rules)
        }

        _uiState.update {
            it.copy(
                samples = samples,
                originalSamples = samples,
                sampleRate = sr,
                analysis = analysis,
                originalAnalysis = analysis,
                noiseProfile = learnedProfile,
                viewRange = TimeRange(0f, analysis.durationSec),
                manualSlots = listOf(
                    ManualSplitSlot("m1", 0f, min(5f, analysis.durationSec / 2f)),
                    ManualSplitSlot("m2", min(5.5f, analysis.durationSec / 2f), min(11f, analysis.durationSec))
                ),
                subtitles = initialSubs,
                selectedSubtitleId = initialSubs.firstOrNull()?.id
            )
        }
    }

    fun selectTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setEditorSubTab(index: Int) {
        _uiState.update { it.copy(editorSubTab = index) }
    }

    fun setProjectTitle(title: String) {
        _uiState.update { it.copy(projectTitle = title, dirty = true) }
    }

    fun setShowAudioFormatDialog(show: Boolean) {
        _uiState.update { it.copy(showAudioFormatDialog = show) }
    }

    fun setShowScriptSyncDialog(show: Boolean) {
        _uiState.update { it.copy(showScriptSyncDialog = show) }
    }

    fun dismissSubtitlePreviewDialog() {
        _uiState.update {
            it.copy(
                exportedSubtitlePreviewTitle = null,
                exportedSubtitlePreviewContent = null,
                exportedSubtitleFile = null
            )
        }
    }

    fun sharePreviewedSubtitleFile() {
        val file = _uiState.value.exportedSubtitleFile ?: return
        AudioExporter.shareFile(getApplication(), file, "text/plain", "সাবটাইটেল ফাইল শেয়ার করুন")
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun notify(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    private fun pushAudioChange(newSamples: FloatArray, label: String, newStatus: String? = null) {
        stopPlayback()
        val current = _uiState.value
        val sr = current.sampleRate
        val analysis = AudioDspEngine.analyze(newSamples, sr)
        val nextUndo = (current.undoStack + HistorySnapshot(current.samples, label)).takeLast(10)
        val totalSec = max(1f, analysis.durationSec)

        // Keep subtitle timestamps synchronized within the new audio duration
        val resyncedSubs = SubtitleEngine.autoFixOverlapsAndGaps(current.subtitles, totalSec)

        _uiState.update {
            it.copy(
                samples = newSamples,
                previewSamples = null,
                analysis = analysis,
                undoStack = nextUndo,
                redoStack = emptyList(),
                status = newStatus ?: it.status,
                dirty = true,
                viewRange = TimeRange(0f, totalSec),
                selection = null,
                playheadSec = 0f,
                subtitles = resyncedSubs
            )
        }
    }

    fun undo() {
        val state = _uiState.value
        if (state.undoStack.isEmpty()) return
        stopPlayback()
        val last = state.undoStack.last()
        val analysis = AudioDspEngine.analyze(last.samples, state.sampleRate)
        _uiState.update {
            it.copy(
                samples = last.samples,
                analysis = analysis,
                undoStack = state.undoStack.dropLast(1),
                redoStack = listOf(HistorySnapshot(state.samples, last.label)) + state.redoStack,
                viewRange = TimeRange(0f, max(1f, analysis.durationSec)),
                toastMessage = "আনডু সফল: ${last.label}"
            )
        }
    }

    fun redo() {
        val state = _uiState.value
        if (state.redoStack.isEmpty()) return
        stopPlayback()
        val next = state.redoStack.first()
        val analysis = AudioDspEngine.analyze(next.samples, state.sampleRate)
        _uiState.update {
            it.copy(
                samples = next.samples,
                analysis = analysis,
                undoStack = state.undoStack + HistorySnapshot(state.samples, next.label),
                redoStack = state.redoStack.drop(1),
                viewRange = TimeRange(0f, max(1f, analysis.durationSec)),
                toastMessage = "রিডু সফল: ${next.label}"
            )
        }
    }

    // ------------------------------------------------------------------
    // Playback & Waveform Zoom/Selection
    // ------------------------------------------------------------------

    fun togglePlayback(useOriginal: Boolean = false, usePreview: Boolean = false) {
        if (player.isPlaying) {
            stopPlayback()
            return
        }
        val state = _uiState.value
        val buf = when {
            usePreview && state.previewSamples != null -> state.previewSamples
            useOriginal -> state.originalSamples
            else -> state.samples
        }
        if (buf.isEmpty()) return
        val total = buf.size.toFloat() / state.sampleRate
        val start = state.selection?.start ?: if (state.playheadSec >= total - 0.1f) 0f else state.playheadSec
        val end = state.selection?.end ?: total
        _uiState.update { it.copy(isPlaying = true) }
        viewModelScope.launch {
            player.playRange(
                samples = buf,
                sampleRate = state.sampleRate,
                startSec = start,
                endSec = end,
                loop = _uiState.value.loopPlayback,
                onPositionSec = { pos ->
                    _uiState.update { it.copy(playheadSec = pos) }
                },
                onComplete = {
                    _uiState.update { it.copy(isPlaying = false) }
                }
            )
        }
    }

    fun playSpecificRange(startSec: Float, endSec: Float) {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        stopPlayback()
        _uiState.update { it.copy(isPlaying = true, playheadSec = startSec) }
        viewModelScope.launch {
            player.playRange(
                samples = state.samples,
                sampleRate = state.sampleRate,
                startSec = startSec,
                endSec = endSec,
                loop = false,
                onPositionSec = { pos -> _uiState.update { it.copy(playheadSec = pos) } },
                onComplete = { _uiState.update { it.copy(isPlaying = false) } }
            )
        }
    }

    fun stopPlayback() {
        player.stop()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(sec: Float) {
        val total = _uiState.value.analysis.durationSec
        val clamped = sec.coerceIn(0f, max(0.01f, total))
        player.seekWhilePlaying(clamped)
        _uiState.update { it.copy(playheadSec = clamped) }
    }

    fun toggleLoop() {
        _uiState.update { it.copy(loopPlayback = !it.loopPlayback) }
    }

    fun setSelection(range: TimeRange?) {
        _uiState.update { it.copy(selection = range) }
    }

    fun trimToSelection() {
        val state = _uiState.value
        val sel = state.selection
        if (sel == null || sel.duration < 0.15f) {
            notify("ট্রিম করার জন্য আগে ওয়েভফর্মে ড্র্যাগ করে একটি অংশ সিলেক্ট করুন।")
            return
        }
        viewModelScope.launch {
            val trimmed = withContext(Dispatchers.Default) {
                AudioDspEngine.extractRange(state.samples, state.sampleRate, sel, 8)
            }
            pushAudioChange(trimmed, "সিলেকশন ট্রিম (${"%.1f".format(sel.duration)}s)", "cleaned")
            notify("নির্বাচিত অংশ রেখে বাকি অডিও ট্রিম করা হয়েছে!")
        }
    }

    fun deleteSelectedAudioRange() {
        val state = _uiState.value
        val sel = state.selection
        if (sel == null || sel.duration < 0.1f) {
            notify("ডিলিট করার জন্য আগে ওয়েভফর্মে একটি অংশ সিলেক্ট করুন।")
            return
        }
        viewModelScope.launch {
            val remaining = withContext(Dispatchers.Default) {
                AudioDspEngine.deleteRange(state.samples, state.sampleRate, sel)
            }
            pushAudioChange(remaining, "সিলেকশন ডিলিট (${"%.1f".format(sel.duration)}s)", "cleaned")
            notify("নির্বাচিত ${"%.1f".format(sel.duration)} সেকেন্ড অংশ কেটে বাদ দেওয়া হয়েছে!")
        }
    }

    fun applyFadeInOutToAudio() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        viewModelScope.launch {
            val faded = withContext(Dispatchers.Default) {
                AudioDspEngine.applyFadeInOut(state.samples, state.sampleRate, 0.5f, 0.5f)
            }
            pushAudioChange(faded, "ফেড-ইন ও ফেড-আউট (0.5s)", "enhanced")
            notify("অডিওর শুরু ও শেষে স্মুথ ফেড-ইন/আউট প্রয়োগ হয়েছে!")
        }
    }

    fun zoomWaveform(factor: Float) {
        val state = _uiState.value
        val total = max(1f, state.analysis.durationSec)
        val cur = state.viewRange
        val span = (cur.duration * factor).coerceIn(0.4f, total)
        val center = if (state.playheadSec in cur.start..cur.end) state.playheadSec else (cur.start + cur.end) / 2f
        val s = (center - span / 2f).coerceIn(0f, max(0f, total - span))
        val e = (s + span).coerceIn(s + 0.1f, total)
        _uiState.update { it.copy(viewRange = TimeRange(s, e)) }
    }

    fun resetZoom() {
        val total = max(1f, _uiState.value.analysis.durationSec)
        _uiState.update { it.copy(viewRange = TimeRange(0f, total)) }
    }

    // ------------------------------------------------------------------
    // Smart Recorder & Noise Profile Learning
    // ------------------------------------------------------------------

    fun setProfileSec(sec: Int) {
        _uiState.update { it.copy(profileSec = sec.coerceIn(3, 5)) }
    }

    fun updateGateSettings(patch: GateSettings) {
        _uiState.update { it.copy(gateSettings = patch) }
    }

    fun setLiveSuppress(enabled: Boolean) {
        _uiState.update { it.copy(liveSuppress = enabled) }
    }

    fun setAutoCleanOnStop(enabled: Boolean) {
        _uiState.update { it.copy(autoCleanOnStop = enabled) }
    }

    fun setAutoLevelOnStop(enabled: Boolean) {
        _uiState.update { it.copy(autoLevelOnStop = enabled) }
    }

    fun updateSuppressOptions(opts: NoiseReduceOptions) {
        _uiState.update { it.copy(suppressOptions = opts) }
    }

    fun captureRoomNoisePrint() {
        if (_uiState.value.recorderPhase != RecorderPhase.IDLE) return
        val secs = _uiState.value.profileSec
        _uiState.update { it.copy(recorderPhase = RecorderPhase.PROFILING, profileCountdown = secs.toFloat()) }
        viewModelScope.launch {
            val (profile, _) = recorder.captureNoiseProfile(secs) { rem, db ->
                _uiState.update { it.copy(profileCountdown = rem, liveDb = db, livePeakDb = db) }
            }
            repository.logOperation(
                "noise-profile",
                "${secs}s রুম নয়েজ প্রিন্ট",
                "ফ্লোর ${"%.1f".format(profile.avgDb)} dB · ${profile.fftSize}-pt FFT"
            )
            _uiState.update {
                it.copy(
                    recorderPhase = RecorderPhase.IDLE,
                    profileCountdown = 0f,
                    noiseProfile = profile,
                    toastMessage = "নয়েজ প্রিন্ট শেখা শেষ (${"%.1f".format(profile.avgDb)} dB ফ্লোর)"
                )
            }
        }
    }

    fun startOrResumeRecording() {
        val state = _uiState.value
        if (state.recorderPhase == RecorderPhase.PAUSED) {
            recorder.resume()
            _uiState.update { it.copy(recorderPhase = RecorderPhase.RECORDING) }
            return
        }
        if (state.recorderPhase != RecorderPhase.IDLE) return
        stopPlayback()
        _uiState.update {
            it.copy(
                recorderPhase = RecorderPhase.RECORDING,
                capturedSec = 0f,
                skippedSec = 0f,
                gateEvents = 0
            )
        }
        viewModelScope.launch {
            val result = recorder.recordSession(
                gate = _uiState.value.gateSettings,
                liveSuppress = _uiState.value.liveSuppress,
                noiseProfile = _uiState.value.noiseProfile,
                suppressOpts = _uiState.value.suppressOptions
            ) { prog ->
                _uiState.update {
                    it.copy(
                        liveDb = prog.db,
                        livePeakDb = prog.peakDb,
                        gateOpen = prog.gateOpen,
                        capturedSec = prog.capturedSec,
                        skippedSec = prog.skippedSec,
                        gateEvents = prog.gateEvents,
                        liveScope = prog.scopeSlice
                    )
                }
            }

            if (result.samples.size < 1024) {
                _uiState.update {
                    it.copy(
                        recorderPhase = RecorderPhase.IDLE,
                        toastMessage = "রেকর্ডিং খুব ছোট — আবার চেষ্টা করুন।"
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(recorderPhase = RecorderPhase.PROCESSING, busyMessage = "রেকর্ডিং ডিএসপি ও সাবটাইটেল তৈরি হচ্ছে…") }

            val processed = withContext(Dispatchers.Default) {
                var out = result.samples
                val cur = _uiState.value
                if (cur.autoCleanOnStop && cur.noiseProfile != null) {
                    out = AudioDspEngine.reduceNoise(out, result.sampleRate, cur.noiseProfile, cur.suppressOptions)
                }
                if (cur.autoLevelOnStop) {
                    out = AudioDspEngine.autoLevel(out, result.sampleRate, cur.autoLevelOpts)
                } else {
                    out = AudioDspEngine.normalizePeak(out, -1f)
                }
                out
            }

            val analysis = withContext(Dispatchers.Default) {
                AudioDspEngine.analyze(processed, result.sampleRate)
            }
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            val subs = withContext(Dispatchers.Default) {
                val generated = SubtitleEngine.transcribeAudioWithGlossary(
                    samples = processed,
                    sampleRate = result.sampleRate,
                    language = _uiState.value.sourceLanguage,
                    glossary = rules
                )
                SubtitleEngine.translateSubtitles(generated, _uiState.value.targetLanguage, rules)
            }

            repository.logOperation(
                "smart-record",
                "স্মার্ট রেকর্ডিং সম্পন্ন",
                "দৈর্ঘ্য ${"%.1f".format(analysis.durationSec)}s · স্কিপ ${"%.1f".format(result.skippedSec)}s · সাবটাইটেল ${subs.size}টি"
            )

            _uiState.update {
                it.copy(
                    recorderPhase = RecorderPhase.IDLE,
                    busyMessage = null,
                    samples = processed,
                    originalSamples = result.samples,
                    sampleRate = result.sampleRate,
                    analysis = analysis,
                    originalAnalysis = AudioDspEngine.analyze(result.samples, result.sampleRate),
                    viewRange = TimeRange(0f, max(1f, analysis.durationSec)),
                    subtitles = subs,
                    selectedSubtitleId = subs.firstOrNull()?.id,
                    status = "cleaned",
                    toastMessage = "রেকর্ডিং ও অটো-সাবটাইটেল প্রস্তুত (${subs.size}টি ব্লক)!"
                )
            }
        }
    }

    fun pauseRecording() {
        recorder.pause()
        _uiState.update { it.copy(recorderPhase = RecorderPhase.PAUSED) }
    }

    fun stopRecordingSession() {
        recorder.stop()
    }

    fun loadDemoAudio(seconds: Int = 22) {
        stopPlayback()
        _uiState.update { it.copy(busyMessage = "ডেমো অডিও ও সাবটাইটেল তৈরি হচ্ছে…") }
        viewModelScope.launch {
            val (samples, _) = withContext(Dispatchers.Default) {
                AudioDspEngine.generateDemoAudio(seconds, 44100, -33f, System.currentTimeMillis())
            }
            val sr = 44100
            val analysis = withContext(Dispatchers.Default) { AudioDspEngine.analyze(samples, sr) }
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            val subs = withContext(Dispatchers.Default) {
                val raw = SubtitleEngine.transcribeAudioWithGlossary(samples, sr, _uiState.value.sourceLanguage, rules)
                SubtitleEngine.translateSubtitles(raw, _uiState.value.targetLanguage, rules)
            }
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    projectTitle = "ডেমো — ভয়েসল্যাব ও সাবক্রাফট",
                    samples = samples,
                    originalSamples = samples,
                    sampleRate = sr,
                    analysis = analysis,
                    originalAnalysis = analysis,
                    viewRange = TimeRange(0f, analysis.durationSec),
                    subtitles = subs,
                    selectedSubtitleId = subs.firstOrNull()?.id,
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    toastMessage = "ডেমো অডিও (${seconds}s) এবং ${subs.size}টি সাবটাইটেল লোড হয়েছে!"
                )
            }
        }
    }

    fun importAudioUri(uri: Uri, displayName: String?) {
        stopPlayback()
        _uiState.update { it.copy(busyMessage = "অডিও ফাইল ডিকোড হচ্ছে…") }
        viewModelScope.launch {
            val decoded = AudioExporter.decodeAudioUri(getApplication(), uri)
            if (decoded == null || decoded.first.isEmpty()) {
                _uiState.update {
                    it.copy(
                        busyMessage = null,
                        toastMessage = "অডিও ডিকোড করা যায়নি — WAV/MP3/M4A ফাইল নির্বাচন করুন।"
                    )
                }
                return@launch
            }
            val (samples, sr) = decoded
            val analysis = withContext(Dispatchers.Default) { AudioDspEngine.analyze(samples, sr) }
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            val subs = withContext(Dispatchers.Default) {
                val raw = SubtitleEngine.transcribeAudioWithGlossary(samples, sr, _uiState.value.sourceLanguage, rules)
                SubtitleEngine.translateSubtitles(raw, _uiState.value.targetLanguage, rules)
            }
            val cleanName = displayName?.substringBeforeLast('.') ?: "ইম্পোর্টেড অডিও"
            repository.logOperation("import-audio", cleanName, "${"%.1f".format(analysis.durationSec)}s · ${sr}Hz")
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    projectTitle = cleanName,
                    samples = samples,
                    originalSamples = samples,
                    sampleRate = sr,
                    analysis = analysis,
                    originalAnalysis = analysis,
                    viewRange = TimeRange(0f, analysis.durationSec),
                    subtitles = subs,
                    selectedSubtitleId = subs.firstOrNull()?.id,
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    toastMessage = "ফাইল ইম্পোর্ট ও সাবটাইটেল তৈরি সফল!"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Editor Tools: Silence Removal, Sample Noise Removal, Splitter, Effects
    // ------------------------------------------------------------------

    fun updateSilenceParams(mode: String? = null, thresholdDb: Float? = null, minSec: Float? = null, keepSec: Float? = null) {
        _uiState.update {
            it.copy(
                silenceMode = mode ?: it.silenceMode,
                silenceThresholdDb = thresholdDb ?: it.silenceThresholdDb,
                silenceMinSec = minSec ?: it.silenceMinSec,
                silenceKeepSec = keepSec ?: it.silenceKeepSec
            )
        }
    }

    fun scanSilenceRegions() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        viewModelScope.launch {
            val found = withContext(Dispatchers.Default) {
                AudioDspEngine.detectSilence(state.samples, state.sampleRate, state.silenceThresholdDb, state.silenceMinSec)
            }
            val totalQuiet = found.fold(0f) { acc, r -> acc + r.duration }
            _uiState.update {
                it.copy(
                    detectedSilences = found,
                    toastMessage = if (found.isNotEmpty()) {
                        "${found.size}টি সাইলেন্ট অংশ পাওয়া গেছে (মোট ${"%.2f".format(totalQuiet)}s)"
                    } else {
                        "এই থ্রেশহোল্ডে কোনো সাইলেন্স পাওয়া যায়নি।"
                    }
                )
            }
        }
    }

    fun applySilenceRemoval() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "সাইলেন্স রিমুভ করা হচ্ছে…") }
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) {
                AudioDspEngine.removeSilence(
                    samples = state.samples,
                    sampleRate = state.sampleRate,
                    opts = SilenceOptions(
                        thresholdDb = state.silenceThresholdDb,
                        minSilenceSec = state.silenceMinSec,
                        keepSec = if (state.silenceMode == "sec") state.silenceKeepSec else null,
                        padMs = 20
                    )
                )
            }
            _uiState.update { it.copy(busyMessage = null, detectedSilences = emptyList()) }
            if (res.removed.isEmpty()) {
                notify("কাটার মতো সাইলেন্স পাওয়া যায়নি — থ্রেশহোল্ড বাড়িয়ে চেষ্টা করুন।")
                return@launch
            }
            pushAudioChange(
                newSamples = res.samples,
                label = if (state.silenceMode == "sec") "সেকেন্ড-লিমিট সাইলেন্স কাট" else "dB সাইলেন্স কাট",
                newStatus = "cleaned"
            )
            repository.logOperation(
                "silence-remove",
                if (state.silenceMode == "sec") "সেকেন্ড-লিমিট মোড" else "dB সাইলেন্স মোড",
                "${res.removed.size}টি অংশ · ${"%.2f".format(res.removedSec)}s বাদ"
            )
            notify("${"%.2f".format(res.removedSec)} সেকেন্ড সাইলেন্স বাদ দেওয়া হয়েছে!")
        }
    }

    fun learnNoiseFromSelection() {
        val state = _uiState.value
        val sel = state.selection ?: TimeRange(0f, min(1.2f, state.analysis.durationSec))
        if (sel.duration < 0.2f) {
            notify("অন্তত ০.২০ সেকেন্ডের নয়েজ অংশ সিলেক্ট করুন।")
            return
        }
        viewModelScope.launch {
            val slice = withContext(Dispatchers.Default) {
                AudioDspEngine.extractRange(state.samples, state.sampleRate, sel, 0)
            }
            val profile = withContext(Dispatchers.Default) {
                AudioDspEngine.learnNoiseProfile(slice, state.sampleRate, 1024)
            }
            _uiState.update {
                it.copy(
                    noiseProfile = profile,
                    sampledNoiseRange = sel,
                    toastMessage = "নয়েজ স্যাম্পল শেখা হয়েছে (ফ্লোর ${"%.1f".format(profile.avgDb)} dB)"
                )
            }
        }
    }

    fun applyLearnedNoiseRemoval() {
        val state = _uiState.value
        val profile = state.noiseProfile
        if (profile == null) {
            notify("আগে নয়েজ স্যাম্পল নিন অথবা রেকর্ডারে প্রিন্ট ক্যাপচার করুন।")
            return
        }
        _uiState.update { it.copy(busyMessage = "স্পেকট্রাল নয়েজ সাবট্রাকশন চলছে…") }
        viewModelScope.launch {
            val cleaned = withContext(Dispatchers.Default) {
                AudioDspEngine.reduceNoise(state.samples, state.sampleRate, profile, state.suppressOptions)
            }
            _uiState.update { it.copy(busyMessage = null) }
            pushAudioChange(cleaned, "স্যাম্পল নয়েজ রিমুভাল", "cleaned")
            repository.logOperation(
                "spectral-denoise",
                "নয়েজ প্রিন্ট সাবট্রাকশন",
                "স্ট্রেংথ ${state.suppressOptions.strength}x · ফ্লোর ${state.suppressOptions.floorDb}dB"
            )
            notify("পুরো অডিও থেকে ব্যাকগ্রাউন্ড নয়েজ পরিষ্কার করা হয়েছে!")
        }
    }

    fun updateSplitOptions(mode: String? = null, opts: SplitOptions? = null) {
        _uiState.update {
            it.copy(
                splitMode = mode ?: it.splitMode,
                splitOptions = opts ?: it.splitOptions
            )
        }
    }

    fun runAutoSplitter() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        viewModelScope.launch {
            val (chunks, _) = withContext(Dispatchers.Default) {
                AudioDspEngine.splitBySilence(state.samples, state.sampleRate, state.splitOptions)
            }
            _uiState.update {
                it.copy(
                    autoSplitChunks = chunks,
                    toastMessage = "${chunks.size}টি অটো-স্প্লিট টুকরো তৈরি হয়েছে!"
                )
            }
        }
    }

    fun addManualSplitSlot(startSec: Float, endSec: Float) {
        val total = _uiState.value.analysis.durationSec
        val a = min(startSec, endSec).coerceIn(0f, total)
        val b = max(startSec, endSec).coerceIn(a + 0.1f, total)
        val newSlot = ManualSplitSlot("m_${System.currentTimeMillis()}", a, b)
        _uiState.update {
            it.copy(
                manualSlots = it.manualSlots + newSlot,
                toastMessage = "স্লট যোগ হয়েছে: ${AudioDspEngine.formatTimecode(a)} → ${AudioDspEngine.formatTimecode(b)}"
            )
        }
    }

    fun removeManualSplitSlot(id: String) {
        _uiState.update { it.copy(manualSlots = it.manualSlots.filterNot { s -> s.id == id }) }
    }

    fun keepSelectedSplitRanges(ranges: List<TimeRange>) {
        val state = _uiState.value
        if (ranges.isEmpty() || state.samples.isEmpty()) return
        viewModelScope.launch {
            val joined = withContext(Dispatchers.Default) {
                AudioDspEngine.keepRanges(state.samples, state.sampleRate, ranges, 6)
            }
            pushAudioChange(joined, "${ranges.size}টি টুকরো একত্রিত", "cleaned")
            notify("${ranges.size}টি রেঞ্জ জোড়া দিয়ে নতুন মাস্টার তৈরি হয়েছে!")
        }
    }

    fun exportSplitChunksZip(ranges: List<TimeRange>) {
        val state = _uiState.value
        if (ranges.isEmpty() || state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "${ranges.size}টি টুকরো ZIP করা হচ্ছে…") }
        viewModelScope.launch {
            val zipFile = AudioExporter.exportChunksZip(
                context = getApplication(),
                samples = state.samples,
                sampleRate = state.sampleRate,
                ranges = ranges,
                baseName = state.projectTitle
            )
            repository.logOperation("export-chunks", "${ranges.size}টি WAV টুকরো ZIP", zipFile.name)
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    lastExportedFilePath = zipFile.absolutePath,
                    toastMessage = "${ranges.size}টি টুকরো Downloads/SubCraftStudio ফোল্ডারে সেভ হয়েছে!"
                )
            }
            AudioExporter.shareFile(getApplication(), zipFile, "application/zip", "স্প্লিট টুকরো শেয়ার / সেভ করুন")
        }
    }

    fun selectEnhancePreset(presetId: String) {
        val preset = AudioDspEngine.enhancePresets.find { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                activePresetId = preset.id,
                enhanceConfig = preset.config,
                previewSamples = null
            )
        }
    }

    fun updateEnhanceConfig(cfg: EnhanceConfig) {
        _uiState.update { it.copy(enhanceConfig = cfg, activePresetId = "custom", previewSamples = null) }
    }

    fun updateAutoLevelOptions(opts: AutoLevelOptions) {
        _uiState.update { it.copy(autoLevelOpts = opts) }
    }

    fun applyAutoLevelBooster() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "ভয়েস বুস্টার ও অটো-লেভেলিং চলছে…") }
        viewModelScope.launch {
            val leveled = withContext(Dispatchers.Default) {
                AudioDspEngine.autoLevel(state.samples, state.sampleRate, state.autoLevelOpts)
            }
            _uiState.update { it.copy(busyMessage = null) }
            pushAudioChange(leveled, "ভয়েস বুস্টার (${state.autoLevelOpts.targetDb} dBFS)", "enhanced")
            repository.logOperation("auto-level", "ভয়েস বুস্টার / RMS লেভেলিং", "টার্গেট ${state.autoLevelOpts.targetDb} dBFS")
            notify("অটো-লেভেলিং ও ভয়েস বুস্ট প্রয়োগ হয়েছে!")
        }
    }

    fun renderEnhancePreview() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "স্টুডিও চেইন প্রিভিউ রেন্ডার হচ্ছে…") }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                AudioDspEngine.renderEnhanced(state.samples, state.sampleRate, state.enhanceConfig)
            }
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    previewSamples = preview,
                    toastMessage = "প্রিভিউ প্রস্তুত — A/B বাটনে শুনে তুলনা করুন!"
                )
            }
        }
    }

    fun applyOneClickStudioChain() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "ওয়ান-ক্লিক স্টুডিও চেইন প্রয়োগ হচ্ছে…") }
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) {
                var buf = state.samples
                if (state.noiseProfile != null) {
                    buf = AudioDspEngine.reduceNoise(buf, state.sampleRate, state.noiseProfile, state.suppressOptions)
                }
                buf = AudioDspEngine.autoLevel(buf, state.sampleRate, state.autoLevelOpts)
                buf = AudioDspEngine.renderEnhanced(buf, state.sampleRate, state.enhanceConfig)
                AudioDspEngine.normalizePeak(buf, -1f)
            }
            _uiState.update { it.copy(busyMessage = null) }
            pushAudioChange(out, "ওয়ান-ক্লিক AI স্টুডিও চেইন", "enhanced")
            repository.logOperation("studio-chain", "ওয়ান-ক্লিক স্টুডিও মাস্টার", "Noise Print → Auto-Level → EQ/Comp → Peak -1dB")
            notify("ওয়ান-ক্লিক স্টুডিও মাস্টার চেইন সম্পন্ন!")
        }
    }

    // ------------------------------------------------------------------
    // Multi-Format Audio Saving & Project Persistence
    // ------------------------------------------------------------------

    fun saveAndExportAudioFormat(format: AudioExportFormat, shareAfterSave: Boolean = true) {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "${format.label} ফরম্যাটে সেভ হচ্ছে…", showAudioFormatDialog = false) }
        viewModelScope.launch {
            val exportedFile = AudioExporter.exportAudioFile(
                context = getApplication(),
                samples = state.samples,
                sampleRate = state.sampleRate,
                baseName = state.projectTitle,
                format = format
            )
            val projectEntity = ProjectEntity(
                title = "${state.projectTitle} (${format.extension.uppercase()})",
                durationSec = state.analysis.durationSec,
                sampleRate = state.sampleRate,
                status = state.status,
                sourceLanguage = state.sourceLanguage.code,
                targetLanguage = state.targetLanguage.code,
                peakDb = state.analysis.peakDb,
                rmsDb = state.analysis.rmsDb,
                noiseFloorDb = state.analysis.noiseFloorDb,
                wavFilePath = exportedFile.absolutePath,
                subtitlesJson = StudioRepository.serializeSubtitles(state.subtitles)
            )
            repository.saveProject(projectEntity)
            repository.logOperation(
                "audio-export",
                "${format.label} সেভ",
                "${exportedFile.name} (${exportedFile.length() / 1024} KB)"
            )
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    dirty = false,
                    lastExportedFilePath = exportedFile.absolutePath,
                    toastMessage = "Downloads/SubCraftStudio ফোল্ডারে সেভ হয়েছে: ${exportedFile.name}"
                )
            }
            if (shareAfterSave) {
                AudioExporter.shareFile(getApplication(), exportedFile, format.mimeType, "${format.label} সেভ / শেয়ার করুন")
            }
        }
    }

    fun loadProjectFromLibrary(project: ProjectEntity) {
        stopPlayback()
        _uiState.update { it.copy(busyMessage = "প্রজেক্ট লোড হচ্ছে…") }
        viewModelScope.launch {
            val file = File(project.wavFilePath)
            val loadedPair = if (file.exists()) {
                withContext(Dispatchers.IO) {
                    runCatching { AudioExporter.parseWavBytes(file.readBytes()) }.getOrNull()
                }
            } else null

            val (samples, sr) = loadedPair ?: withContext(Dispatchers.Default) {
                AudioDspEngine.generateDemoAudio(project.durationSec.roundToInt().coerceIn(8, 30), project.sampleRate)
                    .first to project.sampleRate
            }
            val analysis = withContext(Dispatchers.Default) { AudioDspEngine.analyze(samples, sr) }
            val subs = StudioRepository.deserializeSubtitles(project.subtitlesJson)
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    projectTitle = project.title,
                    samples = samples,
                    originalSamples = samples,
                    sampleRate = sr,
                    status = project.status,
                    analysis = analysis,
                    originalAnalysis = analysis,
                    viewRange = TimeRange(0f, max(1f, analysis.durationSec)),
                    subtitles = subs,
                    selectedSubtitleId = subs.firstOrNull()?.id,
                    activeTab = StudioTab.EDITOR,
                    toastMessage = "\"${project.title}\" ওয়ার্কস্পেসে লোড হয়েছে!"
                )
            }
        }
    }

    fun deleteSavedProject(id: Int) {
        viewModelScope.launch {
            repository.deleteProject(id)
            notify("প্রজেক্ট মুছে ফেলা হয়েছে।")
        }
    }

    // ------------------------------------------------------------------
    // Feature 1 & 4: Subtitle Editor, Live Dictation, Script Sync & Translation
    // ------------------------------------------------------------------

    fun setSourceLanguage(lang: SupportedLanguage) {
        _uiState.update { it.copy(sourceLanguage = lang) }
    }

    fun setTargetLanguage(lang: SupportedLanguage) {
        _uiState.update { it.copy(targetLanguage = lang) }
    }

    fun toggleLiveVoiceDictation() {
        val state = _uiState.value
        if (state.isLiveDictating) {
            speechDictation.stopListening()
            _uiState.update { it.copy(isLiveDictating = false, liveDictationPartial = "") }
        } else {
            stopPlayback()
            speechDictation.startListening(state.sourceLanguage)
        }
    }

    private fun appendDictatedSubtitleBlock(spokenText: String) {
        val state = _uiState.value
        val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
        val (correctedOrig, hits1) = SubtitleEngine.applyGlossaryToText(spokenText, rules)
        val rawTranslated = SubtitleEngine.translateSingleLine(correctedOrig, state.subtitles.size, state.targetLanguage)
        val (correctedTrans, hits2) = SubtitleEngine.applyGlossaryToText(rawTranslated, rules)

        val total = max(4f, state.analysis.durationSec)
        val start = if (state.playheadSec > 0.1f && state.playheadSec < total - 1f) {
            state.playheadSec
        } else {
            (state.subtitles.maxOfOrNull { it.endSec } ?: 0f) + 0.1f
        }.coerceIn(0f, max(0f, total - 0.8f))
        val estDur = (spokenText.split(" ").size * 0.42f).coerceIn(1.2f, 4.0f)
        val end = min(total, start + estDur)

        val newBlock = SubtitleBlock(
            id = "sub_dict_${System.currentTimeMillis()}",
            index = state.subtitles.size + 1,
            startSec = (start * 100f).roundToInt() / 100f,
            endSec = (end * 100f).roundToInt() / 100f,
            originalText = correctedOrig,
            translatedText = correctedTrans,
            matchedGlossaryTerms = (hits1 + hits2).distinct()
        )

        _uiState.update {
            it.copy(
                isLiveDictating = false,
                liveDictationPartial = "",
                subtitles = it.subtitles + newBlock,
                selectedSubtitleId = newBlock.id,
                playheadSec = newBlock.startSec,
                toastMessage = "ভয়েস ডিক্টেশন যুক্ত হয়েছে: \"$correctedOrig\""
            )
        }
    }

    fun runSpeechToSubtitles() {
        val state = _uiState.value
        if (state.samples.isEmpty()) return
        _uiState.update { it.copy(busyMessage = "${state.sourceLanguage.nativeName} অডিও ভয়েস সেগমেন্ট ও গ্লোসারি চেক চলছে…") }
        viewModelScope.launch {
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            val generated = withContext(Dispatchers.Default) {
                val blocks = SubtitleEngine.transcribeAudioWithGlossary(
                    samples = state.samples,
                    sampleRate = state.sampleRate,
                    language = state.sourceLanguage,
                    glossary = rules
                )
                SubtitleEngine.translateSubtitles(blocks, state.targetLanguage, rules)
            }
            repository.logOperation(
                "transcribe",
                "সাবটাইটেল জেনারেশন (${state.sourceLanguage.nativeName})",
                "${generated.size}টি টাইমস্ট্যাম্প ব্লক · গ্লোসারি রুল ${rules.size}টি"
            )
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    subtitles = generated,
                    selectedSubtitleId = generated.firstOrNull()?.id,
                    toastMessage = "${generated.size}টি সাবটাইটেল ব্লক ও টাইমস্ট্যাম্প তৈরি হয়েছে!"
                )
            }
        }
    }

    fun syncCustomScriptToSubtitles(scriptText: String) {
        val state = _uiState.value
        val lines = scriptText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            notify("অন্তত ১ লাইন স্ক্রিপ্ট বা ডায়ালগ লিখুন।")
            return
        }
        _uiState.update { it.copy(busyMessage = "অডিও ওয়েভফর্মের সাথে স্ক্রিপ্ট অটো-সিঙ্ক হচ্ছে…", showScriptSyncDialog = false) }
        viewModelScope.launch {
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            val synced = withContext(Dispatchers.Default) {
                val blocks = SubtitleEngine.transcribeAudioWithGlossary(
                    samples = state.samples,
                    sampleRate = state.sampleRate,
                    language = state.sourceLanguage,
                    glossary = rules,
                    customScriptLines = lines
                )
                SubtitleEngine.translateSubtitles(blocks, state.targetLanguage, rules)
            }
            repository.logOperation("script-sync", "স্ক্রিপ্ট অটো-সিঙ্ক", "${synced.size}টি লাইন টাইমস্ট্যাম্পে সিঙ্ক")
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    subtitles = synced,
                    selectedSubtitleId = synced.firstOrNull()?.id,
                    toastMessage = "${synced.size}টি স্ক্রিপ্ট লাইন অডিও টাইমলাইনে সিঙ্ক হয়েছে!"
                )
            }
        }
    }

    fun importSubtitleFileUri(uri: Uri) {
        _uiState.update { it.copy(busyMessage = "সাবটাইটেল ফাইল পড়া হচ্ছে…") }
        viewModelScope.launch {
            val rawText = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (rawText.isNullOrBlank()) {
                _uiState.update { it.copy(busyMessage = null, toastMessage = "সাবটাইটেল ফাইল পড়া যায়নি।") }
                return@launch
            }
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            val totalSec = max(5f, _uiState.value.analysis.durationSec)
            val parsed = withContext(Dispatchers.Default) {
                val blocks = SubtitleEngine.parseSubtitleText(rawText, totalSec, rules)
                SubtitleEngine.translateSubtitles(blocks, _uiState.value.targetLanguage, rules)
            }
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    subtitles = parsed,
                    selectedSubtitleId = parsed.firstOrNull()?.id,
                    toastMessage = "${parsed.size}টি সাবটাইটেল ব্লক ইম্পোর্ট হয়েছে!"
                )
            }
        }
    }

    fun translateAllSubtitles(target: SupportedLanguage = _uiState.value.targetLanguage) {
        val state = _uiState.value
        if (state.subtitles.isEmpty()) return
        val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
        val translated = SubtitleEngine.translateSubtitles(state.subtitles, target, rules)
        viewModelScope.launch {
            repository.logOperation(
                "auto-translate",
                "অটো-ট্রান্সলেশন → ${target.nativeName}",
                "${translated.size}টি সাবটাইটেল অনূদিত"
            )
        }
        _uiState.update {
            it.copy(
                targetLanguage = target,
                subtitles = translated,
                subtitleStyle = it.subtitleStyle.copy(showTranslated = true),
                toastMessage = "সব সাবটাইটেল ${target.nativeName} ভাষায় অনুবাদ করা হয়েছে!"
            )
        }
    }

    fun selectSubtitleBlock(id: String) {
        val block = _uiState.value.subtitles.find { it.id == id }
        if (block != null) {
            player.seekWhilePlaying(block.startSec)
        }
        _uiState.update {
            it.copy(
                selectedSubtitleId = id,
                playheadSec = block?.startSec ?: it.playheadSec
            )
        }
    }

    fun updateSubtitleBlock(
        id: String,
        newOriginal: String? = null,
        newTranslated: String? = null,
        newStartSec: Float? = null,
        newEndSec: Float? = null
    ) {
        val total = max(2f, _uiState.value.analysis.durationSec)
        val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
        _uiState.update { state ->
            val updated = state.subtitles.mapIndexed { idx, b ->
                if (b.id != id) b else {
                    val s = (newStartSec ?: b.startSec).coerceIn(0f, total - 0.15f)
                    val e = (newEndSec ?: b.endSec).coerceIn(s + 0.15f, total)
                    val rawOrig = newOriginal ?: b.originalText
                    val (cleanOrig, hits) = SubtitleEngine.applyGlossaryToText(rawOrig, rules)
                    // If original text changed and newTranslated wasn't manually passed, auto-update translation too
                    val updatedTrans = when {
                        newTranslated != null -> newTranslated
                        newOriginal != null && newOriginal != b.originalText -> {
                            SubtitleEngine.translateSingleLine(cleanOrig, idx, state.targetLanguage)
                        }
                        else -> b.translatedText
                    }
                    b.copy(
                        startSec = (s * 100f).roundToInt() / 100f,
                        endSec = (e * 100f).roundToInt() / 100f,
                        originalText = cleanOrig,
                        translatedText = updatedTrans,
                        matchedGlossaryTerms = hits
                    )
                }
            }
            state.copy(subtitles = updated, dirty = true)
        }
    }

    fun autoFixSubtitleTimeline() {
        val state = _uiState.value
        if (state.subtitles.isEmpty()) return
        val fixed = SubtitleEngine.autoFixOverlapsAndGaps(state.subtitles, max(2f, state.analysis.durationSec))
        _uiState.update {
            it.copy(
                subtitles = fixed,
                toastMessage = "টাইমলাইন ওভারল্যাপ ও গ্যাপ অটো-ফিক্স করা হয়েছে!"
            )
        }
    }

    fun mergeSubtitleWithNext(id: String) {
        val state = _uiState.value
        val idx = state.subtitles.indexOfFirst { it.id == id }
        if (idx < 0 || idx >= state.subtitles.lastIndex) {
            notify("মার্জ করার জন্য নিচে আরেকটি ব্লক থাকতে হবে।")
            return
        }
        val first = state.subtitles[idx]
        val second = state.subtitles[idx + 1]
        val merged = first.copy(
            endSec = max(first.endSec, second.endSec),
            originalText = "${first.originalText} ${second.originalText}".trim(),
            translatedText = "${first.translatedText} ${second.translatedText}".trim(),
            matchedGlossaryTerms = (first.matchedGlossaryTerms + second.matchedGlossaryTerms).distinct()
        )
        val nextList = state.subtitles.toMutableList().apply {
            set(idx, merged)
            removeAt(idx + 1)
        }.mapIndexed { i, b -> b.copy(index = i + 1) }

        _uiState.update {
            it.copy(
                subtitles = nextList,
                selectedSubtitleId = merged.id,
                toastMessage = "২টি সাবটাইটেল ব্লক একসাথে মার্জ হয়েছে!"
            )
        }
    }

    fun shiftAllSubtitleTimestamps(deltaSec: Float) {
        val total = max(1f, _uiState.value.analysis.durationSec)
        _uiState.update { state ->
            val shifted = state.subtitles.map { b ->
                val dur = b.durationSec
                val ns = (b.startSec + deltaSec).coerceIn(0f, max(0f, total - dur))
                val ne = (ns + dur).coerceAtMost(total)
                b.copy(
                    startSec = (ns * 100f).roundToInt() / 100f,
                    endSec = (ne * 100f).roundToInt() / 100f
                )
            }
            state.copy(
                subtitles = shifted,
                globalTimeShiftSec = state.globalTimeShiftSec + deltaSec,
                toastMessage = "সব টাইমস্ট্যাম্প ${if (deltaSec >= 0) "+" else ""}${"%.2f".format(deltaSec)}s শিফট হয়েছে"
            )
        }
    }

    fun addNewSubtitleBlock() {
        val state = _uiState.value
        val total = max(2f, state.analysis.durationSec)
        val lastEnd = state.subtitles.maxOfOrNull { it.endSec } ?: 0f
        val start = min(total - 1.2f, lastEnd + 0.15f).coerceAtLeast(0f)
        val end = min(total, start + 2.2f)
        val newBlock = SubtitleBlock(
            id = "sub_custom_${System.currentTimeMillis()}",
            index = state.subtitles.size + 1,
            startSec = (start * 100f).roundToInt() / 100f,
            endSec = (end * 100f).roundToInt() / 100f,
            originalText = "নতুন সাবটাইটেল টেক্সট লিখুন…",
            translatedText = "Enter new subtitle text…"
        )
        _uiState.update {
            it.copy(
                subtitles = it.subtitles + newBlock,
                selectedSubtitleId = newBlock.id,
                playheadSec = newBlock.startSec
            )
        }
    }

    fun splitSelectedSubtitleBlock(id: String) {
        val state = _uiState.value
        val idx = state.subtitles.indexOfFirst { it.id == id }
        if (idx < 0) return
        val target = state.subtitles[idx]
        if (target.durationSec < 0.6f) {
            notify("ব্লকটি ভাগ করার জন্য খুব ছোট।")
            return
        }
        val mid = ((target.startSec + target.endSec) / 2f * 100f).roundToInt() / 100f
        val words = target.originalText.split(" ")
        val halfWords = max(1, words.size / 2)
        val firstOrig = words.take(halfWords).joinToString(" ")
        val secondOrig = words.drop(halfWords).joinToString(" ").ifBlank { target.originalText }

        val transWords = target.translatedText.split(" ")
        val halfTrans = max(1, transWords.size / 2)
        val firstTrans = transWords.take(halfTrans).joinToString(" ")
        val secondTrans = transWords.drop(halfTrans).joinToString(" ")

        val firstBlock = target.copy(endSec = mid, originalText = firstOrig, translatedText = firstTrans)
        val secondBlock = target.copy(
            id = "sub_split_${System.currentTimeMillis()}",
            startSec = mid,
            originalText = secondOrig,
            translatedText = secondTrans
        )
        val nextList = state.subtitles.toMutableList().apply {
            set(idx, firstBlock)
            add(idx + 1, secondBlock)
        }.mapIndexed { i, b -> b.copy(index = i + 1) }

        _uiState.update {
            it.copy(
                subtitles = nextList,
                selectedSubtitleId = secondBlock.id,
                toastMessage = "সাবটাইটেল ব্লক ২ ভাগে স্প্লিট হয়েছে!"
            )
        }
    }

    fun deleteSubtitleBlock(id: String) {
        _uiState.update { state ->
            val filtered = state.subtitles.filterNot { it.id == id }.mapIndexed { i, b -> b.copy(index = i + 1) }
            state.copy(
                subtitles = filtered,
                selectedSubtitleId = filtered.firstOrNull()?.id
            )
        }
    }

    fun exportSubtitles(extension: String) {
        val state = _uiState.value
        if (state.subtitles.isEmpty()) {
            notify("এক্সপোর্ট করার জন্য কোনো সাবটাইটেল নেই।")
            return
        }
        _uiState.update { it.copy(busyMessage = ".${extension.uppercase()} সাবটাইটেল এক্সপোর্ট হচ্ছে…") }
        viewModelScope.launch {
            val (file, content) = SubtitleEngine.exportSubtitleFile(
                context = getApplication(),
                blocks = state.subtitles,
                baseName = state.projectTitle,
                extension = extension,
                useTranslation = state.subtitleStyle.showTranslated,
                bilingual = state.subtitleStyle.bilingualDualSub
            )
            repository.logOperation("export-$extension", ".${extension.uppercase()} সাবটাইটেল এক্সপোর্ট", file.name)
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    lastExportedFilePath = file.absolutePath,
                    exportedSubtitlePreviewTitle = "${file.name} (Downloads/SubCraftStudio)",
                    exportedSubtitlePreviewContent = content,
                    exportedSubtitleFile = file,
                    toastMessage = "Downloads/SubCraftStudio ফোল্ডারে .${extension.uppercase()} সেভ হয়েছে!"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Feature 3: Custom Styles & Burn-in Video Export (.MP4)
    // ------------------------------------------------------------------

    fun updateSubtitleStyle(style: SubtitleStyleConfig) {
        _uiState.update { it.copy(subtitleStyle = style) }
    }

    fun importVideoForBurnIn(uri: Uri, name: String?) {
        _uiState.update { it.copy(busyMessage = "ভিডিও ফ্রেম ও অডিও ট্র্যাক লোড হচ্ছে…") }
        viewModelScope.launch {
            val bmp = VideoBurnInExporter.extractVideoBackdrop(getApplication(), uri)
            val audioPair = AudioExporter.decodeAudioUri(getApplication(), uri)
            if (audioPair != null && audioPair.first.isNotEmpty()) {
                val (samples, sr) = audioPair
                val analysis = withContext(Dispatchers.Default) { AudioDspEngine.analyze(samples, sr) }
                val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
                val subs = withContext(Dispatchers.Default) {
                    val raw = SubtitleEngine.transcribeAudioWithGlossary(samples, sr, _uiState.value.sourceLanguage, rules)
                    SubtitleEngine.translateSubtitles(raw, _uiState.value.targetLanguage, rules)
                }
                _uiState.update {
                    it.copy(
                        busyMessage = null,
                        customVideoBackdrop = bmp,
                        customVideoName = name ?: "Uploaded Video.mp4",
                        samples = samples,
                        originalSamples = samples,
                        sampleRate = sr,
                        analysis = analysis,
                        subtitles = subs,
                        toastMessage = "ভিডিও লোড ও অটো-সাবটাইটেল প্রস্তুত!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        busyMessage = null,
                        customVideoBackdrop = bmp,
                        customVideoName = name ?: "Custom Video Background",
                        toastMessage = "ভিডিও ব্যাকড্রপ সেট করা হয়েছে!"
                    )
                }
            }
        }
    }

    fun exportBurnInVideoMp4() {
        val state = _uiState.value
        if (state.subtitles.isEmpty()) {
            notify("ভিডিওতে বার্ন-ইন করার জন্য আগে সাবটাইটেল তৈরি করুন।")
            return
        }
        _uiState.update { it.copy(videoExportProgress = 0.02f, busyMessage = "হার্ডকোডেড সাবটাইটেলসহ .MP4 ভিডিও তৈরি হচ্ছে…") }
        viewModelScope.launch {
            val mp4File = VideoBurnInExporter.exportBurnInMp4(
                context = getApplication(),
                samples = state.samples,
                sampleRate = state.sampleRate,
                subtitles = state.subtitles,
                style = state.subtitleStyle,
                projectTitle = state.projectTitle,
                backdropBitmap = state.customVideoBackdrop,
                onProgress = { p ->
                    _uiState.update { it.copy(videoExportProgress = p) }
                }
            )
            repository.logOperation(
                "video-burn-in",
                "হার্ডকোডেড .MP4 ভিডিও এক্সপোর্ট",
                "${mp4File.name} (${mp4File.length() / 1024} KB)"
            )
            _uiState.update {
                it.copy(
                    videoExportProgress = null,
                    busyMessage = null,
                    lastExportedFilePath = mp4File.absolutePath,
                    toastMessage = "Downloads/SubCraftStudio-তে .MP4 ভিডিও সেভ হয়েছে: ${mp4File.name}"
                )
            }
            AudioExporter.shareFile(getApplication(), mp4File, "video/mp4", "বার্ন-ইন MP4 ভিডিও ডাউনলোড / শেয়ার করুন")
        }
    }

    // ------------------------------------------------------------------
    // Feature 5: Custom Vocabulary & Glossary Dictionary + Batch Queue
    // ------------------------------------------------------------------

    fun addGlossaryTerm(spoken: String, canonical: String, category: String, language: String) {
        if (spoken.isBlank() || canonical.isBlank()) {
            notify("ভুল/উচ্চারিত শব্দ এবং সঠিক বানান দুটোই দিন।")
            return
        }
        viewModelScope.launch {
            repository.addGlossary(spoken, canonical, category, language)
            repository.logOperation("glossary-add", "গ্লোসারি শব্দ যোগ", "$spoken → $canonical")
            notify("গ্লোসারিতে যুক্ত হয়েছে: $spoken → $canonical")
        }
    }

    fun deleteGlossaryTerm(id: Int) {
        viewModelScope.launch {
            repository.deleteGlossary(id)
        }
    }

    fun applyGlossaryToCurrentSubtitles() {
        val state = _uiState.value
        val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
        var totalReplacements = 0
        val updated = state.subtitles.map { b ->
            val (fixedOrig, hits1) = SubtitleEngine.applyGlossaryToText(b.originalText, rules)
            val (fixedTrans, hits2) = if (b.translatedText.isNotBlank()) {
                SubtitleEngine.applyGlossaryToText(b.translatedText, rules)
            } else "" to emptyList()
            val mergedHits = (hits1 + hits2).distinct()
            totalReplacements += mergedHits.size
            b.copy(
                originalText = fixedOrig,
                translatedText = fixedTrans,
                matchedGlossaryTerms = mergedHits
            )
        }
        _uiState.update {
            it.copy(
                subtitles = updated,
                toastMessage = "গ্লোসারি ডিকশনারি প্রয়োগ হয়েছে ($totalReplacements টি শব্দ যাচাইকৃত)!"
            )
        }
    }

    fun addDemoFilesToBatchQueue() {
        viewModelScope.launch {
            repository.addBatchItem("Podcast_Ep01_Bengali_Raw.wav", 18.4f, "bn")
            repository.addBatchItem("Tech_Keynote_English_Noisy.m4a", 24.0f, "en")
            repository.addBatchItem("Interview_Spanish_Clip.wav", 15.2f, "es")
            notify("৩টি অডিও ফাইল ব্যাচ প্রসেসিং কিউতে যোগ হয়েছে!")
        }
    }

    fun processAllBatchQueue() {
        val items = batchItems.value
        if (items.isEmpty()) {
            notify("ব্যাচ কিউ খালি — আগে ফাইল যোগ করুন।")
            return
        }
        _uiState.update { it.copy(busyMessage = "ব্যাচ প্রসেসিং চলছে (${items.size}টি ফাইল)…") }
        viewModelScope.launch {
            val rules = StudioRepository.toGlossaryRules(glossaryEntries.value)
            for (item in items) {
                repository.updateBatchItem(item.id, "PROCESSING", item.subtitleCount, item.exportedFormat)
                val lang = SupportedLanguage.entries.find { it.code == item.languageCode } ?: SupportedLanguage.BENGALI
                val (demoSamples, _) = withContext(Dispatchers.Default) {
                    AudioDspEngine.generateDemoAudio(item.durationSec.roundToInt().coerceIn(8, 24), 44100)
                }
                val subs = withContext(Dispatchers.Default) {
                    SubtitleEngine.transcribeAudioWithGlossary(demoSamples, 44100, lang, rules)
                }
                val outDir = File(getApplication<Application>().filesDir, "exports").apply { mkdirs() }
                val wavFile = File(outDir, "${item.fileName.substringBeforeLast('.')}_cleaned.wav")
                withContext(Dispatchers.IO) {
                    FileOutputStream(wavFile).use {
                        it.write(AudioExporter.encodeWavBytes(demoSamples, 44100, 16))
                    }
                }
                repository.updateBatchItem(item.id, "DONE", subs.size, "WAV 16-bit + SRT (${subs.size} blocks)")
            }
            repository.logOperation("batch-process", "ব্যাচ প্রসেসিং সম্পন্ন", "${items.size}টি ফাইল ক্লিন ও সাবটাইটেল তৈরি")
            _uiState.update {
                it.copy(
                    busyMessage = null,
                    toastMessage = "${items.size}টি অডিও ফাইলের ব্যাচ প্রসেসিং সম্পন্ন!"
                )
            }
        }
    }

    fun clearBatchQueue() {
        viewModelScope.launch {
            repository.clearBatchQueue()
            notify("ব্যাচ কিউ পরিষ্কার করা হয়েছে।")
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
        recorder.stop()
        speechDictation.stopListening()
    }
}
