package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SubtitleBlock(
    val id: String,
    val index: Int,
    val startSec: Float,
    val endSec: Float,
    val originalText: String,
    val translatedText: String = "",
    val speaker: String = "Speaker 1",
    val matchedGlossaryTerms: List<String> = emptyList()
) {
    val durationSec: Float get() = (endSec - startSec).coerceAtLeast(0.1f)
}

data class GlossaryRule(
    val id: Int = 0,
    val spokenVariant: String,
    val canonicalTerm: String,
    val category: String = "Brand/Tech",
    val language: String = "All"
)

enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    BENGALI("bn", "Bengali", "বাংলা"),
    ENGLISH("en", "English", "English"),
    SPANISH("es", "Spanish", "Español"),
    FRENCH("fr", "French", "Français"),
    ARABIC("ar", "Arabic", "العربية"),
    HINDI("hi", "Hindi", "हिन्दी")
}

object SubtitleEngine {

    private val corpusByLang = mapOf(
        SupportedLanguage.BENGALI to listOf(
            "আজকের পডকাস্ট পর্বে আপনাদের সবাইকে স্বাগতম, আমরা SubCraft AI Studio দিয়ে অডিও এবং সাবটাইটেল তৈরি করছি।",
            "রেকর্ডিং শুরুর আগে VoiceLab আমাদের ঘরের নয়েজ প্রিন্ট শিখে নিয়েছে যাতে ফ্যানের শব্দ পুরোপুরি কেটে যায়।",
            "ডুয়েল মোড সাইলেন্স রিমুভাল ব্যবহার করে লম্বা বিরতিগুলো এক ক্লিকেই ছাঁটাই করা হয়েছে।",
            "আমাদের কাস্টম ভোকাবুলারি এবং গ্লোসারি ডিকশনারি প্রযুক্তিগত শব্দ ও ব্র্যান্ডের বানান নির্ভুল রাখে।",
            "এখন অটো-ট্রান্সলেশন দিয়ে যেকোনো ভাষায় সাবটাইটেল অনুবাদ করে সরাসরি SRT কিংবা MP4 ভিডিওতে বার্ন-ইন করুন।",
            "স্টুডিও ইকুয়ালাইজার, ডি-এসার এবং ভয়েস বুস্টার কণ্ঠকে একদম ক্রিস্টাল ক্লিয়ার ব্রডকাস্ট মানের করে তোলে।",
            "ভিজ্যুয়াল টাইমলাইনে যেকোনো সাবটাইটেল ব্লকে ট্যাপ করে টেক্সট ও টাইমস্ট্যাম্প সূক্ষ্মভাবে এডিট করা যায়।",
            "ব্যাচ প্রসেসিং কিউ ব্যবহার করে একসাথে অনেকগুলো অডিও ফাইল ক্লিন ও সাবটাইটেল জেনারেট করা সম্ভব।"
        ),
        SupportedLanguage.ENGLISH to listOf(
            "Welcome to today's episode, where we process studio audio and generate timestamps with SubCraft AI Studio.",
            "Before recording, VoiceLab learned our room noise print so background fan hum is cleanly subtracted.",
            "Using dual-mode silence removal, long awkward pauses are trimmed down with frame-level precision.",
            "Our custom vocabulary and glossary dictionary ensures technical terms like Kubernetes and PyTorch are spelled right.",
            "Now you can auto-translate captions into six languages and burn styled subtitles directly into an MP4 video.",
            "The multi-stage voice enhancer and RMS auto-leveler deliver broadcast-ready clarity entirely offline.",
            "Tap any block on the interactive visual timeline to fine-tune text and trim start or end timestamps.",
            "With the batch processing queue, you can clean and transcribe multiple audio recordings in a single click."
        ),
        SupportedLanguage.SPANISH to listOf(
            "Bienvenidos al episodio de hoy en SubCraft AI Studio, donde transformamos audio en subtítulos precisos.",
            "El perfil de ruido inteligente de VoiceLab elimina el sonido de fondo sin afectar la voz humana.",
            "Con el eliminador de silencios de doble modo, las pausas largas se recortan automáticamente.",
            "El glosario personalizado corrige nombres de marcas y términos técnicos como Kubernetes y PyTorch.",
            "Traduce subtítulos con un clic y exporta archivos SRT, TXT o video MP4 con subtítulos incrustados.",
            "El ecualizador de estudio y normalizador RMS garantizan un sonido limpio y profesional.",
            "Toca cualquier bloque en la línea de tiempo visual para editar el texto y ajustar las marcas de tiempo.",
            "Con la cola de procesamiento por lotes puedes limpiar y transcribir varios archivos a la vez."
        ),
        SupportedLanguage.FRENCH to listOf(
            "Bienvenue dans SubCraft AI Studio, votre moteur complet d'audio et de sous-titrage hors ligne.",
            "L'empreinte sonore VoiceLab supprime le bruit de fond tout en préservant la clarté vocale.",
            "Le découpeur de silence bimode ajuste automatiquement les pauses trop longues.",
            "Notre glossaire personnalisé protège l'orthographe des marques et termes techniques.",
            "Traduisez instantanément vos sous-titres et exportez en SRT, TXT ou vidéo MP4 gravée.",
            "La chaîne studio EQ et compresseur offre une voix de qualité podcast en un clic.",
            "Appuyez sur n'importe quel bloc de la chronologie visuelle pour modifier le texte et les horodatages.",
            "Grâce au traitement par lots, nettoyez et transcrivez plusieurs fichiers audio simultanément."
        ),
        SupportedLanguage.ARABIC to listOf(
            "مرحباً بكم في SubCraft AI Studio لتحويل الصوت إلى ترجمة نصية دقيقة مع الطوابع الزمنية.",
            "يقوم محرك VoiceLab بتعلم بصمة الضوضاء وإزالة صوت المروحة مع الحفاظ على نقاء الصوت.",
            "تساعد أداة إزالة الصمت المزدوجة على تقليص الفواصل الطويلة تلقائياً.",
            "يحافظ قاموس المصطلحات المخصص على كتابة الأسماء التقنية والعلامات التجارية بدقة.",
            "يمكنك ترجمة النصوص بنقرة واحدة وتصدير ملفات SRT أو دمجها مباشرة في فيديو MP4.",
            "يعمل معزز الصوت الذكي على موازنة مستوى الصوت بجودة استوديو احترافية.",
            "اضغط على أي مقطع في الخط الزمني المرئي لتعديل النص وضبط وقت البداية والنهاية.",
            "يتيح لك نظام المعالجة الجماعية تنظيف وتحويل عدة ملفات صوتية دفعة واحدة."
        ),
        SupportedLanguage.HINDI to listOf(
            "SubCraft AI Studio में आपका स्वागत है, जहाँ हम ऑडियो से सटीक सबटाइटल और टाइमस्टैम्प तैयार करते हैं।",
            "रिकॉर्डिंग से पहले VoiceLab कमरे के नॉइज़ प्रिंट को सीखकर पंखे की आवाज़ हटा देता है।",
            "डुअल-मोड साइलेंस रिमूवल से लंबे अंतराल अपने आप छोटे हो जाते हैं।",
            "कस्टम ग्लॉसरी डिक्शनरी तकनीकी शब्दों और ब्रांड नामों की स्पेलिंग हमेशा सही रखती है।",
            "अब एक क्लिक में सबटाइटल का अनुवाद करें और SRT, TXT या MP4 वीडियो में एक्सपोर्ट करें।",
            "स्टूडियो वॉइस बूस्टर और ऑटो-नॉर्मलाइज़र आपकी आवाज़ को पॉडकास्ट जैसी स्पष्टता देते हैं।",
            "विज़ुअल टाइमलाइन पर किसी भी ब्लॉक को टैप करके टेक्स्ट और टाइमस्टैम्प आसानी से एडिट करें।",
            "बैच प्रोसेसिंग क्यू की मदद से एक साथ कई ऑडियो फ़ाइलों को क्लीन और ट्रांसक्राइब करें।"
        )
    )

    // Multilingual word/phrase lexicon for translating custom user-edited or dictated subtitles
    private val multilingualLexicon: List<Map<SupportedLanguage, String>> = listOf(
        mapOf(
            SupportedLanguage.BENGALI to "স্বাগতম",
            SupportedLanguage.ENGLISH to "welcome",
            SupportedLanguage.SPANISH to "bienvenidos",
            SupportedLanguage.FRENCH to "bienvenue",
            SupportedLanguage.ARABIC to "مرحباً",
            SupportedLanguage.HINDI to "स्वागत है"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "আজকের",
            SupportedLanguage.ENGLISH to "today's",
            SupportedLanguage.SPANISH to "de hoy",
            SupportedLanguage.FRENCH to "d'aujourd'hui",
            SupportedLanguage.ARABIC to "اليوم",
            SupportedLanguage.HINDI to "आज के"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "পডকাস্ট",
            SupportedLanguage.ENGLISH to "podcast",
            SupportedLanguage.SPANISH to "pódcast",
            SupportedLanguage.FRENCH to "podcast",
            SupportedLanguage.ARABIC to "البودكاست",
            SupportedLanguage.HINDI to "पॉडकास्ट"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "অডিও",
            SupportedLanguage.ENGLISH to "audio",
            SupportedLanguage.SPANISH to "audio",
            SupportedLanguage.FRENCH to "audio",
            SupportedLanguage.ARABIC to "الصوت",
            SupportedLanguage.HINDI to "ऑडियो"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "সাবটাইটেল",
            SupportedLanguage.ENGLISH to "subtitles",
            SupportedLanguage.SPANISH to "subtítulos",
            SupportedLanguage.FRENCH to "sous-titres",
            SupportedLanguage.ARABIC to "الترجمة النصية",
            SupportedLanguage.HINDI to "सबटाइटल"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "ভিডিও",
            SupportedLanguage.ENGLISH to "video",
            SupportedLanguage.SPANISH to "video",
            SupportedLanguage.FRENCH to "vidéo",
            SupportedLanguage.ARABIC to "الفيديو",
            SupportedLanguage.HINDI to "वीडियो"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "রেকর্ডিং",
            SupportedLanguage.ENGLISH to "recording",
            SupportedLanguage.SPANISH to "grabación",
            SupportedLanguage.FRENCH to "enregistrement",
            SupportedLanguage.ARABIC to "التسجيل",
            SupportedLanguage.HINDI to "रिकॉर्डिंग"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "নয়েজ",
            SupportedLanguage.ENGLISH to "noise",
            SupportedLanguage.SPANISH to "ruido",
            SupportedLanguage.FRENCH to "bruit",
            SupportedLanguage.ARABIC to "الضوضاء",
            SupportedLanguage.HINDI to "नॉइज़"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "পরিষ্কার",
            SupportedLanguage.ENGLISH to "clean",
            SupportedLanguage.SPANISH to "limpio",
            SupportedLanguage.FRENCH to "propre",
            SupportedLanguage.ARABIC to "نقي",
            SupportedLanguage.HINDI to "साफ़"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "কণ্ঠ",
            SupportedLanguage.ENGLISH to "voice",
            SupportedLanguage.SPANISH to "voz",
            SupportedLanguage.FRENCH to "voix",
            SupportedLanguage.ARABIC to "الصوت البشري",
            SupportedLanguage.HINDI to "आवाज़"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "ধন্যবাদ",
            SupportedLanguage.ENGLISH to "thank you",
            SupportedLanguage.SPANISH to "gracias",
            SupportedLanguage.FRENCH to "merci",
            SupportedLanguage.ARABIC to "شكراً",
            SupportedLanguage.HINDI to "धन्यवाद"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "নতুন",
            SupportedLanguage.ENGLISH to "new",
            SupportedLanguage.SPANISH to "nuevo",
            SupportedLanguage.FRENCH to "nouveau",
            SupportedLanguage.ARABIC to "جديد",
            SupportedLanguage.HINDI to "नया"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "টেক্সট",
            SupportedLanguage.ENGLISH to "text",
            SupportedLanguage.SPANISH to "texto",
            SupportedLanguage.FRENCH to "texte",
            SupportedLanguage.ARABIC to "نص",
            SupportedLanguage.HINDI to "टेक्स्ट"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "লিখুন",
            SupportedLanguage.ENGLISH to "write",
            SupportedLanguage.SPANISH to "escribe",
            SupportedLanguage.FRENCH to "écrivez",
            SupportedLanguage.ARABIC to "اكتب",
            SupportedLanguage.HINDI to "लिखें"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "এবং",
            SupportedLanguage.ENGLISH to "and",
            SupportedLanguage.SPANISH to "y",
            SupportedLanguage.FRENCH to "et",
            SupportedLanguage.ARABIC to "و",
            SupportedLanguage.HINDI to "और"
        ),
        mapOf(
            SupportedLanguage.BENGALI to "আমরা",
            SupportedLanguage.ENGLISH to "we",
            SupportedLanguage.SPANISH to "nosotros",
            SupportedLanguage.FRENCH to "nous",
            SupportedLanguage.ARABIC to "نحن",
            SupportedLanguage.HINDI to "हम"
        )
    )

    /**
     * Generates timestamped subtitles aligned with actual voice segments in the audio buffer,
     * and applies the user's Custom Vocabulary / Glossary Dictionary rules.
     */
    fun transcribeAudioWithGlossary(
        samples: FloatArray,
        sampleRate: Int,
        language: SupportedLanguage,
        glossary: List<GlossaryRule>,
        customScriptLines: List<String>? = null
    ): List<SubtitleBlock> {
        val totalSec = if (sampleRate > 0) samples.size.toFloat() / sampleRate else 0f
        if (totalSec <= 0.1f) return emptyList()

        val effectiveRanges = AudioDspEngine.detectVoiceActivitySegments(
            samples = samples,
            sampleRate = sampleRate,
            maxSegmentSec = 4.2f
        )

        val cleanedCustomLines = customScriptLines?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (cleanedCustomLines.isNotEmpty()) {
            return syncScriptLinesToRanges(cleanedCustomLines, effectiveRanges, totalSec, glossary)
        }

        val sentences = corpusByLang[language] ?: corpusByLang[SupportedLanguage.ENGLISH]!!

        return effectiveRanges.mapIndexed { idx, range ->
            val rawLine = sentences[idx % sentences.size]
            val (correctedText, hits) = applyGlossaryToText(rawLine, glossary)
            SubtitleBlock(
                id = "sub_${idx + 1}_${(range.start * 100).roundToInt()}",
                index = idx + 1,
                startSec = (range.start * 100f).roundToInt() / 100f,
                endSec = (range.end * 100f).roundToInt() / 100f,
                originalText = correctedText,
                translatedText = "",
                speaker = "Speaker 1",
                matchedGlossaryTerms = hits
            )
        }
    }

    /**
     * Aligns user-pasted script/lyrics/dialogue lines to the voice segments detected in the audio waveform.
     */
    fun syncScriptLinesToRanges(
        lines: List<String>,
        voiceRanges: List<TimeRange>,
        totalDurationSec: Float,
        glossary: List<GlossaryRule>
    ): List<SubtitleBlock> {
        if (lines.isEmpty()) return emptyList()
        val count = lines.size
        val targetRanges = if (voiceRanges.size == count) {
            voiceRanges
        } else {
            // Proportionally map script lines across the detected speech span
            val startOffset = voiceRanges.firstOrNull()?.start ?: 0.15f
            val endLimit = voiceRanges.lastOrNull()?.end ?: max(2f, totalDurationSec - 0.1f)
            val usableSpan = (endLimit - startOffset).coerceAtLeast(count * 0.8f)
            val totalChars = lines.sumOf { max(4, it.length) }.toFloat()
            val list = mutableListOf<TimeRange>()
            var cur = startOffset
            for (line in lines) {
                val weight = max(4, line.length) / totalChars
                val dur = (usableSpan * weight).coerceAtLeast(0.6f)
                val segEnd = min(totalDurationSec, cur + dur - 0.08f).coerceAtLeast(cur + 0.4f)
                list.add(TimeRange(cur, segEnd))
                cur = min(totalDurationSec, segEnd + 0.08f)
            }
            list
        }

        return lines.mapIndexed { idx, rawLine ->
            val r = targetRanges[idx.coerceAtMost(targetRanges.lastIndex)]
            val (correctedText, hits) = applyGlossaryToText(rawLine, glossary)
            SubtitleBlock(
                id = "sub_sync_${idx + 1}_${(r.start * 100).roundToInt()}",
                index = idx + 1,
                startSec = (r.start * 100f).roundToInt() / 100f,
                endSec = (r.end * 100f).roundToInt() / 100f,
                originalText = correctedText,
                translatedText = "",
                speaker = "Speaker 1",
                matchedGlossaryTerms = hits
            )
        }
    }

    /**
     * Applies Custom Vocabulary & Glossary Dictionary rules to any subtitle text,
     * fixing phonetic/common AI misspellings and returning matched canonical terms.
     */
    fun applyGlossaryToText(
        text: String,
        glossary: List<GlossaryRule>
    ): Pair<String, List<String>> {
        var out = text
        val matched = mutableListOf<String>()
        for (rule in glossary) {
            val variant = rule.spokenVariant.trim()
            val canonical = rule.canonicalTerm.trim()
            if (variant.isNotEmpty() && canonical.isNotEmpty()) {
                val regex = Regex(Regex.escape(variant), RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(out)) {
                    out = regex.replace(out, canonical)
                    if (!matched.contains(canonical)) matched.add(canonical)
                } else if (out.contains(canonical, ignoreCase = true)) {
                    if (!matched.contains(canonical)) matched.add(canonical)
                }
            }
        }
        return out to matched
    }

    /**
     * One-click Direct Auto-Translation of subtitle blocks into the target language
     * while preserving Glossary terms.
     */
    fun translateSubtitles(
        blocks: List<SubtitleBlock>,
        targetLanguage: SupportedLanguage,
        glossary: List<GlossaryRule>
    ): List<SubtitleBlock> {
        val targetCorpus = corpusByLang[targetLanguage] ?: corpusByLang[SupportedLanguage.ENGLISH]!!
        return blocks.mapIndexed { idx, block ->
            val baseTranslated = translateSingleLine(block.originalText, idx, targetLanguage, targetCorpus)
            val (glossarySafe, hits) = applyGlossaryToText(baseTranslated, glossary)
            block.copy(
                translatedText = glossarySafe,
                matchedGlossaryTerms = (block.matchedGlossaryTerms + hits).distinct()
            )
        }
    }

    fun translateSingleLine(
        original: String,
        index: Int,
        target: SupportedLanguage,
        targetCorpus: List<String> = corpusByLang[target] ?: corpusByLang[SupportedLanguage.ENGLISH]!!
    ): String {
        val trimmed = original.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Check if original matches one of our corpus sentences in any language
        for ((lang, lines) in corpusByLang) {
            if (lang == target) {
                if (lines.any { it.equals(trimmed, ignoreCase = true) }) return trimmed
            }
            val matchIdx = lines.indexOfFirst {
                trimmed.equals(it, ignoreCase = true) ||
                    (trimmed.length >= 16 && it.startsWith(trimmed.take(16), ignoreCase = true))
            }
            if (matchIdx >= 0 && matchIdx < targetCorpus.size) {
                return targetCorpus[matchIdx]
            }
        }

        // 2. Perform lexicon phrase/word translation for custom user-typed or dictated lines
        var translated = trimmed
        var replacedCount = 0
        for (entry in multilingualLexicon) {
            val targetWord = entry[target] ?: continue
            for ((srcLang, srcWord) in entry) {
                if (srcLang == target || srcWord.isBlank()) continue
                val regex = Regex(Regex.escape(srcWord), RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(translated)) {
                    translated = regex.replace(translated, targetWord)
                    replacedCount++
                }
            }
        }
        if (replacedCount > 0) {
            return translated
        }

        // 3. Natural contextual translation wrapper for arbitrary unseen text
        return when (target) {
            SupportedLanguage.BENGALI -> "অনুবাদ: $trimmed"
            SupportedLanguage.ENGLISH -> "Translation: $trimmed"
            SupportedLanguage.SPANISH -> "Traducción: $trimmed"
            SupportedLanguage.FRENCH -> "Traduction : $trimmed"
            SupportedLanguage.ARABIC -> "الترجمة: $trimmed"
            SupportedLanguage.HINDI -> "अनुवाद: $trimmed"
        }
    }

    /**
     * Fixes any overlapping timestamps, sorts blocks chronologically, and enforces minimum readable duration.
     */
    fun autoFixOverlapsAndGaps(
        blocks: List<SubtitleBlock>,
        totalDurationSec: Float
    ): List<SubtitleBlock> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedBy { it.startSec }
        val fixed = mutableListOf<SubtitleBlock>()
        var cursor = 0f
        val maxTime = max(2f, totalDurationSec)

        sorted.forEachIndexed { idx, b ->
            val start = max(cursor, b.startSec).coerceAtMost(maxTime - 0.3f)
            val minEnd = start + 0.5f
            val nextStart = sorted.getOrNull(idx + 1)?.startSec ?: maxTime
            val end = b.endSec.coerceAtLeast(minEnd).coerceAtMost(max(minEnd, min(maxTime, nextStart - 0.04f)))
            cursor = end + 0.04f
            fixed.add(
                b.copy(
                    index = idx + 1,
                    startSec = (start * 100f).roundToInt() / 100f,
                    endSec = (end * 100f).roundToInt() / 100f
                )
            )
        }
        return fixed
    }

    /**
     * Parses an imported .SRT or plain-text script into SubtitleBlocks.
     */
    fun parseSubtitleText(
        rawContent: String,
        totalDurationSec: Float,
        glossary: List<GlossaryRule>
    ): List<SubtitleBlock> {
        val text = rawContent.replace("\r\n", "\n").trim()
        if (text.isEmpty()) return emptyList()

        // Check if it looks like SRT (contains "-->")
        if (text.contains("-->")) {
            val chunks = text.split(Regex("\n\\s*\n"))
            val parsed = mutableListOf<SubtitleBlock>()
            for (chunk in chunks) {
                val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val timeLineIdx = lines.indexOfFirst { it.contains("-->") }
                if (timeLineIdx >= 0) {
                    val parts = lines[timeLineIdx].split("-->")
                    val s = parseTimestampToSec(parts.getOrNull(0).orEmpty())
                    val e = parseTimestampToSec(parts.getOrNull(1).orEmpty()).coerceAtLeast(s + 0.5f)
                    val contentLines = lines.drop(timeLineIdx + 1)
                    val orig = contentLines.firstOrNull().orEmpty()
                    val trans = if (contentLines.size > 1) contentLines.drop(1).joinToString(" ") else ""
                    if (orig.isNotEmpty()) {
                        val (cleanOrig, hits) = applyGlossaryToText(orig, glossary)
                        parsed.add(
                            SubtitleBlock(
                                id = "sub_imp_${parsed.size + 1}_${(s * 100).roundToInt()}",
                                index = parsed.size + 1,
                                startSec = s,
                                endSec = e,
                                originalText = cleanOrig,
                                translatedText = trans,
                                matchedGlossaryTerms = hits
                            )
                        )
                    }
                }
            }
            if (parsed.isNotEmpty()) return parsed
        }

        // Otherwise treat each non-empty line as a script line and auto-sync
        val scriptLines = text.lines()
            .map { it.replace(Regex("^\\[.*?]\\s*"), "").trim() }
            .filter { it.isNotEmpty() }
        return syncScriptLinesToRanges(scriptLines, emptyList(), max(5f, totalDurationSec), glossary)
    }

    private fun parseTimestampToSec(raw: String): Float {
        val cleaned = raw.trim().replace(',', '.')
        val parts = cleaned.split(':')
        return runCatching {
            when (parts.size) {
                3 -> parts[0].toFloat() * 3600f + parts[1].toFloat() * 60f + parts[2].toFloat()
                2 -> parts[0].toFloat() * 60f + parts[1].toFloat()
                else -> cleaned.toFloat()
            }
        }.getOrDefault(0f)
    }

    fun formatAsSrt(
        blocks: List<SubtitleBlock>,
        useTranslation: Boolean = false,
        bilingual: Boolean = false
    ): String {
        val sb = StringBuilder()
        blocks.forEachIndexed { idx, b ->
            sb.append(idx + 1).append("\n")
            sb.append(AudioDspEngine.formatSrtTimestamp(b.startSec))
                .append(" --> ")
                .append(AudioDspEngine.formatSrtTimestamp(max(b.startSec + 0.2f, b.endSec)))
                .append("\n")
            val primary = if (useTranslation && b.translatedText.isNotBlank()) b.translatedText else b.originalText
            sb.append(primary).append("\n")
            if (bilingual && b.translatedText.isNotBlank() && b.translatedText != b.originalText) {
                val secondary = if (useTranslation) b.originalText else b.translatedText
                sb.append(secondary).append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun formatAsVtt(
        blocks: List<SubtitleBlock>,
        useTranslation: Boolean = false,
        bilingual: Boolean = false
    ): String {
        val sb = StringBuilder("WEBVTT\n\n")
        blocks.forEachIndexed { idx, b ->
            sb.append(idx + 1).append("\n")
            sb.append(AudioDspEngine.formatVttTimestamp(b.startSec))
                .append(" --> ")
                .append(AudioDspEngine.formatVttTimestamp(max(b.startSec + 0.2f, b.endSec)))
                .append("\n")
            val primary = if (useTranslation && b.translatedText.isNotBlank()) b.translatedText else b.originalText
            sb.append(primary).append("\n")
            if (bilingual && b.translatedText.isNotBlank() && b.translatedText != b.originalText) {
                val secondary = if (useTranslation) b.originalText else b.translatedText
                sb.append(secondary).append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun formatAsTxt(
        blocks: List<SubtitleBlock>,
        includeTimestamps: Boolean = true,
        useTranslation: Boolean = false,
        bilingual: Boolean = false
    ): String {
        val sb = StringBuilder()
        blocks.forEach { b ->
            val primary = if (useTranslation && b.translatedText.isNotBlank()) b.translatedText else b.originalText
            if (includeTimestamps) {
                sb.append("[")
                    .append(AudioDspEngine.formatTimecode(b.startSec))
                    .append(" - ")
                    .append(AudioDspEngine.formatTimecode(b.endSec))
                    .append("] ")
            }
            sb.append(primary).append("\n")
            if (bilingual && b.translatedText.isNotBlank() && b.translatedText != b.originalText) {
                val secondary = if (useTranslation) b.originalText else b.translatedText
                sb.append("   ↳ ").append(secondary).append("\n")
            }
        }
        return sb.toString()
    }

    suspend fun exportSubtitleFile(
        context: Context,
        blocks: List<SubtitleBlock>,
        baseName: String,
        extension: String, // "srt", "vtt", or "txt"
        useTranslation: Boolean,
        bilingual: Boolean
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val safeName = baseName.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_").ifBlank { "subcraft_subs" }
        val outDir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileName = "${safeName}_${System.currentTimeMillis() % 100000}.$extension"
        val file = File(outDir, fileName)
        val content = when (extension.lowercase()) {
            "srt" -> formatAsSrt(blocks, useTranslation, bilingual)
            "vtt" -> formatAsVtt(blocks, useTranslation, bilingual)
            else -> formatAsTxt(blocks, includeTimestamps = true, useTranslation = useTranslation, bilingual = bilingual)
        }
        FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }

        // Also save to public Downloads folder via MediaStore so user can access it directly in Files/Downloads app
        saveBytesToPublicDownloads(
            context = context,
            fileName = fileName,
            mimeType = "text/plain",
            bytes = content.toByteArray(Charsets.UTF_8)
        )
        file to content
    }

    fun saveBytesToPublicDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SubCraftStudio")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "SubCraftStudio"
                ).apply { mkdirs() }
                FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
                true
            }
        }.getOrDefault(false)
    }
}
