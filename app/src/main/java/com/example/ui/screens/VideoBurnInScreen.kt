package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioDspEngine
import com.example.audio.SubtitleFontOption
import com.example.audio.SubtitlePositionOption
import com.example.audio.VideoBurnInExporter
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPanel
import com.example.ui.components.StudioSegmentedControl
import com.example.ui.components.StudioSlider
import com.example.ui.components.StudioToggleRow
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink800
import com.example.ui.theme.Ink900
import com.example.ui.theme.Ink950
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.RecRed
import com.example.ui.theme.Sand100
import com.example.ui.theme.Sand300
import com.example.ui.theme.Sand50
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TealAccent
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoBurnInScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel
) {
    // Zero-permission Android Photo Picker for selecting a video to burn subtitles into
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importVideoForBurnIn(uri, uri.lastPathSegment)
        }
    }

    val style = uiState.subtitleStyle
    val totalSec = max(1f, uiState.analysis.durationSec)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Panel 1: Real-time Video Frame + Burned-in Subtitle Canvas Preview
        StudioPanel(
            title = "ভিডিও বার্ন-ইন প্রিভিউ ও .MP4 রেন্ডারার",
            step = "FEAT 3",
            subtitle = "ভিডিও আপলোড করুন অথবা স্টুডিও ওয়েভফর্ম ব্যাকড্রপে কাস্টম স্টাইলের সাবটাইটেল হার্ডকোড করে .MP4 ডাউনলোড করুন",
            accentColor = SignalOrange,
            headerRight = {
                StatusPill(
                    text = uiState.customVideoName ?: "STUDIO WAVE VIDEO",
                    color = TealAccent
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 16:9 Live Burn-in Video Monitor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, Ink700, RoundedCornerShape(12.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        VideoBurnInExporter.renderStyledFrame(
                            canvas = drawContext.canvas.nativeCanvas,
                            width = size.width.toInt().coerceAtLeast(320),
                            height = size.height.toInt().coerceAtLeast(180),
                            timeSec = uiState.playheadSec,
                            totalDurationSec = totalSec,
                            samples = uiState.samples,
                            sampleRate = uiState.sampleRate,
                            subtitles = uiState.subtitles,
                            style = style,
                            projectTitle = uiState.projectTitle,
                            backdropBitmap = uiState.customVideoBackdrop
                        )
                    }
                }

                // Scrub Slider + Play Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.togglePlayback() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isPlaying) RecRed else SignalOrange,
                            contentColor = Ink950
                        )
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Slider(
                        value = uiState.playheadSec.coerceIn(0f, totalSec),
                        onValueChange = { viewModel.seekTo(it) },
                        valueRange = 0f..totalSec,
                        colors = SliderDefaults.colors(
                            thumbColor = TealAccent,
                            activeTrackColor = TealAccent,
                            inactiveTrackColor = Ink700
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = AudioDspEngine.formatTimecode(uiState.playheadSec, false),
                        style = MaterialTheme.typography.labelMedium,
                        color = Sand100
                    )
                }

                // Upload Video & Export Burn-in MP4 Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        border = BorderStroke(1.dp, TealAccent),
                        modifier = Modifier.weight(1f).testTag("upload_video_button")
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ভিডিও আপলোড", color = TealAccent, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { viewModel.exportBurnInVideoMp4() },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950),
                        modifier = Modifier.weight(1.25f).testTag("export_burnin_mp4_button")
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("বার্ন-ইন .MP4 এক্সপোর্ট", fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.videoExportProgress != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { uiState.videoExportProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = LimeAccent,
                            trackColor = Ink800
                        )
                        Text(
                            text = "হার্ডকোডেড সাবটাইটেল .MP4 রেন্ডারিং: ${(uiState.videoExportProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = LimeAccent
                        )
                    }
                }
            }
        }

        // Panel 2: Custom Subtitle Appearance Studio
        StudioPanel(
            title = "সাবটাইটেল কাস্টম স্টাইল (Font, Color, Box & Position)",
            step = "STYLE",
            subtitle = "ফন্ট ফ্যামিলি, ফন্ট সাইজ, টেক্সট কালার, ব্যাকগ্রাউন্ড বক্স ও পজিশন কাস্টমাইজ করুন",
            accentColor = TealAccent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ফন্ট ফ্যামিলি (Font Family):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand300
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleFontOption.entries.forEach { fontOpt ->
                        val active = style.fontOption == fontOpt
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) SignalOrange else Ink900)
                                .border(1.dp, if (active) SignalOrange else Ink700, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.updateSubtitleStyle(style.copy(fontOption = fontOpt))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = fontOpt.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (active) Ink950 else Sand100
                            )
                        }
                    }
                }

                StudioSlider(
                    label = "ফন্ট সাইজ (Font Size)",
                    value = style.fontSizeSp,
                    valueRange = 14f..30f,
                    onValueChange = { viewModel.updateSubtitleStyle(style.copy(fontSizeSp = it)) },
                    valueText = "${style.fontSizeSp.toInt()} sp"
                )

                Text(
                    text = "সাবটাইটেল পজিশন (Position):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand300
                )
                StudioSegmentedControl(
                    options = SubtitlePositionOption.entries.map { it to it.label },
                    selected = style.position,
                    onSelect = { viewModel.updateSubtitleStyle(style.copy(position = it)) },
                    accent = TealAccent
                )

                // Text Color Swatches
                val textSwatches = listOf(
                    0xFFF6F1E8L to "ক্রিম সাদা",
                    0xFFFFA23AL to "সিগন্যাল গোল্ড",
                    0xFF2FD4C4L to "সাইবার টিল",
                    0xFFB8E04AL to "লাইম নিওন",
                    0xFFFF6B8BL to "কোরাল পিঙ্ক"
                )
                Text(
                    text = "টেক্সট কালার (Text Color):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand300
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    textSwatches.forEach { (hex, label) ->
                        val isChosen = style.textColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(hex or 0xFF000000L))
                                .border(
                                    width = if (isChosen) 3.dp else 1.dp,
                                    color = if (isChosen) SignalOrange else Ink700,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.updateSubtitleStyle(style.copy(textColorHex = hex)) }
                        )
                    }
                }

                // Background Box Color Swatches + Opacity
                val bgSwatches = listOf(
                    0xFF0B0D10L to "ডার্ক ওনিক্স",
                    0xFF1A103CL to "ডিপ ইন্ডিগো",
                    0xFF0D2828L to "ডার্ক টিল",
                    0xFF2B1216L to "ডার্ক ক্রিমসন"
                )
                Text(
                    text = "ব্যাকগ্রাউন্ড বক্স কালার ও ট্রান্সপারেন্সি:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand300
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    bgSwatches.forEach { (hex, _) ->
                        val isChosen = style.bgColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(hex or 0xFF000000L))
                                .border(
                                    width = if (isChosen) 2.5.dp else 1.dp,
                                    color = if (isChosen) TealAccent else Ink700,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateSubtitleStyle(style.copy(bgColorHex = hex)) }
                        )
                    }
                }

                StudioSlider(
                    label = "ব্যাকগ্রাউন্ড বক্স অপাসিটি (Box Opacity)",
                    value = style.bgAlpha,
                    valueRange = 0.0f..1.0f,
                    onValueChange = { viewModel.updateSubtitleStyle(style.copy(bgAlpha = it)) },
                    valueText = "${(style.bgAlpha * 100).toInt()}%",
                    accent = TealAccent
                )

                StudioToggleRow(
                    label = "অনূদিত সাবটাইটেল প্রাধান্য দিন (Show Translated Primary)",
                    hint = "চালু থাকলে ভিডিওতে অনূদিত ভাষাটি মূল লাইন হিসেবে দেখাবে",
                    checked = style.showTranslated,
                    onCheckedChange = { viewModel.updateSubtitleStyle(style.copy(showTranslated = it)) },
                    accent = SignalOrange
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.exportSubtitles("srt") },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(".SRT ডাউনলোড", color = TealAccent)
                    }
                    OutlinedButton(
                        onClick = { viewModel.exportSubtitles("txt") },
                        border = BorderStroke(1.dp, Ink700),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = LimeAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(".TXT ডাউনলোড", color = LimeAccent)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
