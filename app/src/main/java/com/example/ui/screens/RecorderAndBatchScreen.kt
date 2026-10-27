package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.AudioDspEngine
import com.example.data.BatchQueueEntity
import com.example.ui.components.LiveScopeAndMeter
import com.example.ui.components.SpectrumBarsCanvas
import com.example.ui.components.StatBadgeCard
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPanel
import com.example.ui.components.StudioSegmentedControl
import com.example.ui.components.StudioSlider
import com.example.ui.components.StudioToggleRow
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink800
import com.example.ui.theme.Ink900
import com.example.ui.theme.Ink950
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.RecRed
import com.example.ui.theme.Sand100
import com.example.ui.theme.Sand300
import com.example.ui.theme.Sand50
import com.example.ui.theme.Sand500
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TealAccent
import com.example.viewmodel.RecorderPhase
import com.example.viewmodel.StudioTab
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecorderAndBatchScreen(
    uiState: StudioUiState,
    batchItems: List<BatchQueueEntity>,
    viewModel: StudioViewModel
) {
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether granted or emulator fallback, start recording or profiling
        viewModel.startOrResumeRecording()
    }

    val profilePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.captureRoomNoisePrint()
    }

    val audioFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudioUri(uri, uri.lastPathSegment)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Action Banner
        StudioPanel(
            title = "স্মার্ট রেকর্ডার ও ব্যাচ স্টুডিও",
            step = "01",
            subtitle = "অডাসিটি-স্টাইল নয়েজ প্রোফাইল লার্নিং, সাইলেন্স স্কিপ রেকর্ডিং ও মাল্টি-ফাইল ব্যাচ প্রসেসিং",
            accentColor = SignalOrange,
            headerRight = {
                StatusPill(
                    text = when (uiState.recorderPhase) {
                        RecorderPhase.RECORDING -> "REC LIVE"
                        RecorderPhase.PROFILING -> "LEARNING"
                        RecorderPhase.PAUSED -> "PAUSED"
                        RecorderPhase.PROCESSING -> "DSP"
                        RecorderPhase.IDLE -> "READY"
                    },
                    color = if (uiState.recorderPhase == RecorderPhase.RECORDING) RecRed else TealAccent
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Big Record Button
                    val isRec = uiState.recorderPhase == RecorderPhase.RECORDING
                    val isPaused = uiState.recorderPhase == RecorderPhase.PAUSED
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    if (isRec) listOf(RecRed.copy(alpha = 0.35f), Ink950)
                                    else listOf(Ink800, Ink950)
                                )
                            )
                            .border(
                                width = 3.dp,
                                color = if (isRec) RecRed else SignalOrange,
                                shape = CircleShape
                            )
                            .testTag("record_main_button")
                            .clickable(
                                enabled = uiState.recorderPhase != RecorderPhase.PROFILING &&
                                    uiState.recorderPhase != RecorderPhase.PROCESSING
                            ) {
                                if (isRec) {
                                    viewModel.stopRecordingSession()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.startOrResumeRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isRec) Icons.Default.Stop else if (isPaused) Icons.Default.PlayArrow else Icons.Default.FiberManualRecord,
                                contentDescription = "Record",
                                tint = if (isRec) RecRed else if (isPaused) LimeAccent else RecRed,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = AudioDspEngine.formatTimecode(uiState.capturedSec),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Sand50
                            )
                            Text(
                                text = if (isRec) "স্টপ ও প্রসেস" else if (isPaused) "রিজিউম" else "রেকর্ড শুরু",
                                style = MaterialTheme.typography.labelSmall,
                                color = Sand300
                            )
                        }
                    }

                    // Scope & Stats column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiveScopeAndMeter(
                            scopeValues = uiState.liveScope,
                            db = uiState.liveDb,
                            peakDb = uiState.livePeakDb,
                            thresholdDb = if (uiState.gateSettings.enabled) uiState.gateSettings.thresholdDb else null,
                            gateOpen = uiState.gateOpen,
                            isActive = isRec
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatBadgeCard(
                                label = "ক্যাপচার",
                                value = "%.1f".format(uiState.capturedSec),
                                unit = "s",
                                accent = SignalOrange,
                                modifier = Modifier.weight(1f)
                            )
                            StatBadgeCard(
                                label = "স্কিপড",
                                value = "%.1f".format(uiState.skippedSec),
                                unit = "s",
                                accent = LimeAccent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (uiState.recorderPhase == RecorderPhase.RECORDING || uiState.recorderPhase == RecorderPhase.PAUSED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (uiState.recorderPhase == RecorderPhase.RECORDING) viewModel.pauseRecording()
                                else viewModel.startOrResumeRecording()
                            },
                            border = BorderStroke(1.dp, Ink700)
                        ) {
                            Icon(
                                if (uiState.recorderPhase == RecorderPhase.RECORDING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.recorderPhase == RecorderPhase.RECORDING) "পজ (Pause)" else "রিজিউম")
                        }
                        Button(
                            onClick = { viewModel.stopRecordingSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("স্টপ ও অটো-সাবটাইটেল", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Demo, Import & Save Format Bar
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.loadDemoAudio(20) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950),
                        modifier = Modifier.testTag("load_demo_button")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ডেমো অডিও লোড (20s)", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { audioFileLauncher.launch("audio/*") },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.testTag("import_audio_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Sand100, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("অডিও ইম্পোর্ট", color = Sand100)
                    }
                    OutlinedButton(
                        onClick = { viewModel.setShowAudioFormatDialog(true) },
                        border = BorderStroke(1.dp, SignalOrange.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("open_save_format_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = SignalOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("অডিও ফরম্যাট সেভ", color = SignalOrange, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Panel 2: Noise Profile Learning
        StudioPanel(
            title = "নয়েজ প্রোফাইল লার্নিং (Room Noise Print)",
            step = "02",
            subtitle = "৩–৫ সেকেন্ডের রুম টোন থেকে পরিবেশের নয়েজ প্রিন্ট শিখে নেয় যাতে কণ্ঠ অক্ষুণ্ণ রেখে ফ্যান/AC শব্দ বিয়োগ হয়",
            accentColor = TealAccent,
            headerRight = {
                StatusPill(
                    text = if (uiState.noiseProfile != null) "প্রিন্ট রেডি" else "প্রিন্ট নেই",
                    color = if (uiState.noiseProfile != null) LimeAccent else Sand500
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioSegmentedControl(
                        options = listOf(3 to "৩ সেকেন্ড", 4 to "৪ সেকেন্ড", 5 to "৫ সেকেন্ড"),
                        selected = uiState.profileSec,
                        onSelect = { viewModel.setProfileSec(it) },
                        modifier = Modifier.weight(1f),
                        accent = TealAccent
                    )
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.captureRoomNoisePrint()
                            } else {
                                profilePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = uiState.recorderPhase == RecorderPhase.IDLE,
                        colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                        modifier = Modifier.testTag("capture_noise_print_button")
                    ) {
                        if (uiState.recorderPhase == RecorderPhase.PROFILING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Ink950
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${"%.1f".format(uiState.profileCountdown)}s")
                        } else {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রিন্ট নিন", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val profile = uiState.noiseProfile
                if (profile != null) {
                    SpectrumBarsCanvas(
                        magnitudes = profile.magnitudes,
                        sampleRate = profile.sampleRate,
                        fftSize = profile.fftSize
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBadgeCard(
                            label = "নয়েজ ফ্লোর",
                            value = "%.1f".format(profile.avgDb),
                            unit = "dB",
                            accent = SignalOrange,
                            modifier = Modifier.weight(1f)
                        )
                        StatBadgeCard(
                            label = "শেখা সময়",
                            value = "%.1f".format(profile.learnedSec),
                            unit = "s",
                            accent = TealAccent,
                            modifier = Modifier.weight(1f)
                        )
                        StatBadgeCard(
                            label = "ম্যাক্স কাট",
                            value = "%.0f".format(uiState.suppressOptions.floorDb),
                            unit = "dB",
                            accent = LimeAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Panel 3: Silence Skip Gate + Smart Noise Filter
        StudioPanel(
            title = "সাইলেন্স স্কিপ ও স্মার্ট নয়েজ ফিল্টার",
            step = "03",
            subtitle = "নির্দিষ্ট সময়ের বেশি চুপ থাকলে অটো স্কিপ হবে এবং প্রিন্ট অনুযায়ী লাইভ নয়েজ ক্লিন হবে",
            accentColor = LimeAccent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioToggleRow(
                    label = "সাইলেন্স স্কিপ রেকর্ডিং গেট",
                    hint = "১৪০ms প্রি-রোল বাফারসহ সাইলেন্ট ফ্রেম অটো বাদ দেয়",
                    checked = uiState.gateSettings.enabled,
                    onCheckedChange = { viewModel.updateGateSettings(uiState.gateSettings.copy(enabled = it)) },
                    accent = TealAccent
                )
                StudioSlider(
                    label = "গেট থ্রেশহোল্ড (Threshold)",
                    value = uiState.gateSettings.thresholdDb,
                    valueRange = -60f..-20f,
                    onValueChange = { viewModel.updateGateSettings(uiState.gateSettings.copy(thresholdDb = it)) },
                    valueText = "${uiState.gateSettings.thresholdDb.toInt()} dB",
                    enabled = uiState.gateSettings.enabled,
                    accent = TealAccent
                )
                StudioSlider(
                    label = "হোল্ড টাইম (কত সেকেন্ড চুপ থাকলে স্কিপ)",
                    value = uiState.gateSettings.holdSec,
                    valueRange = 0.5f..5.0f,
                    onValueChange = { viewModel.updateGateSettings(uiState.gateSettings.copy(holdSec = it)) },
                    valueText = "${"%.1f".format(uiState.gateSettings.holdSec)} s",
                    enabled = uiState.gateSettings.enabled,
                    accent = TealAccent
                )

                StudioToggleRow(
                    label = "লাইভ ও পোস্ট-রেকর্ড অটো নয়েজ ক্লিন",
                    hint = "রেকর্ড স্টপ করার সাথে সাথে নয়েজ প্রিন্ট বিয়োগ ও RMS লেভেলিং",
                    checked = uiState.autoCleanOnStop,
                    onCheckedChange = { viewModel.setAutoCleanOnStop(it) },
                    accent = SignalOrange
                )
                StudioSlider(
                    label = "সাবট্রাকশন স্ট্রেংথ (Strength)",
                    value = uiState.suppressOptions.strength,
                    valueRange = 1.0f..4.0f,
                    onValueChange = { viewModel.updateSuppressOptions(uiState.suppressOptions.copy(strength = it)) },
                    valueText = "${"%.1f".format(uiState.suppressOptions.strength)}×",
                    accent = SignalOrange
                )
            }
        }

        // Panel 4: Batch Audio Processing Queue
        StudioPanel(
            title = "ব্যাচ অডিও ও সাবটাইটেল কিউ (Batch Processing)",
            step = "04",
            subtitle = "একাধিক অডিও ফাইল একসাথে নয়েজ ক্লিন, গ্লোসারি চেক ও অটো-সাবটাইটেল তৈরি করুন",
            accentColor = SignalOrange,
            headerRight = {
                StatusPill(text = "${batchItems.size} ফাইল", color = SignalOrange)
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.addDemoFilesToBatchQueue() },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f).testTag("batch_add_demo_button")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("৩টি ফাইল কিউতে যোগ", color = Sand100)
                    }
                    Button(
                        onClick = { viewModel.processAllBatchQueue() },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                        modifier = Modifier.weight(1f).testTag("batch_process_all_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সব প্রসেস করুন", fontWeight = FontWeight.Bold)
                    }
                }

                if (batchItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Ink900, RoundedCornerShape(10.dp))
                            .border(1.dp, Ink700, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ব্যাচ কিউ খালি — '৩টি ফাইল কিউতে যোগ' বাটনে ট্যাপ করে একসাথে প্রসেস টেস্ট করুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sand500
                        )
                    }
                } else {
                    batchItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Ink900, RoundedCornerShape(10.dp))
                                .border(1.dp, Ink700, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = if (item.status == "DONE") LimeAccent else SignalOrange
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = Sand50
                                    )
                                    Text(
                                        text = "${item.durationSec}s · ভাষা: ${item.languageCode.uppercase()} · ${item.exportedFormat}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Sand300
                                    )
                                }
                            }
                            StatusPill(
                                text = item.status,
                                color = when (item.status) {
                                    "DONE" -> LimeAccent
                                    "PROCESSING" -> SignalOrange
                                    else -> TealAccent
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearBatchQueue() },
                            border = BorderStroke(1.dp, Ink700)
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = null, tint = Sand300)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("কিউ পরিষ্কার", color = Sand300)
                        }
                        Button(
                            onClick = { viewModel.selectTab(StudioTab.SUBTITLES) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950)
                        ) {
                            Text("সাবটাইটেল এডিটরে যান →", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
