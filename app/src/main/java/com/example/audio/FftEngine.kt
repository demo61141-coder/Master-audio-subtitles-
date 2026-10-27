package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iterative radix-2 in-place FFT used by the Noise Profile Learning & Spectral Subtraction engine.
 */
class FftEngine(val size: Int) {
    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)

    init {
        require(size >= 2 && (size and (size - 1)) == 0) {
            "FFT size must be a power of two, got $size"
        }
        for (i in 0 until size / 2) {
            val angle = 2.0 * PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
    }

    fun forward(re: FloatArray, im: FloatArray) {
        transform(re, im, -1f)
    }

    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, 1f)
        val n = size.toFloat()
        for (i in 0 until size) {
            re[i] /= n
            im[i] /= n
        }
    }

    private fun transform(re: FloatArray, im: FloatArray, sign: Float) {
        val n = size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val idx = k * step
                    val c = cosTable[idx]
                    val s = sign * sinTable[idx]
                    val e = i + k
                    val o = e + half
                    val tre = re[o] * c - im[o] * s
                    val tim = re[o] * s + im[o] * c
                    re[o] = re[e] - tre
                    im[o] = im[e] - tim
                    re[e] += tre
                    im[e] += tim
                }
                i += len
            }
            len = len shl 1
        }
    }
}

fun sqrtHannWindow(size: Int): FloatArray {
    val w = FloatArray(size)
    for (i in 0 until size) {
        w[i] = sqrt(0.5 * (1.0 - cos((2.0 * PI * i) / size))).toFloat()
    }
    return w
}

fun hannWindow(size: Int): FloatArray {
    val w = FloatArray(size)
    for (i in 0 until size) {
        w[i] = (0.5 * (1.0 - cos((2.0 * PI * i) / size))).toFloat()
    }
    return w
}
