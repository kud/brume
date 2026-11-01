package app.beansontoast.brume.core

import org.junit.Assert

/** kotlin.test-shaped asserts (message last) on JUnit 4, for the newer tests. Explicit imports elsewhere win. */
internal fun assertTrue(condition: Boolean, message: String = "") = Assert.assertTrue(message, condition)
internal fun assertFalse(condition: Boolean, message: String = "") = Assert.assertFalse(message, condition)
internal fun <T> assertEquals(expected: T, actual: T, message: String = "") = Assert.assertEquals(message, expected, actual)
internal fun assertEquals(expected: Float, actual: Float, delta: Float) = Assert.assertEquals(expected, actual, delta)
internal inline fun <reified E : Throwable> assertFailsWith(block: () -> Unit) {
    try { block() } catch (e: Throwable) { if (e is E) return; throw e }
    throw AssertionError("expected ${E::class.simpleName}")
}

internal val PINK = app.beansontoast.brume.core.sound.Sound.Noise(app.beansontoast.brume.core.sound.NoiseColour.PINK)
internal val BROWN = app.beansontoast.brume.core.sound.Sound.Noise(app.beansontoast.brume.core.sound.NoiseColour.BROWN)
internal val RAIN = app.beansontoast.brume.core.sound.Sound.Recorded(app.beansontoast.brume.core.sound.Ambience.RAIN)
internal val SEA = app.beansontoast.brume.core.sound.Sound.Recorded(app.beansontoast.brume.core.sound.Ambience.SEA)

/** A stand-in recording: two seconds of seeded noise at 48 kHz, quiet like a real file (-20 dBFS RMS or so). */
internal fun testLoop(seed: Long = 42, seconds: Double = 2.0, rate: Int = 48_000): app.beansontoast.brume.core.sound.Loop {
    val rng = app.beansontoast.brume.core.rng.Pcg32.seeded(seed)
    return app.beansontoast.brume.core.sound.Loop(ShortArray((seconds * rate).toInt()) { (rng.nextSigned() * 5000).toInt().toShort() }, rate)
}
