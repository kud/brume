package app.beansontoast.brume.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A biquad from the RBJ audio EQ cookbook, transposed direct form II, state in Double. */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var z1 = 0.0; private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double): Biquad {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
        return this
    }

    fun highPass(rate: Int, hz: Double, q: Double = 0.7071): Biquad {
        val w = 2 * PI * hz / rate; val c = cos(w); val alpha = sin(w) / (2 * q)
        return set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun lowPass(rate: Int, hz: Double, q: Double = 0.7071): Biquad {
        val w = 2 * PI * hz / rate; val c = cos(w); val alpha = sin(w) / (2 * q)
        return set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Constant 0 dB peak gain. */
    fun bandPass(rate: Int, hz: Double, q: Double): Biquad {
        val w = 2 * PI * hz / rate; val c = cos(w); val alpha = sin(w) / (2 * q)
        return set(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun peaking(rate: Int, hz: Double, db: Double, q: Double): Biquad {
        val a = 10.0.pow(db / 40); val w = 2 * PI * hz / rate; val c = cos(w); val alpha = sin(w) / (2 * q)
        return set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun lowShelf(rate: Int, hz: Double, db: Double): Biquad {
        val a = 10.0.pow(db / 40); val w = 2 * PI * hz / rate; val c = cos(w)
        val alpha = sin(w) / 2 * sqrt(2.0); val k = 2 * sqrt(a) * alpha
        return set(
            a * ((a + 1) - (a - 1) * c + k), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - k),
            (a + 1) + (a - 1) * c + k, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - k,
        )
    }

    fun highShelf(rate: Int, hz: Double, db: Double): Biquad {
        val a = 10.0.pow(db / 40); val w = 2 * PI * hz / rate; val c = cos(w)
        val alpha = sin(w) / 2 * sqrt(2.0); val k = 2 * sqrt(a) * alpha
        return set(
            a * ((a + 1) + (a - 1) * c + k), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - k),
            (a + 1) - (a - 1) * c + k, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - k,
        )
    }
}
