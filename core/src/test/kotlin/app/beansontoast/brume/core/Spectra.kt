package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.Source
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

const val RATE = 48_000

/** Goertzel power at [hz], averaged over [windows] Hann-windowed blocks: no FFT library needed. */
fun powerDb(source: Source, hz: Double, windows: Int = 120, size: Int = 4096): Double {
    val k = 2 * cos(2 * PI * hz / RATE)
    var total = 0.0
    repeat(windows) {
        var s1 = 0.0; var s2 = 0.0
        for (i in 0 until size) {
            val w = 0.5 - 0.5 * cos(2 * PI * i / (size - 1))
            val s = source.next() * w + k * s1 - s2
            s2 = s1; s1 = s
        }
        total += s1 * s1 + s2 * s2 - k * s1 * s2
    }
    return 10 * log10(total / windows)
}

fun rms(source: Source, seconds: Int = 4): Double {
    var sum = 0.0
    val n = RATE * seconds
    repeat(n) { val x = source.next(); sum += x * x }
    return sqrt(sum / n)
}

fun db(ratio: Double) = 20 * log10(ratio)
