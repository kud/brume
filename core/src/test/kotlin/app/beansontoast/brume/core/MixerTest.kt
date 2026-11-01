package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.Loop
import app.beansontoast.brume.core.sound.LoopSource
import app.beansontoast.brume.core.sound.Mix
import app.beansontoast.brume.core.sound.MixLayer
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import app.beansontoast.brume.core.sound.TARGET_RMS
import app.beansontoast.brume.core.sound.TonedNoise
import org.junit.Test
import kotlin.math.abs

class MixerTest {
    private val block = 480
    private val buffer = FloatArray(block * 2)

    private fun SoundEngine.peaks(seconds: Double): List<Float> =
        List((seconds * RATE / block).toInt()) { render(buffer, block); buffer.maxOf { abs(it) } }

    private fun all(level: Float) = SoundEngine(RATE, 5).apply {
        Sound.all.forEach { post(Command.Add(it, level, loop = (it as? Sound.Recorded)?.let { _ -> testLoop(it.key.hashCode().toLong()) })) }
    }

    @Test fun `every sound at once, all at full, never reaches full scale`() {
        val e = all(1f); e.post(Command.Start)
        val peak = e.peaks(6.0).max()
        assertTrue(peak < 0.96f, "peak $peak")
        assertEquals(Sound.all.size, e.sounds.size)
    }

    @Test fun `the layers together never ask for more than one layer at full`() {
        val e = all(1f); e.post(Command.Start); e.peaks(4.0)
        assertTrue(e.effectiveGain() <= SoundEngine.HEADROOM + 1e-4f, "gain ${e.effectiveGain()}")
    }

    @Test fun `quiet layers are left exactly as set`() {
        val one = SoundEngine(RATE, 5).apply { post(Command.Add(PINK, 0.3f)); post(Command.Start) }
        val two = SoundEngine(RATE, 5).apply { post(Command.Add(PINK, 0.3f)); post(Command.Add(BROWN, 0.3f)); post(Command.Start) }
        one.peaks(4.0); two.peaks(4.0)
        assertEquals(2 * one.effectiveGain(), two.effectiveGain(), 1e-4f)
    }

    @Test fun `a layer added while playing fades in on its own, and the others never move`() {
        val e = SoundEngine(RATE, 5).apply { post(Command.Add(PINK, 0.5f)); post(Command.Start) }
        e.peaks(4.0)
        val before = e.effectiveGain()
        e.post(Command.Add(RAIN, 0.5f, loop = testLoop()))
        e.peaks(0.02)
        val added = LevelRules.gainFor(0.5f)
        assertTrue(e.effectiveGain() - before < added * 0.01f, "rain jumped in: ${e.effectiveGain() - before}")
        e.peaks(1.0)
        assertTrue(e.effectiveGain() - before < added * 0.6f, "still rising at 1 s")
        var last = e.effectiveGain()
        repeat(300) { e.render(buffer, block); assertTrue(e.effectiveGain() >= last - 1e-6f); last = e.effectiveGain() }
        assertEquals(before + added, e.effectiveGain(), 1e-4f)
    }

    @Test fun `removing a layer fades it out, then it is gone`() {
        val e = SoundEngine(RATE, 5).apply { post(Command.Add(PINK, 0.5f)); post(Command.Add(SEA, 0.5f, loop = testLoop())); post(Command.Start) }
        e.peaks(4.0)
        e.post(Command.Remove(SEA))
        e.peaks(0.1)
        assertEquals(listOf<Sound>(PINK), e.sounds)
        assertTrue(e.effectiveGain() > LevelRules.gainFor(0.5f) * 1.5f, "cut, not faded")
        e.peaks(LevelRules.REMOVE_FADE_SECONDS + 0.1)
        assertEquals(LevelRules.gainFor(0.5f), e.effectiveGain(), 1e-4f)
    }

    @Test fun `each sound plays once, adding it again keeps the one playing`() {
        val e = SoundEngine(RATE, 5).apply { post(Command.Add(PINK, 0.5f)); post(Command.Start) }
        e.peaks(4.0)
        val g = e.effectiveGain()
        e.post(Command.Add(PINK, 0.5f, tone = 0.4f)); e.peaks(0.1)
        assertEquals(g, e.effectiveGain(), 1e-5f)
        assertEquals(1, e.sounds.size)
    }

    @Test fun `each layer has its own level`() {
        val e = SoundEngine(RATE, 5).apply { post(Command.Add(PINK, 0.2f)); post(Command.Add(RAIN, 0.7f, loop = testLoop())); post(Command.Start) }
        e.peaks(4.0)
        assertEquals(LevelRules.gainFor(0.2f) + LevelRules.gainFor(0.7f), e.effectiveGain(), 1e-4f)
        e.post(Command.Level(RAIN, 0.1f)); e.peaks(0.1)
        assertEquals(LevelRules.gainFor(0.2f) + LevelRules.gainFor(0.1f), e.effectiveGain(), 1e-4f)
    }

    @Test fun `too loud silences every layer`() {
        val e = all(0.8f); e.post(Command.Start); e.peaks(4.0)
        e.post(Command.TooLoud); e.peaks(0.02)
        assertEquals(SoundEngine.State.STOPPED, e.state)
        assertTrue(e.peaks(1.0).all { it == 0f })
    }

    @Test fun `a loop wraps without a seam, and plays at the loop's loudness`() {
        // A slow sine whose period divides the loop: wrapping is seamless only if no sample is lost or doubled.
        val n = 48_000
        val pcm = ShortArray(n) { (8000 * kotlin.math.sin(2 * Math.PI * 10 * it / n)).toInt().toShort() }
        val s = LoopSource(Loop(pcm, 48_000), 48_000, 0)
        var last = s.next(); var worst = 0f
        repeat(3 * n) { val x = s.next(); worst = maxOf(worst, abs(x - last)); last = x }
        val step = 2 * Math.PI * 10 / n * 8000 / 32768 * Loop(pcm, 48_000).gain
        assertTrue(worst <= step * 1.1, "jump $worst vs $step")
        org.junit.Assert.assertEquals(0.0, db(rms(LoopSource(testLoop(), RATE, 3), seconds = 2) / TARGET_RMS), 0.5)
    }

    @Test fun `a 48 kHz loop plays at its own pitch on a 44100 Hz phone`() {
        val n = 48_000
        val pcm = ShortArray(n) { (8000 * kotlin.math.sin(2 * Math.PI * 1000 * it / 48_000.0)).toInt().toShort() }
        val s = LoopSource(Loop(pcm, 48_000), 44_100, 0)
        var crossings = 0; var last = s.next()
        repeat(44_100) { val x = s.next(); if (last < 0 && x >= 0) crossings++; last = x }
        org.junit.Assert.assertEquals(1000.0, crossings.toDouble(), 2.0)
    }

    @Test fun `tone tilts the noise from low to bright, at the same loudness`() {
        fun tilt(t: Float) = TonedNoise(NoiseColour.PINK, RATE, 11, t).let { powerDb(it, 4000.0) } -
            TonedNoise(NoiseColour.PINK, RATE, 11, t).let { powerDb(it, 150.0) }
        val soft = tilt(-1f); val flat = tilt(0f); val bright = tilt(1f)
        assertTrue(soft < flat - 6 && bright > flat + 6, "soft $soft flat $flat bright $bright")
        listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { t ->
            org.junit.Assert.assertEquals(0.0, db(rms(TonedNoise(NoiseColour.BROWN, RATE, 3, t), seconds = 4) / TARGET_RMS), 1.0)
        }
    }

    @Test fun `tone 0 is the noise as it always sounded`() {
        val a = TonedNoise(NoiseColour.PINK, RATE, 9, 0f)
        val b = app.beansontoast.brume.core.sound.noiseSource(NoiseColour.PINK, RATE, 9)
        repeat(10_000) { assertEquals(b.next(), a.next(), 1e-4f) }
    }

    @Test fun `mix round-trips, and the two-slot mixes stored before come back as layers`() {
        val mix = Mix(listOf(MixLayer(BROWN, 0.3f), MixLayer(RAIN), MixLayer(Sound.Recorded(Ambience.CAFE))), 45)
        assertEquals(mix, Mix.decode(mix.encode()))
        assertEquals(Mix(listOf(MixLayer(PINK), MixLayer(SEA)), null), Mix.decode("PINK|SURF|-"))
        assertEquals(Mix(listOf(MixLayer(RAIN)), 45), Mix.decode("-|RAIN|45"))
        assertEquals(Mix(listOf(MixLayer(Sound.Recorded(Ambience.WIND))), null), Mix.decode("-|WIND|-"))
        assertEquals(null, Mix.decode("garbage"))
        assertEquals(Mix(listOf(MixLayer(PINK)), null), Mix.decode("2|-|noise:PINK@0.0,noise:PINK@0.5,nature:NOPE@0"))
    }
}
