package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioDspEngine
import com.example.audio.TimeRange
import com.example.ui.components.InteractiveWaveformCanvas
import com.example.ui.components.StatBadgeCard
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPanel
import com.example.ui.components.StudioSegmentedControl
import com.example.ui.components.StudioSlider
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink800
import com.example.ui.theme.Ink900
import com.example.ui.theme.Ink950
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.RecRed
import com.example.ui.theme.Sand100
import com.example.ui.theme.Sand300
import com.example.ui.theme.Sand50
import com.example.ui.theme.Sand500
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TealAccent
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioEditorAndEffectsScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel
) {
    var manualStartText by remember { mutableStateOf("0.0") }
    var manualEndText by remember { mutableStateOf("4.5") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Waveform Deck + Multi-Format Audio Save Bar
        StudioPanel(
            title = "ওয়েভফর্ম এডিটর ও মাস্টার ডেক",
            step = "WAVE",
            accentColor = SignalOrange,
            headerRight = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.undoStack.isNotEmpty(),
                        modifier = Modifier.size(34.dp).testTag("editor_undo_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.undoStack.isNotEmpty()) SignalOrange else Sand500
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.redoStack.isNotEmpty(),
                        modifier = Modifier.size(34.dp).testTag("editor_redo_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.redoStack.isNotEmpty()) SignalOrange else Sand500
                        )
                    }
                    Button(
                        onClick = { viewModel.setShowAudioFormatDialog(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950),
                        modifier = Modifier.testTag("editor_save_format_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("অডিও সেভ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InteractiveWaveformCanvas(
                    samples = uiState.previewSamples ?: uiState.samples,
                    sampleRate = uiState.sampleRate,
                    viewRange = uiState.viewRange,
                    playheadSec = uiState.playheadSec,
                    selection = uiState.selection,
                    onSeek = { viewModel.seekTo(it) },
                    onSelectionChange = { viewModel.setSelection(it) },
                    silenceRegions = uiState.detectedSilences,
                    chunkRegions = if (uiState.splitMode == "auto") uiState.autoSplitChunks else uiState.manualSlots.map { TimeRange(it.startSec, it.endSec) },
                    subtitleBlocks = uiState.subtitles,
                    waveColor = if (uiState.previewSamples != null) TealAccent else SignalOrange
                )

                // Transport & Zoom Row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.togglePlayback() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isPlaying) RecRed else SignalOrange,
                            contentColor = Ink950
                        ),
                        modifier = Modifier.testTag("editor_play_button")
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (uiState.isPlaying) "পজ" else if (uiState.selection != null) "সিলেকশন প্লে" else "প্লে",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopPlayback() },
                        border = BorderStroke(1.dp, Ink700)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Sand100, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("স্টপ", color = Sand100)
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleLoop() },
                        border = BorderStroke(1.dp, if (uiState.loopPlayback) TealAccent else Ink700)
                    ) {
                        Icon(
                            Icons.Default.Loop,
                            contentDescription = null,
                            tint = if (uiState.loopPlayback) TealAccent else Sand300,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("লুপ", color = if (uiState.loopPlayback) TealAccent else Sand300)
                    }
                    IconButton(onClick = { viewModel.zoomWaveform(0.62f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Sand100)
                    }
                    IconButton(onClick = { viewModel.zoomWaveform(1.6f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Sand100)
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetZoom() },
                        border = BorderStroke(1.dp, Ink700)
                    ) {
                        Text("ফিট (${AudioDspEngine.formatTimecode(uiState.analysis.durationSec)})", color = Sand300)
                    }
                    OutlinedButton(
                        onClick = { viewModel.trimToSelection() },
                        border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("editor_trim_selection_button")
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = TealAccent, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সিলেকশন ট্রিম", color = TealAccent)
                    }
                    OutlinedButton(
                        onClick = { viewModel.deleteSelectedAudioRange() },
                        border = BorderStroke(1.dp, RecRed.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("editor_delete_selection_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RecRed, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সিলেকশন ডিলিট", color = RecRed)
                    }
                    OutlinedButton(
                        onClick = { viewModel.applyFadeInOutToAudio() },
                        border = BorderStroke(1.dp, LimeAccent.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("editor_fade_button")
                    ) {
                        Text("ফেড ইন/আউট", color = LimeAccent)
                    }
                }

                // Loudness & Signal Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatBadgeCard(
                        label = "পিক (Peak)",
                        value = "%.1f".format(uiState.analysis.peakDb),
                        unit = "dB",
                        accent = SignalOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadgeCard(
                        label = "RMS লাউডনেস",
                        value = "%.1f".format(uiState.analysis.rmsDb),
                        unit = "dB",
                        accent = TealAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadgeCard(
                        label = "ডাইনামিক রেঞ্জ",
                        value = "%.1f".format(uiState.analysis.dynamicRangeDb),
                        unit = "dB",
                        accent = LimeAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatBadgeCard(
                        label = "সাইলেন্স",
                        value = "%.1f".format(uiState.analysis.silenceSec),
                        unit = "s",
                        accent = Sand100,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sub-navigation inside Editor for clean, user-friendly switching
        StudioSegmentedControl(
            options = listOf(
                0 to "সাইলেন্স কাট",
                1 to "নয়েজ রিমুভ",
                2 to "স্প্লিটার",
                3 to "ইফেক্টস ও বুস্ট"
            ),
            selected = uiState.editorSubTab,
            onSelect = { viewModel.setEditorSubTab(it) },
            accent = SignalOrange
        )

        when (uiState.editorSubTab) {
            0 -> {
                // Dual-Mode Silence Removal
                StudioPanel(
                    title = "ডুয়েল মোড সাইলেন্স রিমুভাল (Silence Trimmer)",
                    step = "TOOL 1",
                    subtitle = "dB মোডে সব চুপ অংশ কাটে; সেকেন্ড-লিমিট মোডে লম্বা বিরতি নির্দিষ্ট সেকেন্ড পর্যন্ত ছেঁটে দেয়",
                    accentColor = SignalOrange
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioSegmentedControl(
                            options = listOf(
                                "db" to "dB মোড (পুরোপুরি কাটো)",
                                "sec" to "সেকেন্ড লিমিট মোড"
                            ),
                            selected = uiState.silenceMode,
                            onSelect = { viewModel.updateSilenceParams(mode = it) },
                            accent = TealAccent
                        )
                        StudioSlider(
                            label = "সাইলেন্স থ্রেশহোল্ড (Threshold)",
                            value = uiState.silenceThresholdDb,
                            valueRange = -70f..-20f,
                            onValueChange = { viewModel.updateSilenceParams(thresholdDb = it) },
                            valueText = "${uiState.silenceThresholdDb.toInt()} dB"
                        )
                        StudioSlider(
                            label = "ন্যূনতম সাইলেন্স দৈর্ঘ্য (Min Pause)",
                            value = uiState.silenceMinSec,
                            valueRange = 0.1f..2.5f,
                            onValueChange = { viewModel.updateSilenceParams(minSec = it) },
                            valueText = "${"%.2f".format(uiState.silenceMinSec)} s"
                        )
                        if (uiState.silenceMode == "sec") {
                            StudioSlider(
                                label = "বিরতি সর্বোচ্চ কত সেকেন্ড রাখবেন (Keep Max)",
                                value = uiState.silenceKeepSec,
                                valueRange = 0.15f..2.5f,
                                onValueChange = { viewModel.updateSilenceParams(keepSec = it) },
                                valueText = "${"%.2f".format(uiState.silenceKeepSec)} s",
                                accent = TealAccent
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.scanSilenceRegions() },
                                border = BorderStroke(1.dp, Ink700),
                                modifier = Modifier.weight(1f).testTag("scan_silence_button")
                            ) {
                                Text("সাইলেন্স খুঁজুন (${uiState.detectedSilences.size})", color = Sand100)
                            }
                            Button(
                                onClick = { viewModel.applySilenceRemoval() },
                                colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                                modifier = Modifier.weight(1f).testTag("apply_silence_remove_button")
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সাইলেন্স রিমুভ", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            1 -> {
                // Sample-based Noise Removal
                StudioPanel(
                    title = "স্যাম্পল বেসড নয়েজ রিমুভ (Sample Denoiser)",
                    step = "TOOL 2",
                    subtitle = "ওয়েভফর্মে ড্র্যাগ করে নয়েজ অংশ সিলেক্ট করুন এবং এক ক্লিকে পুরো ফাইল ক্লিন করুন",
                    accentColor = TealAccent
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Ink900, RoundedCornerShape(10.dp))
                                .border(1.dp, Ink700, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.selection != null) {
                                        "সিলেকশন: ${AudioDspEngine.formatTimecode(uiState.selection.start)} → ${AudioDspEngine.formatTimecode(uiState.selection.end)}"
                                    } else {
                                        "ডিফল্ট: শুরুর রুম টোন থেকে প্রিন্ট"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Sand50
                                )
                                Text(
                                    text = if (uiState.noiseProfile != null) {
                                        "শেখা নয়েজ ফ্লোর: ${"%.1f".format(uiState.noiseProfile.avgDb)} dB"
                                    } else {
                                        "এখনো কোনো প্রিন্ট নেওয়া হয়নি"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TealAccent
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.learnNoiseFromSelection() },
                                border = BorderStroke(1.dp, TealAccent)
                            ) {
                                Text("স্যাম্পল নিন", color = TealAccent)
                            }
                        }

                        StudioSlider(
                            label = "নয়েজ সাবট্রাকশন স্ট্রেংথ",
                            value = uiState.suppressOptions.strength,
                            valueRange = 1.0f..4.0f,
                            onValueChange = { viewModel.updateSuppressOptions(uiState.suppressOptions.copy(strength = it)) },
                            valueText = "${"%.1f".format(uiState.suppressOptions.strength)}×",
                            accent = TealAccent
                        )
                        StudioSlider(
                            label = "ম্যাক্স অ্যাটেনুয়েশন ফ্লোর",
                            value = uiState.suppressOptions.floorDb,
                            valueRange = -48f..-8f,
                            onValueChange = { viewModel.updateSuppressOptions(uiState.suppressOptions.copy(floorDb = it)) },
                            valueText = "${uiState.suppressOptions.floorDb.toInt()} dB",
                            accent = TealAccent
                        )

                        Button(
                            onClick = { viewModel.applyLearnedNoiseRemoval() },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                            modifier = Modifier.fillMaxWidth().testTag("apply_noise_removal_button")
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("পুরো ফাইলে নয়েজ রিমুভ প্রয়োগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            2 -> {
                // Flexible Audio Splitter
                StudioPanel(
                    title = "ফ্লেক্সিবল অডিও স্প্লিটার (100 Chunks & Manual +)",
                    step = "TOOL 3",
                    subtitle = "সাইলেন্স অনুযায়ী অটো ভাগ করুন অথবা নিজের টাইম রেঞ্জ (+) যোগ করে আলাদা WAV বা ZIP এক্সপোর্ট করুন",
                    accentColor = LimeAccent
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioSegmentedControl(
                            options = listOf(
                                "auto" to "অটো সাইলেন্স স্প্লিট",
                                "manual" to "ম্যানুয়াল টাইম রেঞ্জ (+)"
                            ),
                            selected = uiState.splitMode,
                            onSelect = { viewModel.updateSplitOptions(mode = it) },
                            accent = LimeAccent
                        )

                        if (uiState.splitMode == "auto") {
                            StudioSlider(
                                label = "গ্যাপ থ্রেশহোল্ড",
                                value = uiState.splitOptions.thresholdDb,
                                valueRange = -65f..-22f,
                                onValueChange = { viewModel.updateSplitOptions(opts = uiState.splitOptions.copy(thresholdDb = it)) },
                                valueText = "${uiState.splitOptions.thresholdDb.toInt()} dB"
                            )
                            StudioSlider(
                                label = "সর্বোচ্চ টুকরো সংখ্যা (Max Chunks)",
                                value = uiState.splitOptions.maxChunks.toFloat(),
                                valueRange = 2f..100f,
                                onValueChange = { viewModel.updateSplitOptions(opts = uiState.splitOptions.copy(maxChunks = it.toInt())) },
                                valueText = "${uiState.splitOptions.maxChunks} টুকরো",
                                accent = TealAccent
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.runAutoSplitter() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                                    modifier = Modifier.weight(1f).testTag("run_auto_split_button")
                                ) {
                                    Text("অটো টুকরো করুন", fontWeight = FontWeight.Bold)
                                }
                                if (uiState.autoSplitChunks.isNotEmpty()) {
                                    Button(
                                        onClick = { viewModel.exportSplitChunksZip(uiState.autoSplitChunks) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ZIP এক্সপোর্ট (${uiState.autoSplitChunks.size})", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualStartText,
                                    onValueChange = { manualStartText = it },
                                    label = { Text("শুরু (s)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealAccent,
                                        unfocusedBorderColor = Ink700
                                    )
                                )
                                OutlinedTextField(
                                    value = manualEndText,
                                    onValueChange = { manualEndText = it },
                                    label = { Text("শেষ (s)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TealAccent,
                                        unfocusedBorderColor = Ink700
                                    )
                                )
                                Button(
                                    onClick = {
                                        val s = manualStartText.toFloatOrNull() ?: 0f
                                        val e = manualEndText.toFloatOrNull() ?: (s + 4f)
                                        viewModel.addManualSplitSlot(s, e)
                                        manualStartText = "%.1f".format(e)
                                        manualEndText = "%.1f".format(e + 4.5f)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Range")
                                    Text("যোগ")
                                }
                            }

                            uiState.manualSlots.forEachIndexed { idx, slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Ink900, RoundedCornerShape(8.dp))
                                        .border(1.dp, Ink700, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "#${idx + 1}  ${AudioDspEngine.formatTimecode(slot.startSec)} → ${AudioDspEngine.formatTimecode(slot.endSec)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Sand50
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.playSpecificRange(slot.startSec, slot.endSec) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play slot", tint = TealAccent)
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeManualSplitSlot(slot.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete slot", tint = RecRed)
                                        }
                                    }
                                }
                            }

                            if (uiState.manualSlots.isNotEmpty()) {
                                val ranges = uiState.manualSlots.map { TimeRange(it.startSec, it.endSec) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.keepSelectedSplitRanges(ranges) },
                                        border = BorderStroke(1.dp, SignalOrange),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("জোড়া দিয়ে মাস্টার বানান", color = SignalOrange)
                                    }
                                    Button(
                                        onClick = { viewModel.exportSplitChunksZip(ranges) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("সব স্লট ZIP ডাউনলোড", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Studio Voice Effects, Presets & Voice Booster
                StudioPanel(
                    title = "AI ভয়েস এনহ্যান্সার ও অটো-লেভেল বুস্টার",
                    step = "TOOL 4",
                    subtitle = "পডকাস্ট প্রিসেট, ভয়েস বুস্টার, মাল্টি-ব্যান্ড ইকুয়ালাইজার, ডি-এসার ও কম্প্রেসর চেইন",
                    accentColor = SignalOrange,
                    headerRight = {
                        Button(
                            onClick = { viewModel.applyOneClickStudioChain() },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                            modifier = Modifier.testTag("one_click_studio_chain_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("১-ক্লিক স্টুডিও চেইন", fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Presets Grid
                        Text(
                            text = "স্টুডিও ভয়েস প্রিসেট নির্বাচন করুন:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Sand300
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioDspEngine.enhancePresets.forEach { preset ->
                                val active = uiState.activePresetId == preset.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) SignalOrange.copy(alpha = 0.18f) else Ink900)
                                        .border(
                                            1.dp,
                                            if (active) SignalOrange else Ink700,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.selectEnhancePreset(preset.id) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (active) SignalOrange else Sand50
                                        )
                                        Text(
                                            text = preset.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TealAccent
                                        )
                                    }
                                }
                            }
                        }

                        // A/B Comparison Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.togglePlayback(useOriginal = true) },
                                border = BorderStroke(1.dp, Ink700),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("মূল অডিও (A)", color = Sand300)
                            }
                            OutlinedButton(
                                onClick = { viewModel.renderEnhancePreview() },
                                border = BorderStroke(1.dp, TealAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("প্রিভিউ রেন্ডার", color = TealAccent)
                            }
                            if (uiState.previewSamples != null) {
                                Button(
                                    onClick = { viewModel.togglePlayback(usePreview = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("প্রিভিউ (B)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Voice Booster / Auto-Leveler
                        StudioSlider(
                            label = "টার্গেট লাউডনেস (Voice Booster RMS)",
                            value = uiState.autoLevelOpts.targetDb,
                            valueRange = -28f..-8f,
                            onValueChange = { viewModel.updateAutoLevelOptions(uiState.autoLevelOpts.copy(targetDb = it)) },
                            valueText = "${"%.1f".format(uiState.autoLevelOpts.targetDb)} dBFS",
                            accent = LimeAccent
                        )
                        StudioSlider(
                            label = "প্রেজেন্স ক্লিয়ারিটি বুস্ট (3.2 kHz)",
                            value = uiState.enhanceConfig.presenceGainDb,
                            valueRange = -6f..10f,
                            onValueChange = { viewModel.updateEnhanceConfig(uiState.enhanceConfig.copy(presenceGainDb = it)) },
                            valueText = "${"%.1f".format(uiState.enhanceConfig.presenceGainDb)} dB"
                        )
                        StudioSlider(
                            label = "ওয়ার্মথ বডি (Low-Shelf 180 Hz)",
                            value = uiState.enhanceConfig.warmthGainDb,
                            valueRange = -6f..8f,
                            onValueChange = { viewModel.updateEnhanceConfig(uiState.enhanceConfig.copy(warmthGainDb = it)) },
                            valueText = "${"%.1f".format(uiState.enhanceConfig.warmthGainDb)} dB"
                        )
                        StudioSlider(
                            label = "ডি-এসার (Sibilance Cut 6.8 kHz)",
                            value = uiState.enhanceConfig.deEsserGainDb,
                            valueRange = -12f..0f,
                            onValueChange = { viewModel.updateEnhanceConfig(uiState.enhanceConfig.copy(deEsserGainDb = it)) },
                            valueText = "${"%.1f".format(uiState.enhanceConfig.deEsserGainDb)} dB",
                            accent = TealAccent
                        )
                        StudioSlider(
                            label = "কম্প্রেসর রেশিও (Dynamics)",
                            value = uiState.enhanceConfig.compRatio,
                            valueRange = 1f..12f,
                            onValueChange = { viewModel.updateEnhanceConfig(uiState.enhanceConfig.copy(compRatio = it)) },
                            valueText = "${"%.1f".format(uiState.enhanceConfig.compRatio)}:1"
                        )

                        Button(
                            onClick = { viewModel.applyAutoLevelBooster() },
                            colors = ButtonDefaults.buttonColors(containerColor = LimeAccent, contentColor = Ink950),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("শুধু ভয়েস বুস্টার ও অটো-লেভেল প্রয়োগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
