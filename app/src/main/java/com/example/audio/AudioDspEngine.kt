package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

data class TimeRange(val start: Float, val end: Float) {
    val duration: Float get() = (end - start).coerceAtLeast(0f)
}

data class NoiseProfile(
    val sampleRate: Int,
    val fftSize: Int,
    val learnedSec: Float,
    val avgDb: Float,
    val peakDb: Float,
    val magnitudes: FloatArray,
    val floor: FloatArray
)

data class NoiseReduceOptions(
    val strength: Float = 2.0f,
    val floorDb: Float = -32f,
    val smoothing: Float = 0.6f
)

data class SilenceOptions(
    val thresholdDb: Float = -45f,
    val minSilenceSec: Float = 0.4f,
    val keepSec: Float? = null,
    val padMs: Int = 20
)

data class SilenceResult(
    val samples: FloatArray,
    val removed: List<TimeRange>,
    val kept: List<TimeRange>,
    val removedSec: Float
)

data class SplitOptions(
    val thresholdDb: Float = -42f,
    val minSilenceSec: Float = 0.3f,
    val minChunkSec: Float = 0.5f,
    val maxChunks: Int = 24
)

data class AutoLevelOptions(
    val targetDb: Float = -18f,
    val windowMs: Int = 60,
    val maxBoostDb: Float = 18f,
    val maxCutDb: Float = 12f,
    val smoothing: Float = 0.75f,
    val noiseGateDb: Float = -55f
)

data class AudioAnalysis(
    val durationSec: Float,
    val peakDb: Float,
    val rmsDb: Float,
    val dynamicRangeDb: Float,
    val silenceSec: Float,
    val clipped: Int,
    val noiseFloorDb: Float
)

data class EnhanceConfig(
    val highpassHz: Float = 80f,
    val lowpassHz: Float = 15000f,
    val warmthHz: Float = 180f,
    val warmthGainDb: Float = 1.5f,
    val presenceHz: Float = 3200f,
    val presenceGainDb: Float = 3.0f,
    val presenceQ: Float = 0.9f,
    val airHz: Float = 11000f,
    val airGainDb: Float = 2.0f,
    val deEsserHz: Float = 6800f,
    val deEsserGainDb: Float = -3.0f,
    val deEsserQ: Float = 2.4f,
    val compThresholdDb: Float = -22f,
    val compRatio: Float = 4.0f,
    val compAttack: Float = 0.006f,
    val compRelease: Float = 0.18f,
    val makeupGainDb: Float = 4.0f,
    val limiterDrive: Float = 1.15f,
    val levelAmount: Float = 0.7f
)

data class EnhancePreset(
    val id: String,
    val name: String,
    val tag: String,
    val description: String,
    val config: EnhanceConfig
)

object AudioDspEngine {

    val enhancePresets = listOf(
        EnhancePreset(
            id = "podcast-studio",
            name = "পডকাস্ট স্টুডিও (Podcast)",
            tag = "AI VOICE",
            description = "ঘরে বসা রেকর্ডিংকে ব্রডকাস্ট পডকাস্ট টোনে বদলে দেয় — উষ্ণ বডি, স্পষ্ট উপস্থিতি, সমান লাউডনেস।",
            config = EnhanceConfig()
        ),
        EnhancePreset(
            id = "crystal-clear",
            name = "ক্রিস্টাল ক্লিয়ার (Clarity)",
            tag = "CLARITY",
            description = "কনসোন্যান্ট ও ডিকশন তীক্ষ্ণ করে, হিস-হিস শব্দ কমিয়ে একদম পরিচ্ছন্ন কণ্ঠ দেয়।",
            config = EnhanceConfig(
                highpassHz = 100f,
                presenceHz = 4200f,
                presenceGainDb = 4.5f,
                airHz = 12500f,
                airGainDb = 3.5f,
                deEsserGainDb = -4.5f,
                compRatio = 5f,
                compThresholdDb = -24f,
                warmthGainDb = 0.5f
            )
        ),
        EnhancePreset(
            id = "radio-broadcast",
            name = "রেডিও ব্রডকাস্ট (Broadcast)",
            tag = "LOUD",
            description = "ভারী কম্প্রেশন ও লিমিটার — এফএম/ইউটিউবের জন্য একদম ঘন, লাউড ভোকাল।",
            config = EnhanceConfig(
                compThresholdDb = -28f,
                compRatio = 8f,
                compAttack = 0.003f,
                compRelease = 0.12f,
                makeupGainDb = 7f,
                limiterDrive = 1.4f,
                presenceGainDb = 3.5f,
                levelAmount = 0.9f
            )
        ),
        EnhancePreset(
            id = "phone-clean",
            name = "ফোন কল ক্লিনআপ (Rescue)",
            tag = "RESCUE",
            description = "মোবাইল/কল রেকর্ডিংয়ের বুকুনো আওয়াজ, হাম ও কম ভলিউম ঠিক করে বোধগম্য কণ্ঠ ফেরায়।",
            config = EnhanceConfig(
                highpassHz = 140f,
                lowpassHz = 9000f,
                warmthHz = 240f,
                warmthGainDb = 2.5f,
                presenceHz = 2600f,
                presenceGainDb = 5f,
                airGainDb = 0f,
                compThresholdDb = -26f,
                compRatio = 6f,
                makeupGainDb = 8f
            )
        ),
        EnhancePreset(
            id = "interview-natural",
            name = "ইন্টারভিউ ন্যাচারাল (Natural)",
            tag = "NATURAL",
            description = "স্টুডিও ইন্টারভিউয়ের মতো প্রাকৃতিক টিম্বার — হালকা EQ, নরম কম্প্রেশন।",
            config = EnhanceConfig(
                presenceGainDb = 2f,
                airGainDb = 1.5f,
                compRatio = 2.6f,
                compThresholdDb = -20f,
                makeupGainDb = 2.5f,
                limiterDrive = 1.05f,
                levelAmount = 0.4f
            )
        ),
        EnhancePreset(
            id = "voiceover-warm",
            name = "ভয়েসওভার ওয়ার্ম (Cinema)",
            tag = "DUBBING",
            description = "ডাবিং/গল্প বলার জন্য বুকে-রেজোন্যান্স বাড়ানো গভীর, সিনেমাটিক কণ্ঠ।",
            config = EnhanceConfig(
                warmthHz = 150f,
                warmthGainDb = 4f,
                presenceHz = 2900f,
                presenceGainDb = 2.5f,
                airHz = 10000f,
                airGainDb = 1.5f,
                compRatio = 3.4f,
                makeupGainDb = 3.5f
            )
        ),
        EnhancePreset(
            id = "bypass",
            name = "বাইপাস (Original Ref)",
            tag = "REF",
            description = "কোনো প্রসেসিং ছাড়াই মূল সিগন্যাল — A/B তুলনার জন্য।",
            config = EnhanceConfig(
                highpassHz = 20f,
                lowpassHz = 20000f,
                warmthGainDb = 0f,
                presenceGainDb = 0f,
                airGainDb = 0f,
                deEsserGainDb = 0f,
                compThresholdDb = 0f,
                compRatio = 1f,
                makeupGainDb = 0f,
                limiterDrive = 1f,
                levelAmount = 0f
            )
        )
    )

    fun linToDb(v: Float): Float = if (v <= 1e-9f) -180f else (20.0 * log10(v.toDouble())).toFloat()
    fun dbToLin(db: Float): Float = 10.0.pow((db / 20.0)).toFloat()

    fun peakLevel(samples: FloatArray): Float {
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        return peak
    }

    fun rmsLevel(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val len = (end - start).coerceAtLeast(0)
        if (len == 0) return 0f
        var sum = 0.0
        for (i in start until end) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / len).toFloat()
    }

    fun peakDb(samples: FloatArray): Float = linToDb(peakLevel(samples))
    fun rmsDb(samples: FloatArray): Float = linToDb(rmsLevel(samples))

    fun envelopeDb(samples: FloatArray, sampleRate: Int, hopMs: Int = 10): Pair<FloatArray, Float> {
        val hop = max(64, ((sampleRate * hopMs) / 1000.0).roundToInt())
        val count = max(1, ceil(samples.size.toDouble() / hop).toInt())
        val values = FloatArray(count)
        for (b in 0 until count) {
            val off = b * hop
            val end = min(samples.size, off + hop)
            val rms = rmsLevel(samples, off, end)
            values[b] = linToDb(rms)
        }
        return values to (hop.toFloat() / sampleRate)
    }

    /**
     * Adaptive Voice Activity Detection (VAD) that finds spoken phrase segments in any audio buffer.
     * Automatically adapts to the file's noise floor and splits overly long segments (> 4.2s) at quiet dips.
     */
    fun detectVoiceActivitySegments(
        samples: FloatArray,
        sampleRate: Int,
        maxSegmentSec: Float = 4.2f
    ): List<TimeRange> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()
        val totalSec = samples.size.toFloat() / sampleRate
        if (totalSec <= 0.2f) return listOf(TimeRange(0f, totalSec))

        val (env, hopSec) = envelopeDb(samples, sampleRate, 20)
        val sorted = env.filter { it > -120f }.sorted()
        val p15 = if (sorted.isNotEmpty()) sorted[(sorted.size * 0.15).toInt()] else -50f
        val p85 = if (sorted.isNotEmpty()) sorted[(sorted.size * 0.85).toInt()] else -20f
        val adaptiveThreshold = (p15 + (p85 - p15) * 0.28f).coerceIn(-48f, -24f)

        val rawRanges = mutableListOf<TimeRange>()
        var startIdx = -1
        var silenceRun = 0
        val minSilenceHops = max(1, (0.28f / hopSec).roundToInt())

        for (i in env.indices) {
            val active = env[i] >= adaptiveThreshold
            if (active) {
                if (startIdx < 0) startIdx = max(0, i - 2)
                silenceRun = 0
            } else if (startIdx >= 0) {
                silenceRun++
                if (silenceRun >= minSilenceHops) {
                    val endIdx = max(startIdx + 1, i - silenceRun + 2)
                    val s = (startIdx * hopSec).coerceIn(0f, totalSec)
                    val e = (endIdx * hopSec).coerceIn(s, totalSec)
                    if (e - s >= 0.45f) {
                        rawRanges.add(TimeRange(s, e))
                    }
                    startIdx = -1
                    silenceRun = 0
                }
            }
        }
        if (startIdx >= 0) {
            val s = (startIdx * hopSec).coerceIn(0f, totalSec)
            if (totalSec - s >= 0.4f) {
                rawRanges.add(TimeRange(s, totalSec))
            }
        }

        // Subdivide any long segment (> maxSegmentSec) at local energy minima
        val finalRanges = mutableListOf<TimeRange>()
        for (r in rawRanges) {
            if (r.duration <= maxSegmentSec) {
                finalRanges.add(r)
            } else {
                var curStart = r.start
                while (r.end - curStart > maxSegmentSec) {
                    val searchFrom = curStart + maxSegmentSec * 0.6f
                    val searchTo = min(r.end - 0.5f, curStart + maxSegmentSec)
                    val h0 = (searchFrom / hopSec).toInt().coerceIn(0, env.lastIndex)
                    val h1 = (searchTo / hopSec).toInt().coerceIn(h0, env.lastIndex)
                    var minHop = h1
                    var minVal = Float.POSITIVE_INFINITY
                    for (h in h0..h1) {
                        if (env[h] < minVal) {
                            minVal = env[h]
                            minHop = h
                        }
                    }
                    val splitAt = (minHop * hopSec).coerceIn(curStart + 1.0f, r.end)
                    finalRanges.add(TimeRange(curStart, splitAt))
                    curStart = splitAt + 0.06f
                }
                if (r.end - curStart >= 0.4f) {
                    finalRanges.add(TimeRange(curStart, r.end))
                }
            }
        }

        if (finalRanges.isEmpty()) {
            var cur = 0.15f
            while (cur < totalSec - 0.25f) {
                val end = min(totalSec - 0.05f, cur + 3.0f)
                finalRanges.add(TimeRange(cur, end))
                cur = end + 0.2f
            }
        }
        return finalRanges
    }

    fun learnNoiseProfile(
        rawSamples: FloatArray,
        sampleRate: Int,
        fftSize: Int = 1024
    ): NoiseProfile {
        val n = fftSize
        val hop = n shr 1
        val bins = n shr 1
        val fft = FftEngine(n)
        val win = sqrtHannWindow(n)
        val re = FloatArray(n)
        val im = FloatArray(n)
        val sum = FloatArray(bins + 1)
        val minArr = FloatArray(bins + 1) { Float.POSITIVE_INFINITY }
        var frames = 0

        val samples = if (rawSamples.size < n) {
            FloatArray(n).also { rawSamples.copyInto(it, 0, 0, min(rawSamples.size, n)) }
        } else {
            rawSamples
        }

        var pos = 0
        while (pos + n <= samples.size) {
            for (i in 0 until n) {
                re[i] = samples[pos + i] * win[i]
                im[i] = 0f
            }
            fft.forward(re, im)
            for (k in 0..bins) {
                val mag = hypot(re[k].toDouble(), im[k].toDouble()).toFloat()
                sum[k] += mag
                if (mag < minArr[k]) minArr[k] = mag
            }
            frames++
            pos += hop
        }
        if (frames == 0) frames = 1
        val magnitudes = FloatArray(bins + 1)
        val floor = FloatArray(bins + 1)
        for (k in 0..bins) {
            val avg = sum[k] / frames
            val prev = if (k > 0) sum[k - 1] / frames else avg
            val next = if (k < bins) sum[k + 1] / frames else avg
            magnitudes[k] = (prev + avg * 2f + next) / 4f
            floor[k] = if (minArr[k].isFinite()) minArr[k] else 0f
        }
        return NoiseProfile(
            sampleRate = sampleRate,
            fftSize = n,
            learnedSec = rawSamples.size.toFloat() / sampleRate,
            avgDb = rmsDb(rawSamples),
            peakDb = peakDb(rawSamples),
            magnitudes = magnitudes,
            floor = floor
        )
    }

    fun reduceNoise(
        samples: FloatArray,
        sampleRate: Int,
        profile: NoiseProfile,
        opts: NoiseReduceOptions = NoiseReduceOptions()
    ): FloatArray {
        val n = profile.fftSize
        if (samples.size < n / 4) return samples.copyOf()
        val hop = n shr 1
        val bins = n shr 1
        val fft = FftEngine(n)
        val win = sqrtHannWindow(n)

        val threshold = FloatArray(bins + 1) { k ->
            val srcK = ((k.toFloat() / bins) * (profile.magnitudes.size - 1)).roundToInt()
                .coerceIn(0, profile.magnitudes.size - 1)
            profile.magnitudes[srcK] * opts.strength
        }
        val floorGain = dbToLin(opts.floorDb)
        val re = FloatArray(n)
        val im = FloatArray(n)
        val prevGain = FloatArray(bins) { 1f }
        val out = FloatArray(samples.size)
        val weight = FloatArray(samples.size)
        val s = opts.smoothing

        val padded = if (samples.size >= n) samples else FloatArray(n).also { samples.copyInto(it) }
        var pos = 0
        while (pos < padded.size) {
            val avail = min(n, padded.size - pos)
            for (i in 0 until n) {
                re[i] = if (i < avail) padded[pos + i] * win[i] else 0f
                im[i] = 0f
            }
            fft.forward(re, im)
            for (k in 0..bins) {
                val mag = hypot(re[k].toDouble(), im[k].toDouble()).toFloat()
                var g = if (mag > 1e-9f) (mag - threshold[k]) / mag else floorGain
                g = g.coerceIn(floorGain, 1f)
                val gi = if (k == bins) bins - 1 else k
                g = s * prevGain[gi] + (1f - s) * g
                prevGain[gi] = g
                re[k] *= g
                im[k] *= g
                if (k != 0 && k != bins) {
                    re[n - k] *= g
                    im[n - k] *= g
                }
            }
            fft.inverse(re, im)
            val write = min(n, samples.size - pos)
            for (i in 0 until write) {
                val v = re[i] * win[i]
                out[pos + i] += v
                weight[pos + i] += win[i] * win[i]
            }
            if (pos + n >= padded.size) break
            pos += hop
        }
        for (i in out.indices) {
            val w = weight[i]
            out[i] = if (w > 1e-6f) out[i] / w else 0f
        }
        return out
    }

    fun detectSilence(
        samples: FloatArray,
        sampleRate: Int,
        thresholdDb: Float,
        minSec: Float
    ): List<TimeRange> {
        val (values, hopSec) = envelopeDb(samples, sampleRate, 10)
        val ranges = mutableListOf<TimeRange>()
        var runStart = -1
        val totalSec = samples.size.toFloat() / sampleRate
        for (i in values.indices) {
            val quiet = values[i] < thresholdDb
            if (quiet && runStart < 0) runStart = i
            if ((!quiet || i == values.lastIndex) && runStart >= 0) {
                val end = if (quiet) i + 1 else i
                val startSec = runStart * hopSec
                val stopSec = min(totalSec, end * hopSec)
                if (stopSec - startSec >= minSec) {
                    ranges.add(TimeRange(startSec, stopSec))
                }
                runStart = -1
            }
        }
        return ranges
    }

    fun invertRanges(ranges: List<TimeRange>, totalSec: Float): List<TimeRange> {
        val sorted = ranges.sortedBy { it.start }
        val out = mutableListOf<TimeRange>()
        var cursor = 0f
        for (r in sorted) {
            if (r.start > cursor + 0.002f) {
                out.add(TimeRange(cursor, r.start))
            }
            cursor = max(cursor, r.end)
        }
        if (cursor < totalSec - 0.002f) {
            out.add(TimeRange(cursor, totalSec))
        }
        return out
    }

    fun extractRange(
        samples: FloatArray,
        sampleRate: Int,
        range: TimeRange,
        fadeMs: Int = 6
    ): FloatArray {
        val a = (range.start * sampleRate).roundToInt().coerceIn(0, samples.size)
        val b = (range.end * sampleRate).roundToInt().coerceIn(a, samples.size)
        val out = samples.copyOfRange(a, b)
        val fade = min(max(1, (sampleRate * fadeMs) / 1000), out.size / 2)
        for (i in 0 until fade) {
            val g = i.toFloat() / fade
            out[i] *= g
            out[out.lastIndex - i] *= g
        }
        return out
    }

    fun keepRanges(
        samples: FloatArray,
        sampleRate: Int,
        ranges: List<TimeRange>,
        fadeMs: Int = 6
    ): FloatArray {
        val parts = ranges.map { extractRange(samples, sampleRate, it, fadeMs) }
        val total = parts.sumOf { it.size }
        val out = FloatArray(total)
        var off = 0
        for (p in parts) {
            p.copyInto(out, off)
            off += p.size
        }
        return out
    }

    fun deleteRange(
        samples: FloatArray,
        sampleRate: Int,
        rangeToRemove: TimeRange
    ): FloatArray {
        val totalSec = samples.size.toFloat() / sampleRate
        val kept = invertRanges(listOf(rangeToRemove), totalSec)
        return if (kept.isEmpty()) samples else keepRanges(samples, sampleRate, kept, 8)
    }

    fun applyFadeInOut(
        samples: FloatArray,
        sampleRate: Int,
        fadeInSec: Float = 0.4f,
        fadeOutSec: Float = 0.4f
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val out = samples.copyOf()
        val inSamples = min(out.size / 2, (fadeInSec * sampleRate).roundToInt())
        val outSamples = min(out.size / 2, (fadeOutSec * sampleRate).roundToInt())
        for (i in 0 until inSamples) {
            out[i] *= (i.toFloat() / max(1, inSamples))
        }
        for (i in 0 until outSamples) {
            out[out.lastIndex - i] *= (i.toFloat() / max(1, outSamples))
        }
        return out
    }

    fun removeSilence(
        samples: FloatArray,
        sampleRate: Int,
        opts: SilenceOptions
    ): SilenceResult {
        val detected = detectSilence(samples, sampleRate, opts.thresholdDb, opts.minSilenceSec)
        val total = samples.size.toFloat() / sampleRate
        val pad = opts.padMs / 1000f
        val cut = mutableListOf<TimeRange>()
        for (r in detected) {
            val len = r.duration
            val keep = opts.keepSec
            if (keep != null && keep > 0f) {
                if (len <= keep) continue
                val remove = len - keep
                val s = (r.start + keep / 2f - pad).coerceIn(0f, total)
                val e = (s + remove + pad * 2f).coerceIn(s, total)
                cut.add(TimeRange(s, e))
            } else {
                val s = (r.start - pad).coerceIn(0f, total)
                val e = (r.end + pad).coerceIn(s, total)
                cut.add(TimeRange(s, e))
            }
        }
        val kept = invertRanges(cut, total)
        val outSamples = if (kept.isEmpty()) samples.copyOf() else keepRanges(samples, sampleRate, kept, 6)
        val removedSec = cut.fold(0f) { acc, r -> acc + r.duration }
        return SilenceResult(
            samples = outSamples,
            removed = cut,
            kept = kept,
            removedSec = removedSec
        )
    }

    fun splitBySilence(
        samples: FloatArray,
        sampleRate: Int,
        opts: SplitOptions
    ): Pair<List<TimeRange>, List<TimeRange>> {
        val total = samples.size.toFloat() / sampleRate
        val silences = detectSilence(samples, sampleRate, opts.thresholdDb, opts.minSilenceSec)
        val maxPoints = max(0, opts.maxChunks - 1)
        val chosen = if (silences.size > maxPoints) {
            silences.sortedByDescending { it.duration }.take(maxPoints).sortedBy { it.start }
        } else {
            silences
        }
        var ranges = invertRanges(chosen, total)
        if (opts.minChunkSec > 0f) {
            ranges = ranges.filter { it.duration >= opts.minChunkSec }
            if (ranges.isEmpty()) ranges = listOf(TimeRange(0f, total))
        }
        return ranges to silences
    }

    fun softClip(samples: FloatArray, threshold: Float = 0.98f): FloatArray {
        val out = FloatArray(samples.size)
        val t = max(0.05f, threshold)
        for (i in samples.indices) {
            val v = samples[i]
            out[i] = if (abs(v) <= t) {
                v
            } else {
                val sgn = if (v >= 0f) 1f else -1f
                sgn * (t + (1f - t) * tanh(((abs(v) - t) / (1f - t)).toDouble()).toFloat())
            }
        }
        return out
    }

    fun normalizePeak(samples: FloatArray, targetDb: Float = -1f): FloatArray {
        val peak = peakLevel(samples)
        if (peak < 1e-6f) return samples.copyOf()
        val g = dbToLin(targetDb) / peak
        return FloatArray(samples.size) { i -> (samples[i] * g).coerceIn(-1f, 1f) }
    }

    fun normalizeRms(samples: FloatArray, targetDb: Float = -18f): FloatArray {
        val rms = rmsLevel(samples)
        if (rms < 1e-6f) return samples.copyOf()
        val g = min(dbToLin(targetDb) / rms, dbToLin(24f))
        val boosted = FloatArray(samples.size) { i -> samples[i] * g }
        return normalizePeak(softClip(boosted, 0.96f), -1f)
    }

    fun autoLevel(
        samples: FloatArray,
        sampleRate: Int,
        opts: AutoLevelOptions = AutoLevelOptions()
    ): FloatArray {
        val hop = max(64, (sampleRate * opts.windowMs) / 1000)
        val blocks = max(1, ceil(samples.size.toDouble() / hop).toInt())
        val gains = FloatArray(blocks)
        val maxBoost = dbToLin(opts.maxBoostDb)
        val maxCut = dbToLin(-opts.maxCutDb)
        var prev = 1f
        for (b in 0 until blocks) {
            val off = b * hop
            val end = min(samples.size, off + hop)
            val rms = rmsLevel(samples, off, end)
            val db = linToDb(rms)
            var g = prev
            if (db > opts.noiseGateDb) {
                g = (dbToLin(opts.targetDb) / max(rms, 1e-6f)).coerceIn(maxCut, maxBoost)
                g = opts.smoothing * prev + (1f - opts.smoothing) * g
            }
            prev = g
            gains[b] = g
        }
        val out = FloatArray(samples.size)
        for (i in samples.indices) {
            val b = min(blocks - 1, i / hop)
            val nb = min(blocks - 1, b + 1)
            val t = (i - b * hop).toFloat() / hop
            val g = gains[b] * (1f - t) + gains[nb] * t
            out[i] = samples[i] * g
        }
        val limited = softClip(out, 0.97f)
        return if (peakDb(limited) > -1f) normalizePeak(limited, -1f) else limited
    }

    fun renderEnhanced(
        samples: FloatArray,
        sampleRate: Int,
        cfg: EnhanceConfig
    ): FloatArray {
        if (samples.isEmpty()) return samples
        var buf = samples.copyOf()
        buf = applyBiquad(buf, sampleRate, BiquadType.HIGHPASS, cfg.highpassHz, 0.707f, 0f)
        buf = applyBiquad(buf, sampleRate, BiquadType.LOWPASS, cfg.lowpassHz, 0.707f, 0f)
        if (abs(cfg.warmthGainDb) > 0.05f) {
            buf = applyBiquad(buf, sampleRate, BiquadType.LOWSHELF, cfg.warmthHz, 0.707f, cfg.warmthGainDb)
        }
        if (abs(cfg.presenceGainDb) > 0.05f) {
            buf = applyBiquad(buf, sampleRate, BiquadType.PEAKING, cfg.presenceHz, cfg.presenceQ, cfg.presenceGainDb)
        }
        if (abs(cfg.deEsserGainDb) > 0.05f) {
            buf = applyBiquad(buf, sampleRate, BiquadType.PEAKING, cfg.deEsserHz, cfg.deEsserQ, cfg.deEsserGainDb)
        }
        if (abs(cfg.airGainDb) > 0.05f) {
            buf = applyBiquad(buf, sampleRate, BiquadType.HIGHSHELF, cfg.airHz, 0.707f, cfg.airGainDb)
        }
        val threshLin = dbToLin(cfg.compThresholdDb)
        val ratio = max(1f, cfg.compRatio)
        val attackCoef = kotlin.math.exp(-1.0 / (max(0.001f, cfg.compAttack) * sampleRate)).toFloat()
        val releaseCoef = kotlin.math.exp(-1.0 / (max(0.01f, cfg.compRelease) * sampleRate)).toFloat()
        val makeup = dbToLin(cfg.makeupGainDb)
        val drive = max(1f, cfg.limiterDrive)
        val normTanh = tanh(drive.toDouble()).toFloat()

        var env = 0f
        for (i in buf.indices) {
            val x = buf[i]
            val a = abs(x)
            env = if (a > env) attackCoef * env + (1f - attackCoef) * a else releaseCoef * env + (1f - releaseCoef) * a
            var gain = 1f
            if (env > threshLin && env > 1e-6f) {
                val envDb = linToDb(env)
                val overDb = envDb - cfg.compThresholdDb
                val compressedDb = cfg.compThresholdDb + overDb / ratio
                gain = dbToLin(compressedDb - envDb)
            }
            val driven = x * gain * makeup * drive
            buf[i] = (tanh(driven.toDouble()).toFloat() / normTanh).coerceIn(-0.99f, 0.99f)
        }
        return buf
    }

    private enum class BiquadType { HIGHPASS, LOWPASS, LOWSHELF, HIGHSHELF, PEAKING }

    private fun applyBiquad(
        input: FloatArray,
        sampleRate: Int,
        type: BiquadType,
        freqHz: Float,
        q: Float,
        gainDb: Float
    ): FloatArray {
        val f0 = freqHz.coerceIn(20f, sampleRate * 0.45f)
        val w0 = 2.0 * PI * f0 / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * max(0.2f, q))
        val a = 10.0.pow(gainDb / 40.0)

        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a0 = 1.0
        var a1 = 0.0
        var a2 = 0.0

        when (type) {
            BiquadType.HIGHPASS -> {
                b0 = (1.0 + cosW0) / 2.0
                b1 = -(1.0 + cosW0)
                b2 = (1.0 + cosW0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            BiquadType.LOWPASS -> {
                b0 = (1.0 - cosW0) / 2.0
                b1 = 1.0 - cosW0
                b2 = (1.0 - cosW0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            BiquadType.PEAKING -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / a
            }
            BiquadType.LOWSHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
                b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
                a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
                a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
            }
            BiquadType.HIGHSHELF -> {
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
                b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
                a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
                a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
            }
        }

        val nb0 = (b0 / a0).toFloat()
        val nb1 = (b1 / a0).toFloat()
        val nb2 = (b2 / a0).toFloat()
        val na1 = (a1 / a0).toFloat()
        val na2 = (a2 / a0).toFloat()

        val out = FloatArray(input.size)
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f
        for (i in input.indices) {
            val x0 = input[i]
            val y0 = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            out[i] = y0
        }
        return out
    }

    fun analyze(samples: FloatArray, sampleRate: Int): AudioAnalysis {
        if (samples.isEmpty()) {
            return AudioAnalysis(0f, -90f, -90f, 0f, 0f, 0, -90f)
        }
        val (values, hopSec) = envelopeDb(samples, sampleRate, 20)
        var clipped = 0
        for (s in samples) if (abs(s) >= 0.999f) clipped++
        val sorted = values.sorted()
        val p10 = sorted[(sorted.size * 0.1).toInt().coerceIn(0, sorted.lastIndex)]
        val p90 = sorted[(sorted.size * 0.9).toInt().coerceIn(0, sorted.lastIndex)]
        var silence = 0f
        for (v in values) if (v < -45f) silence += hopSec
        return AudioAnalysis(
            durationSec = samples.size.toFloat() / sampleRate,
            peakDb = peakDb(samples),
            rmsDb = rmsDb(samples),
            dynamicRangeDb = (p90 - p10).coerceAtLeast(0f),
            silenceSec = silence,
            clipped = clipped,
            noiseFloorDb = p10
        )
    }

    fun generateDemoAudio(
        seconds: Int = 20,
        sampleRate: Int = 44100,
        noiseDb: Float = -34f,
        seedInit: Long = 20261027L
    ): Pair<FloatArray, List<TimeRange>> {
        var seed = seedInit
        fun rand(): Float {
            seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return seed.toFloat() / 4294967296f
        }

        val total = seconds * sampleRate
        val out = FloatArray(total)
        var lp = 0f
        val noiseAmp = dbToLin(noiseDb)
        for (i in 0 until total) {
            val white = rand() * 2f - 1f
            lp = lp * 0.86f + white * 0.14f
            val drift = 0.75f + 0.25f * sin((2.0 * PI * i) / (sampleRate * 6.5)).toFloat()
            out[i] = lp * noiseAmp * drift
        }

        val speechRanges = mutableListOf<TimeRange>()
        var t = 0.55f
        var idx = 0
        val phraseLengths = floatArrayOf(2.3f, 2.8f, 2.5f, 3.0f, 2.6f, 2.4f)
        val gaps = floatArrayOf(1.1f, 0.85f, 1.5f, 0.9f, 1.3f)

        while (t < seconds - 1.2f) {
            val phraseLen = min(phraseLengths[idx % phraseLengths.size], seconds - t - 0.5f)
            if (phraseLen < 0.8f) break
            val start = t
            var pt = 0f
            val baseF0 = 115f + (idx % 3) * 14f
            while (pt < phraseLen) {
                val syl = 0.16f + rand() * 0.14f
                val s0 = ((start + pt) * sampleRate).toInt().coerceIn(0, total)
                val s1 = min(total, ((start + pt + syl) * sampleRate).toInt())
                val f0 = baseF0 * (0.95f + rand() * 0.12f)
                val f1 = 420f + rand() * 380f
                val f2 = 1250f + rand() * 820f
                val span = max(1, s1 - s0).toFloat()
                for (i in s0 until s1) {
                    val x = (i - s0).toFloat() / sampleRate
                    val env = sin(PI * ((i - s0) / span)).pow(1.3).toFloat()
                    val glottal = (
                        sin(2.0 * PI * f0 * x) * 0.6 +
                        sin(2.0 * PI * f0 * 2 * x) * 0.32 +
                        sin(2.0 * PI * f0 * 3 * x) * 0.18
                    ).toFloat()
                    val formants = (
                        sin(2.0 * PI * f1 * x + glottal * 1.5) * 0.42 +
                        sin(2.0 * PI * f2 * x + glottal * 0.85) * 0.22
                    ).toFloat()
                    out[i] += (glottal * 0.55f + formants * 0.5f) * env * 0.65f
                }
                pt += syl + 0.03f + rand() * 0.04f
            }
            val end = min(seconds.toFloat(), start + phraseLen)
            speechRanges.add(TimeRange(start, end))
            t = end + gaps[idx % gaps.size]
            idx++
        }

        return normalizePeak(softClip(out, 0.97f), -1.5f) to speechRanges
    }

    fun formatTimecode(sec: Float, withMs: Boolean = true): String {
        val safe = if (sec.isFinite() && sec > 0f) sec else 0f
        val m = (safe / 60f).toInt()
        val s = (safe % 60f).toInt()
        val ms = ((safe - floor(safe)) * 100f).toInt()
        return if (withMs) {
            "%02d:%02d.%02d".format(m, s, ms)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    fun formatSrtTimestamp(sec: Float): String {
        val safe = if (sec.isFinite() && sec > 0f) sec else 0f
        val totalMs = (safe * 1000f).roundToInt()
        val hrs = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return "%02d:%02d:%02d,%03d".format(hrs, mins, secs, ms)
    }

    fun formatVttTimestamp(sec: Float): String {
        val safe = if (sec.isFinite() && sec > 0f) sec else 0f
        val totalMs = (safe * 1000f).roundToInt()
        val hrs = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return "%02d:%02d:%02d.%03d".format(hrs, mins, secs, ms)
    }
}
