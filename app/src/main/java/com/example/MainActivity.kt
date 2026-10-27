package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AudioDspEngine
import com.example.ui.components.AudioFormatExportModal
import com.example.ui.screens.AudioEditorAndEffectsScreen
import com.example.ui.screens.GlossaryAndHubScreen
import com.example.ui.screens.RecorderAndBatchScreen
import com.example.ui.screens.SubtitleTimelineScreen
import com.example.ui.screens.VideoBurnInScreen
import com.example.ui.theme.CopperOrange
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink800
import com.example.ui.theme.Ink850
import com.example.ui.theme.Ink900
import com.example.ui.theme.Ink950
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Sand100
import com.example.ui.theme.Sand300
import com.example.ui.theme.Sand50
import com.example.ui.theme.Sand500
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TealAccent
import com.example.viewmodel.StudioTab
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SubCraftVoiceLabApp()
            }
        }
    }
}

private data class NavTabItem(
    val tab: StudioTab,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun SubCraftVoiceLabApp(
    studioViewModel: StudioViewModel = viewModel()
) {
    val uiState by studioViewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by studioViewModel.savedProjects.collectAsStateWithLifecycle()
    val glossaryEntries by studioViewModel.glossaryEntries.collectAsStateWithLifecycle()
    val batchItems by studioViewModel.batchItems.collectAsStateWithLifecycle()
    val recentLogs by studioViewModel.recentLogs.collectAsStateWithLifecycle()

    // Auto-dismiss toast after 3.8s
    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(3800)
            studioViewModel.clearToast()
        }
    }

    // BackHandler returns from secondary tabs to RECORD home tab
    if (uiState.activeTab != StudioTab.RECORD) {
        BackHandler {
            studioViewModel.selectTab(StudioTab.RECORD)
        }
    }

    val navItems = listOf(
        NavTabItem(StudioTab.RECORD, "রেকর্ড", Icons.Default.Mic, "nav_tab_record"),
        NavTabItem(StudioTab.EDITOR, "এডিটর", Icons.Default.GraphicEq, "nav_tab_editor"),
        NavTabItem(StudioTab.SUBTITLES, "সাবটাইটেল", Icons.Default.Subtitles, "nav_tab_subtitles"),
        NavTabItem(StudioTab.VIDEO_BURN, "ভিডিও বার্ন", Icons.Default.MovieCreation, "nav_tab_video"),
        NavTabItem(StudioTab.HUB, "গ্লোসারি", Icons.Default.Book, "nav_tab_hub")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Ink950,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            StudioTopHeader(
                projectTitle = uiState.projectTitle,
                durationSec = uiState.analysis.durationSec,
                sampleRate = uiState.sampleRate,
                subtitleCount = uiState.subtitles.size,
                hasNoisePrint = uiState.noiseProfile != null,
                onOpenAudioSaveDialog = { studioViewModel.setShowAudioFormatDialog(true) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Ink900,
                tonalElevation = 8.dp
            ) {
                navItems.forEach { item ->
                    val selected = uiState.activeTab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { studioViewModel.selectTab(item.tab) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink950,
                            selectedTextColor = SignalOrange,
                            indicatorColor = SignalOrange,
                            unselectedIconColor = Sand300,
                            unselectedTextColor = Sand500
                        ),
                        modifier = Modifier.testTag(item.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                StudioTab.RECORD -> RecorderAndBatchScreen(
                    uiState = uiState,
                    batchItems = batchItems,
                    viewModel = studioViewModel
                )
                StudioTab.EDITOR -> AudioEditorAndEffectsScreen(
                    uiState = uiState,
                    viewModel = studioViewModel
                )
                StudioTab.SUBTITLES -> SubtitleTimelineScreen(
                    uiState = uiState,
                    viewModel = studioViewModel
                )
                StudioTab.VIDEO_BURN -> VideoBurnInScreen(
                    uiState = uiState,
                    viewModel = studioViewModel
                )
                StudioTab.HUB -> GlossaryAndHubScreen(
                    uiState = uiState,
                    glossaryEntries = glossaryEntries,
                    savedProjects = savedProjects,
                    recentLogs = recentLogs,
                    viewModel = studioViewModel
                )
            }

            // Toast Notification Banner
            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    color = Ink800,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TealAccent),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(LimeAccent, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.toastMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Sand50
                        )
                    }
                }
            }

            // Busy DSP/Encoding Overlay
            if (uiState.busyMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ink950.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Ink850,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SignalOrange)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = SignalOrange
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = uiState.busyMessage.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Sand50
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAudioFormatDialog) {
        AudioFormatExportModal(
            projectTitle = uiState.projectTitle,
            durationSec = uiState.analysis.durationSec,
            sampleRate = uiState.sampleRate,
            onDismiss = { studioViewModel.setShowAudioFormatDialog(false) },
            onSelectFormat = { format ->
                studioViewModel.saveAndExportAudioFormat(format, shareAfterSave = true)
            }
        )
    }
}

@Composable
private fun StudioTopHeader(
    projectTitle: String,
    durationSec: Float,
    sampleRate: Int,
    subtitleCount: Int,
    hasNoisePrint: Boolean,
    onOpenAudioSaveDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink900)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand mark + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(listOf(SignalOrange, CopperOrange))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "SubCraft Studio",
                        tint = Ink950,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SUBCRAFT",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Sand50
                        )
                        Text(
                            text = " · ",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SignalOrange
                        )
                        Text(
                            text = "VOICELAB",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TealAccent
                        )
                    }
                    Text(
                        text = "$projectTitle · ${AudioDspEngine.formatTimecode(durationSec, false)} · $subtitleCount Subs" +
                            if (hasNoisePrint) " · Print OK" else "",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = Sand300,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Audio Format Save Button
            Button(
                onClick = onOpenAudioSaveDialog,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalOrange,
                    contentColor = Ink950
                ),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.testTag("top_save_audio_format_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Audio Format",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "অডিও সেভ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Ink700)
        )
    }
}
