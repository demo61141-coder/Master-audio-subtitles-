package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Spellcheck
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioDspEngine
import com.example.data.GlossaryEntryEntity
import com.example.data.ProcessingLogEntity
import com.example.data.ProjectEntity
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioPanel
import com.example.ui.theme.Ink700
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

@Composable
fun GlossaryAndHubScreen(
    uiState: StudioUiState,
    glossaryEntries: List<GlossaryEntryEntity>,
    savedProjects: List<ProjectEntity>,
    recentLogs: List<ProcessingLogEntity>,
    viewModel: StudioViewModel
) {
    var spokenInput by remember { mutableStateOf("") }
    var canonicalInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Brand/Tech") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Panel 1: Custom Vocabulary & Glossary Dictionary (Feature 5)
        StudioPanel(
            title = "কাস্টম ভোকাবুলারি ও গ্লোসারি ডিকশনারি",
            step = "FEAT 5",
            subtitle = "দুর্লভ শব্দ, টেকনিক্যাল টার্ম, নাম বা ব্র্যান্ডের সঠিক বানান নির্ধারণ করুন যাতে AI ট্রান্সক্রিপশনে ভুল না হয়",
            accentColor = LimeAccent,
            headerRight = {
                StatusPill(text = "${glossaryEntries.size} শব্দ", color = LimeAccent)
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = spokenInput,
                        onValueChange = { spokenInput = it },
                        label = { Text("উচ্চারিত/ভুল বানান") },
                        placeholder = { Text("যেমন: কুবারনেটিস") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("glossary_spoken_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LimeAccent,
                            unfocusedBorderColor = Ink700
                        )
                    )
                    OutlinedTextField(
                        value = canonicalInput,
                        onValueChange = { canonicalInput = it },
                        label = { Text("সঠিক বানান (Correct)") },
                        placeholder = { Text("যেমন: Kubernetes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("glossary_canonical_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Ink700
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.addGlossaryTerm(spokenInput, canonicalInput, categoryInput, "All")
                            spokenInput = ""
                            canonicalInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LimeAccent, contentColor = Ink950),
                        modifier = Modifier.weight(1f).testTag("glossary_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("গ্লোসারিতে যুক্ত করুন", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.applyGlossaryToCurrentSubtitles() },
                        border = BorderStroke(1.dp, TealAccent),
                        modifier = Modifier.weight(1f).testTag("glossary_apply_button")
                    ) {
                        Icon(Icons.Default.Spellcheck, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সাবটাইটেলে প্রয়োগ", color = TealAccent)
                    }
                }

                glossaryEntries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Ink900, RoundedCornerShape(10.dp))
                            .border(1.dp, Ink700, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.spokenVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand300
                                )
                                Text(
                                    text = "  →  ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SignalOrange
                                )
                                Text(
                                    text = entry.canonicalTerm,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = LimeAccent
                                )
                            }
                            Text(
                                text = "ক্যাটাগরি: ${entry.category} · ভাষা: ${entry.language}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Sand500
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteGlossaryTerm(entry.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete term", tint = RecRed)
                        }
                    }
                }
            }
        }

        // Panel 2: Saved Projects & Multi-Format Audio Library
        StudioPanel(
            title = "সংরক্ষিত প্রজেক্ট ও অডিও ফরম্যাট লাইব্রেরি",
            step = "LIBRARY",
            subtitle = "অফলাইনে সংরক্ষিত আপনার সব অডিও মাস্টার (WAV 16/24-bit, M4A/AAC) ও সাবটাইটেল প্রজেক্ট",
            accentColor = SignalOrange,
            headerRight = {
                Button(
                    onClick = { viewModel.setShowAudioFormatDialog(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = Ink950)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("বর্তমান প্রজেক্ট সেভ", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (savedProjects.isEmpty()) {
                    Text(
                        text = "এখনো কোনো প্রজেক্ট সেভ করা হয়নি — উপরের 'বর্তমান প্রজেক্ট সেভ' বাটনে ট্যাপ করে WAV বা M4A ফরম্যাটে সেভ করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sand500
                    )
                } else {
                    savedProjects.forEach { proj ->
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
                                    text = proj.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Sand50
                                )
                                Text(
                                    text = "${AudioDspEngine.formatTimecode(proj.durationSec)} · ${proj.sampleRate / 1000.0}kHz · Peak ${"%.1f".format(proj.peakDb)}dB · ${proj.sourceLanguage.uppercase()}→${proj.targetLanguage.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealAccent
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.loadProjectFromLibrary(proj) },
                                    border = BorderStroke(1.dp, TealAccent)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("খুলুন", color = TealAccent)
                                }
                                IconButton(onClick = { viewModel.deleteSavedProject(proj.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete project", tint = RecRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Panel 3: Processing Timeline Logs
        StudioPanel(
            title = "ডিএসপি ও সাবটাইটেল অপারেশন টাইমলাইন",
            step = "LOGS",
            subtitle = "নয়েজ প্রিন্ট, সাইলেন্স কাট, ইফেক্টস ও সাবটাইটেল এক্সপোর্টের সম্পূর্ণ লগ",
            accentColor = TealAccent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recentLogs.isEmpty()) {
                    Text(
                        text = "যেকোনো অডিও এডিট বা সাবটাইটেল অপারেশন চালালে এখানে টাইমলাইন লগ দেখা যাবে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sand500
                    )
                } else {
                    recentLogs.take(15).forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Ink900, RoundedCornerShape(8.dp))
                                .border(1.dp, Ink700, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Sand50
                                )
                                Text(
                                    text = log.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Sand300
                                )
                            }
                            StatusPill(text = log.operation, color = SignalOrange)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
