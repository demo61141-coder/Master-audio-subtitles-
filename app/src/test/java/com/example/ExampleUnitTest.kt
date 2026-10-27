package com.example

import com.example.audio.AudioDspEngine
import com.example.audio.GlossaryRule
import com.example.audio.SilenceOptions
import com.example.audio.SubtitleEngine
import com.example.audio.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun noiseProfileAndSpectralSubtraction_preservesAudioLength() {
        val (demo, _) = AudioDspEngine.generateDemoAudio(seconds = 6, sampleRate = 16000)
        val noiseSlice = demo.copyOfRange(0, 4096)
        val profile = AudioDspEngine.learnNoiseProfile(noiseSlice, 16000, 512)
        val cleaned = AudioDspEngine.reduceNoise(demo, 16000, profile)
        assertEquals(demo.size, cleaned.size)
        assertTrue(profile.avgDb < 0f)
    }

    @Test
    fun dualModeSilenceRemoval_trimsPauses() {
        val (demo, _) = AudioDspEngine.generateDemoAudio(seconds = 10, sampleRate = 16000)
        val resDb = AudioDspEngine.removeSilence(
            demo,
            16000,
            SilenceOptions(thresholdDb = -30f, minSilenceSec = 0.25f, keepSec = null)
        )
        assertTrue(resDb.samples.size <= demo.size)
    }

    @Test
    fun subtitleTranscriptionAndGlossaryAndTranslation_worksOffline() {
        val (demo, _) = AudioDspEngine.generateDemoAudio(seconds = 12, sampleRate = 16000)
        val glossary = listOf(
            GlossaryRule(1, "কুবারনেটিস", "Kubernetes", "Tech", "All"),
            GlossaryRule(2, "সাবক্রাফট", "SubCraft AI Studio", "Brand", "bn")
        )
        val subs = SubtitleEngine.transcribeAudioWithGlossary(
            samples = demo,
            sampleRate = 16000,
            language = SupportedLanguage.BENGALI,
            glossary = glossary
        )
        assertTrue(subs.isNotEmpty())
        val translated = SubtitleEngine.translateSubtitles(subs, SupportedLanguage.ENGLISH, glossary)
        assertTrue(translated.first().translatedText.isNotBlank())
        val srt = SubtitleEngine.formatAsSrt(translated, useTranslation = true, bilingual = true)
        assertTrue(srt.contains("-->"))
    }
}
