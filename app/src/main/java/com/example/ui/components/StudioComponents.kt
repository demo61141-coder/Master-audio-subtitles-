package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioDspEngine
import com.example.audio.AudioExportFormat
import com.example.audio.SubtitleBlock
import com.example.audio.TimeRange
import com.example.ui.theme.CopperOrange
import com.example.ui.theme.Ink600
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink800
import com.example.ui.theme.Ink850
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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun StudioPanel(
    title: String,
    modifier: Modifier = Modifier,
    step: String? = null,
    subtitle: String? = null,
    accentColor: Color = SignalOrange,
    headerRight: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Ink850),
        border = BorderStroke(1.dp, Ink700)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (step != null) {
                            Text(
                                text = step,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier
                                    .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Sand50,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (headerRight != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        headerRight()
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sand300
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = Ink700.copy(alpha = 0.7f)
                )
                content()
            }
        }
    }
}

@Composable
fun StatBadgeCard(
    label: String,
    value: String,
    unit: String = "",
    accent: Color = Sand50,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Ink900, RoundedCornerShape(10.dp))
            .border(1.dp, Ink700, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Sand500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = accent,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Sand500
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color = SignalOrange,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun <T> StudioSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = SignalOrange
) {
    Row(
        modifier = modifier
            .background(Ink900, RoundedCornerShape(10.dp))
            .border(1.dp, Ink700, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) Ink950 else Sand300,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StudioSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueText: String,
    hint: String? = null,
    accent: Color = SignalOrange,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Sand100 else Sand500
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) accent else Sand500
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Ink700
            ),
            modifier = Modifier.height(28.dp)
        )
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = Sand500
            )
        }
    }
}

@Composable
fun StudioToggleRow(
    label: String,
    hint: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accent: Color = SignalOrange
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) Sand50 else Sand500
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sand500
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink950,
                checkedTrackColor = accent,
                uncheckedThumbColor = Sand300,
                uncheckedTrackColor = Ink800
            )
        )
    }
}

/**
 * Interactive Waveform Canvas with selection drag, tap-to-seek, silence/chunk/subtitle overlays.
 */
@Composable
fun InteractiveWaveformCanvas(
    samples: FloatArray,
    sampleRate: Int,
    viewRange: TimeRange,
    playheadSec: Float,
    selection: TimeRange?,
    onSeek: (Float) -> Unit,
    onSelectionChange: (TimeRange?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 156.dp,
    silenceRegions: List<TimeRange> = emptyList(),
    chunkRegions: List<TimeRange> = emptyList(),
    subtitleBlocks: List<SubtitleBlock> = emptyList(),
    waveColor: Color = SignalOrange
) {
    val totalSec = if (sampleRate > 0) samples.size.toFloat() / sampleRate else 0f
    var dragStartX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink950)
            .border(1.dp, Ink700, RoundedCornerShape(10.dp))
            .pointerInput(totalSec, viewRange) {
                detectTapGestures(
                    onDoubleTap = { onSelectionChange(null) },
                    onTap = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val sec = viewRange.start + (offset.x / w) * viewRange.duration
                        onSeek(sec.coerceIn(0f, totalSec))
                    }
                )
            }
            .pointerInput(totalSec, viewRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                    },
                    onDrag = { change, _ ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val x0 = min(dragStartX, change.position.x).coerceIn(0f, w)
                        val x1 = max(dragStartX, change.position.x).coerceIn(0f, w)
                        val s0 = (viewRange.start + (x0 / w) * viewRange.duration).coerceIn(0f, totalSec)
                        val s1 = (viewRange.start + (x1 / w) * viewRange.duration).coerceIn(0f, totalSec)
                        if (s1 - s0 > 0.05f) {
                            onSelectionChange(TimeRange(s0, s1))
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f
            val spanSec = viewRange.duration.coerceAtLeast(0.05f)

            fun secToX(sec: Float): Float = ((sec - viewRange.start) / spanSec) * w

            // Horizontal dB guide lines
            for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, h * frac),
                    end = Offset(w, h * frac),
                    strokeWidth = 1f
                )
            }

            // Silence regions (Red tint)
            for (r in silenceRegions) {
                val x0 = secToX(r.start).coerceIn(0f, w)
                val x1 = secToX(r.end).coerceIn(0f, w)
                if (x1 > x0) {
                    drawRect(
                        color = RecRed.copy(alpha = 0.20f),
                        topLeft = Offset(x0, 0f),
                        size = Size(x1 - x0, h)
                    )
                }
            }

            // Auto/Manual split chunk markers (Teal tint)
            for (r in chunkRegions) {
                val x0 = secToX(r.start).coerceIn(0f, w)
                val x1 = secToX(r.end).coerceIn(0f, w)
                if (x1 > x0) {
                    drawRect(
                        color = TealAccent.copy(alpha = 0.12f),
                        topLeft = Offset(x0, 0f),
                        size = Size(x1 - x0, h)
                    )
                    drawLine(
                        color = TealAccent.copy(alpha = 0.65f),
                        start = Offset(x0, 0f),
                        end = Offset(x0, h),
                        strokeWidth = 1.5f
                    )
                }
            }

            // Subtitle block markers along bottom strip
            for (sub in subtitleBlocks) {
                val x0 = secToX(sub.startSec).coerceIn(0f, w)
                val x1 = secToX(sub.endSec).coerceIn(0f, w)
                if (x1 > x0 + 2f) {
                    drawRoundRect(
                        color = VioletAccent.copy(alpha = 0.28f),
                        topLeft = Offset(x0, h - 18f),
                        size = Size(x1 - x0, 14f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // Waveform bars
            if (samples.isNotEmpty() && sampleRate > 0) {
                val startSample = (viewRange.start * sampleRate).roundToInt().coerceIn(0, samples.lastIndex)
                val endSample = (viewRange.end * sampleRate).roundToInt().coerceIn(startSample + 1, samples.size)
                val columns = 120
                val colWidth = w / columns
                val samplesPerCol = max(1, (endSample - startSample) / columns)

                for (c in 0 until columns) {
                    val s0 = startSample + c * samplesPerCol
                    val s1 = min(endSample, s0 + samplesPerCol)
                    var peak = 0f
                    val step = max(1, (s1 - s0) / 16)
                    var i = s0
                    while (i < s1) {
                        val a = abs(samples[i])
                        if (a > peak) peak = a
                        i += step
                    }
                    val barHalf = (peak * (h * 0.44f)).coerceAtLeast(2f)
                    val x = c * colWidth + colWidth * 0.15f
                    drawRoundRect(
                        color = waveColor,
                        topLeft = Offset(x, midY - barHalf),
                        size = Size(colWidth * 0.7f, barHalf * 2f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }

            // Active selection overlay
            if (selection != null) {
                val x0 = secToX(selection.start).coerceIn(0f, w)
                val x1 = secToX(selection.end).coerceIn(0f, w)
                if (x1 > x0) {
                    drawRect(
                        color = TealAccent.copy(alpha = 0.22f),
                        topLeft = Offset(x0, 0f),
                        size = Size(x1 - x0, h)
                    )
                    drawLine(TealAccent, Offset(x0, 0f), Offset(x0, h), strokeWidth = 2.5f)
                    drawLine(TealAccent, Offset(x1, 0f), Offset(x1, h), strokeWidth = 2.5f)
                }
            }

            // Playhead cursor
            val playX = secToX(playheadSec)
            if (playX in 0f..w) {
                drawLine(
                    color = Color.White,
                    start = Offset(playX, 0f),
                    end = Offset(playX, h),
                    strokeWidth = 2.5f
                )
                drawCircle(
                    color = SignalOrange,
                    radius = 5.dp.toPx(),
                    center = Offset(playX, 8.dp.toPx())
                )
            }
        }

        // Top overlay info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${AudioDspEngine.formatTimecode(viewRange.start, false)} – ${AudioDspEngine.formatTimecode(viewRange.end, false)}",
                style = MaterialTheme.typography.labelSmall,
                color = Sand500
            )
            Text(
                text = if (selection != null) {
                    "সিলেকশন: ${AudioDspEngine.formatTimecode(selection.start)} → ${AudioDspEngine.formatTimecode(selection.end)}"
                } else {
                    "ড্র্যাগ = সিলেক্ট · ট্যাপ = সিক · ডাবল-ট্যাপ = রিসেট"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (selection != null) TealAccent else Sand500
            )
        }
    }
}

@Composable
fun SpectrumBarsCanvas(
    magnitudes: FloatArray,
    sampleRate: Int,
    fftSize: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Ink950)
            .border(1.dp, Ink700, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bars = 36
            val bw = w / bars
            var peak = 1e-6f
            for (m in magnitudes) if (m > peak) peak = m

            for (i in 0 until bars) {
                val f0 = 60.0 * 200.0.pow(i.toDouble() / bars)
                val f1 = 60.0 * 200.0.pow((i + 1).toDouble() / bars)
                val k0 = ((f0 * fftSize) / sampleRate).toInt().coerceIn(0, magnitudes.lastIndex)
                val k1 = ((f1 * fftSize) / sampleRate).toInt().coerceIn(k0 + 1, magnitudes.size)
                var sum = 0f
                for (k in k0 until k1) sum += magnitudes[k]
                val avg = sum / max(1, k1 - k0)
                val barH = ((avg / peak) * (h - 6f)).coerceIn(3f, h)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(SignalOrange, CopperOrange.copy(alpha = 0.45f))),
                    topLeft = Offset(i * bw + 1.5f, h - barH),
                    size = Size(bw - 3f, barH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
        Text(
            text = "LEARNED NOISE SPECTRUM (60Hz – 12kHz)",
            style = MaterialTheme.typography.labelSmall,
            color = Sand300,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
fun LiveScopeAndMeter(
    scopeValues: FloatArray,
    db: Float,
    peakDb: Float,
    thresholdDb: Float?,
    gateOpen: Boolean,
    isActive: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Oscilloscope
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink950)
                .border(1.dp, Ink700, RoundedCornerShape(10.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val mid = h / 2f
                drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, mid), Offset(w, mid), 1f)
                if (scopeValues.isNotEmpty()) {
                    val path = Path()
                    val stepX = w / max(1, scopeValues.size - 1)
                    scopeValues.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = mid - (v.coerceIn(-1f, 1f) * (h * 0.42f))
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = if (isActive) SignalOrange else Sand500,
                        style = Stroke(width = 2.2f)
                    )
                }
            }
            Text(
                text = if (isActive) "LIVE PCM STREAM" else "STANDBY SCOPE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) SignalOrange else Sand500,
                modifier = Modifier.padding(8.dp)
            )
        }

        // Level Bar
        val segments = 30
        val minDb = -60f
        val activeSegs = (((db.coerceIn(minDb, 0f) - minDb) / -minDb) * segments).roundToInt()
        val gateSeg = thresholdDb?.let { (((it.coerceIn(minDb, 0f) - minDb) / -minDb) * segments).roundToInt() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 0 until segments) {
                val on = i < activeSegs
                val isGateLine = gateSeg != null && i == gateSeg
                val segColor = when {
                    isGateLine -> LimeAccent
                    !on -> Ink700
                    i > segments * 0.88f -> RecRed
                    i > segments * 0.70f -> SignalOrange
                    else -> TealAccent
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(segColor)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-60 dB", style = MaterialTheme.typography.labelSmall, color = Sand500)
            Text(
                text = if (gateOpen) "গেট খোলা (${"%.1f".format(db)} dB)" else "সাইলেন্স স্কিপ সক্রিয়",
                style = MaterialTheme.typography.labelSmall,
                color = if (gateOpen) LimeAccent else RecRed
            )
            Text("Peak ${"%.1f".format(peakDb)} dB", style = MaterialTheme.typography.labelSmall, color = Sand300)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFormatExportModal(
    projectTitle: String,
    durationSec: Float,
    sampleRate: Int,
    onDismiss: () -> Unit,
    onSelectFormat: (AudioExportFormat) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Ink850,
        contentColor = Sand50
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "অডিও ফরম্যাট সেভ ও এক্সপোর্ট",
                        style = MaterialTheme.typography.titleLarge,
                        color = Sand50
                    )
                    Text(
                        text = "$projectTitle · ${AudioDspEngine.formatTimecode(durationSec)} · ${sampleRate / 1000.0} kHz",
                        style = MaterialTheme.typography.labelMedium,
                        color = TealAccent
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Sand300)
                }
            }

            AudioExportFormat.entries.forEach { format ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_format_${format.id}")
                        .clickable { onSelectFormat(format) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Ink900),
                    border = BorderStroke(1.dp, Ink700)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(SignalOrange.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = format.label,
                                    tint = SignalOrange
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = format.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Sand50
                                )
                                Text(
                                    text = format.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand300
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSelectFormat(format) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalOrange,
                                contentColor = Ink950
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(".${format.extension.uppercase()}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
