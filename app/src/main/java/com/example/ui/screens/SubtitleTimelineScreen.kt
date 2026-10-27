package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioDspEngine
import com.example.audio.SubtitleBlock
import com.example.audio.SupportedLanguage
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPanel
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
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleTimelineScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel
) {
    val activeSub = uiState.subtitles.firstOrNull {
        uiState.playheadSec >= it.startSec && uiState.playheadSec <= it.endSec
    } ?: uiState.subtitles.firstOrNull { it.id == uiState.selectedSubtitleId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Panel 1: Visual Timeline & Real-Time Linked Player Preview (Feature 1)
        StudioPanel(
            title = "ভিজ্যুয়াল টাইমলাইন ও লাইভ সাবটাইটেল প্রিভিউ",
            step = "FEAT 1",
            subtitle = "অডিও প্লেয়ারের সাথে রিয়েল-টাইম লিঙ্কড সাবটাইটেল ও ইন্টারেক্টিভ ব্লক টাইমলাইন",
            accentColor = VioletAccent,
            headerRight = {
                StatusPill(
                    text = "${uiState.subtitles.size} ব্লক",
                    color = VioletAccent
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Real-time Subtitle Preview Monitor Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink950)
                        .border(1.dp, VioletAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LIVE PREVIEW · ${AudioDspEngine.formatTimecode(uiState.playheadSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalOrange
                            )
                            Text(
                                text = activeSub?.let { "#${it.index} (${AudioDspEngine.formatTimecode(it.startSec)}–${AudioDspEngine.formatTimecode(it.endSec)})" } ?: "NO ACTIVE BLOCK",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeSub?.originalText ?: "অডিও প্লে করুন বা নিচের যেকোনো সাবটাইটেল ব্লকে ট্যাপ করুন…",
                            style = MaterialTheme.typography.titleMedium,
                            color = Sand50,
                            textAlign = TextAlign.Center
                        )
                        if (activeSub != null && activeSub.translatedText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeSub.translatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TealAccent,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Interactive Visual Timeline Canvas (Waveform + Subtitle Blocks Track)
                VisualSubtitleTimelineTrack(
                    samples = uiState.samples,
                    sampleRate = uiState.sampleRate,
                    totalDurationSec = max(1f, uiState.analysis.durationSec),
                    playheadSec = uiState.playheadSec,
                    subtitles = uiState.subtitles,
                    selectedSubtitleId = uiState.selectedSubtitleId,
                    onSeekAndSelect = { sec, blockId ->
                        viewModel.seekTo(sec)
                        if (blockId != null) viewModel.selectSubtitleBlock(blockId)
                    }
                )

                // Player & Global Time-Shift Row
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
                        modifier = Modifier.testTag("subtitle_play_button")
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (uiState.isPlaying) "পজ" else "প্রিভিউ প্লে", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.shiftAllSubtitleTimestamps(-0.25f) },
                        border = BorderStroke(1.dp, Ink700)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Sand300, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("-0.25s শিফট", color = Sand300)
                    }
                    OutlinedButton(
                        onClick = { viewModel.shiftAllSubtitleTimestamps(0.25f) },
                        border = BorderStroke(1.dp, Ink700)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Sand300, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+0.25s শিফট", color = Sand300)
                    }
                    OutlinedButton(
                        onClick = { viewModel.addNewSubtitleBlock() },
                        border = BorderStroke(1.dp, TealAccent),
                        modifier = Modifier.testTag("add_subtitle_block_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("নতুন ব্লক", color = TealAccent)
                    }
                }
            }
        }

        // Panel 2: Multi-Language Speech-to-Text & 1-Click Direct Auto-Translation (Feature 4)
        StudioPanel(
            title = "মাল্টি-ল্যাঙ্গুয়েজ ট্রান্সক্রিপশন ও অটো-ট্রান্সলেশন",
            step = "FEAT 4",
            subtitle = "অডিওর ভাষা নির্বাচন করে ট্রান্সক্রাইব করুন এবং ১ ক্লিকে যেকোনো ভাষায় সাবটাইটেল অনুবাদ ও .SRT/.TXT এক্সপোর্ট করুন",
            accentColor = TealAccent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "১. অডিও ইনপুট ভাষা (Audio Input Language):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand300
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportedLanguage.entries.forEach { lang ->
                        val selected = uiState.sourceLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) SignalOrange else Ink900)
                                .border(1.dp, if (selected) SignalOrange else Ink700, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setSourceLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${lang.nativeName} (${lang.code.uppercase()})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) Ink950 else Sand100
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.runSpeechToSubtitles() },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                    modifier = Modifier.fillMaxWidth().testTag("transcribe_audio_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${uiState.sourceLanguage.nativeName} অডিও থেকে সাবটাইটেল ও টাইমস্ট্যাম্প জেনারেট করুন",
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "২. ১-ক্লিক অটো-ট্রান্সলেশন ভাষা (Target Translation Language):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand300
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportedLanguage.entries.forEach { lang ->
                        val selected = uiState.targetLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) TealAccent else Ink900)
                                .border(1.dp, if (selected) TealAccent else Ink700, RoundedCornerShape(8.dp))
                                .testTag("translate_lang_${lang.code}")
                                .clickable { viewModel.translateAllSubtitles(lang) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "→ ${lang.nativeName}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) Ink950 else Sand100
                            )
                        }
                    }
                }

                StudioToggleRow(
                    label = "ডুয়াল সাবটাইটেল মোড (Bilingual Original + Translated)",
                    hint = "SRT/TXT এবং ভিডিও প্রিভিউতে মূল ও অনূদিত দুই ভাষাই একসাথে দেখাবে",
                    checked = uiState.subtitleStyle.bilingualDualSub,
                    onCheckedChange = {
                        viewModel.updateSubtitleStyle(uiState.subtitleStyle.copy(bilingualDualSub = it))
                    },
                    accent = TealAccent
                )

                // Export SRT & TXT Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportSubtitles("srt") },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Ink950),
                        modifier = Modifier.weight(1f).testTag("export_srt_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(".SRT এক্সপোর্ট", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.exportSubtitles("txt") },
                        colors = ButtonDefaults.buttonColors(containerColor = LimeAccent, contentColor = Ink950),
                        modifier = Modifier.weight(1f).testTag("export_txt_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(".TXT এক্সপোর্ট", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Panel 3: Interactive Subtitle Block Editor List
        StudioPanel(
            title = "ইন-অ্যাপ সাবটাইটেল ব্লক এডিটর (Click to Edit & Trim)",
            step = "BLOCKS",
            subtitle = "যেকোনো ব্লকে ক্লিক করে টেক্সট সংশোধন, শুরু/শেষ টাইমস্ট্যাম্প ট্রিম (+/- 0.1s) কিংবা স্প্লিট করুন",
            accentColor = SignalOrange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.subtitles.forEach { block ->
                    val isSelected = block.id == uiState.selectedSubtitleId
                    SubtitleBlockEditorCard(
                        block = block,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectSubtitleBlock(block.id) },
                        onPlayBlock = { viewModel.playSpecificRange(block.startSec, block.endSec) },
                        onTextChange = { orig, trans ->
                            viewModel.updateSubtitleBlock(block.id, newOriginal = orig, newTranslated = trans)
                        },
                        onTimestampNudge = { dStart, dEnd ->
                            viewModel.updateSubtitleBlock(
                                id = block.id,
                                newStartSec = block.startSec + dStart,
                                newEndSec = block.endSec + dEnd
                            )
                        },
                        onSplit = { viewModel.splitSelectedSubtitleBlock(block.id) },
                        onDelete = { viewModel.deleteSubtitleBlock(block.id) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VisualSubtitleTimelineTrack(
    samples: FloatArray,
    sampleRate: Int,
    totalDurationSec: Float,
    playheadSec: Float,
    subtitles: List<SubtitleBlock>,
    selectedSubtitleId: String?,
    onSeekAndSelect: (Float, String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink900)
            .border(1.dp, Ink700, RoundedCornerShape(10.dp))
            .pointerInput(totalDurationSec, subtitles) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val sec = ((offset.x / w) * totalDurationSec).coerceIn(0f, totalDurationSec)
                    val hit = subtitles.firstOrNull { sec >= it.startSec && sec <= it.endSec }
                    onSeekAndSelect(sec, hit?.id)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val waveTop = 6f
            val waveH = h * 0.42f
            val waveMid = waveTop + waveH / 2f

            // Mini waveform in top half
            if (samples.isNotEmpty() && sampleRate > 0) {
                val cols = 96
                val colW = w / cols
                val perCol = max(1, samples.size / cols)
                for (c in 0 until cols) {
                    val s0 = c * perCol
                    val s1 = min(samples.size, s0 + perCol)
                    var peak = 0f
                    val step = max(1, (s1 - s0) / 10)
                    var i = s0
                    while (i < s1) {
                        val a = abs(samples[i])
                        if (a > peak) peak = a
                        i += step
                    }
                    val bh = (peak * (waveH * 0.46f)).coerceAtLeast(1.5f)
                    drawRoundRect(
                        color = Sand500.copy(alpha = 0.65f),
                        topLeft = Offset(c * colW + 1f, waveMid - bh),
                        size = Size(colW - 2f, bh * 2f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }

            // Subtitle blocks in bottom half
            val trackTop = h * 0.50f
            val trackH = h * 0.42f
            for (sub in subtitles) {
                val x0 = ((sub.startSec / totalDurationSec) * w).coerceIn(0f, w)
                val x1 = ((sub.endSec / totalDurationSec) * w).coerceIn(x0 + 4f, w)
                val isSelected = sub.id == selectedSubtitleId
                val isPlayingNow = playheadSec >= sub.startSec && playheadSec <= sub.endSec
                val fill = when {
                    isPlayingNow -> SignalOrange.copy(alpha = 0.45f)
                    isSelected -> TealAccent.copy(alpha = 0.38f)
                    else -> VioletAccent.copy(alpha = 0.25f)
                }
                val stroke = when {
                    isPlayingNow -> SignalOrange
                    isSelected -> TealAccent
                    else -> VioletAccent
                }
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(x0, trackTop),
                    size = Size(x1 - x0, trackH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(x0, trackTop),
                    size = Size(x1 - x0, trackH),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f)
                )
            }

            // Playhead vertical line
            val px = ((playheadSec / totalDurationSec) * w).coerceIn(0f, w)
            drawLine(
                color = Color.White,
                start = Offset(px, 0f),
                end = Offset(px, h),
                strokeWidth = 2.5f
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleBlockEditorCard(
    block: SubtitleBlock,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlayBlock: () -> Unit,
    onTextChange: (String, String) -> Unit,
    onTimestampNudge: (Float, Float) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subtitle_block_${block.index}")
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Ink800 else Ink900
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) SignalOrange else Ink700
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top bar: index, timestamps, glossary badges, play/split/delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#%02d".format(block.index),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = SignalOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${AudioDspEngine.formatTimecode(block.startSec)} → ${AudioDspEngine.formatTimecode(block.endSec)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = TealAccent
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayBlock, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play segment", tint = TealAccent)
                    }
                    IconButton(onClick = onSplit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CallSplit, contentDescription = "Split subtitle", tint = SignalOrange)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete subtitle", tint = RecRed)
                    }
                }
            }

            if (block.matchedGlossaryTerms.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    block.matchedGlossaryTerms.forEach { term ->
                        StatusPill(text = "গ্লোসারি: $term", color = LimeAccent)
                    }
                }
            }

            if (isSelected) {
                // Editable TextFields + Timestamp Trim controls when selected
                OutlinedTextField(
                    value = block.originalText,
                    onValueChange = { onTextChange(it, block.translatedText) },
                    label = { Text("মূল সাবটাইটেল টেক্সট (Original)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalOrange,
                        unfocusedBorderColor = Ink700
                    )
                )
                OutlinedTextField(
                    value = block.translatedText,
                    onValueChange = { onTextChange(block.originalText, it) },
                    label = { Text("অনূদিত সাবটাইটেল (Translated)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Ink700
                    )
                )

                // Timestamp Fine Trim Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTimestampNudge(-0.1f, 0f) },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("শুরু -0.1s", fontSize = 11.sp, color = Sand100)
                    }
                    OutlinedButton(
                        onClick = { onTimestampNudge(0.1f, 0f) },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("শুরু +0.1s", fontSize = 11.sp, color = Sand100)
                    }
                    OutlinedButton(
                        onClick = { onTimestampNudge(0f, -0.1f) },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("শেষ -0.1s", fontSize = 11.sp, color = TealAccent)
                    }
                    OutlinedButton(
                        onClick = { onTimestampNudge(0f, 0.1f) },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("শেষ +0.1s", fontSize = 11.sp, color = TealAccent)
                    }
                }
            } else {
                Text(
                    text = block.originalText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Sand50,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (block.translatedText.isNotBlank()) {
                    Text(
                        text = "↳ ${block.translatedText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TealAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
