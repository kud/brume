package app.beansontoast.brume.core

import app.beansontoast.brume.core.export.ExportFormat
import app.beansontoast.brume.core.export.ExportFile
import app.beansontoast.brume.core.export.MatchV1
import app.beansontoast.brume.core.export.ProfileV1
import app.beansontoast.brume.core.finder.Character
import app.beansontoast.brume.core.finder.ComfortBand
import app.beansontoast.brume.core.finder.Ear
import app.beansontoast.brume.core.finder.OctaveCheck
import app.beansontoast.brume.core.finder.PitchLadder
import app.beansontoast.brume.core.finder.PitchMatch
import app.beansontoast.brume.core.finder.PitchRange
import app.beansontoast.brume.core.finder.Probe
import app.beansontoast.brume.core.finder.ProbeVoice
import app.beansontoast.brume.core.finder.Rhythm
import app.beansontoast.brume.core.finder.Side
import app.beansontoast.brume.core.finder.Signpost
import app.beansontoast.brume.core.finder.TinnitusProfile
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.Mix
import app.beansontoast.brume.core.sound.MixLayer
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.TARGET_RMS
import app.beansontoast.brume.core.sound.bandSource
import app.beansontoast.brume.core.sync.SyncMerge
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** « Mon acouphène »: the pitch search, the profile and its comfort band. Invented values only. */
class FinderTest {
    private fun assertTrue(c: Boolean) = org.junit.Assert.assertTrue(c)
    private fun assertTrue(m: String, c: Boolean) = org.junit.Assert.assertTrue(m, c)

    private val rate = 48_000

    @Test fun `the coarse ladder walks half-octaves from 250 Hz to under 12 kHz`() {
        assertEquals(listOf(250, 350, 500, 710, 1000, 1410, 2000, 2830, 4000, 5660, 8000, 11310), PitchLadder.coarse)
        assertEquals(4000, PitchLadder.nearestCoarse(4300))
        assertEquals(250, PitchLadder.nearestCoarse(20))
    }

    @Test fun `fine steps are a quarter semitone, reach the next coarse step and stay in range`() {
        assertEquals(8000, PitchLadder.fine(4000, PitchLadder.FINE_STEPS_PER_OCTAVE))
        assertEquals(4060, PitchLadder.fine(4000, 1)) // 4000 x 2^(1/48) = 4058
        assertEquals(PitchLadder.coarse[9], PitchLadder.fineRange(4000).last)
        assertEquals(PitchLadder.MAX_HZ, PitchLadder.fine(11_310, 40))
        assertEquals(PitchLadder.MIN_HZ, PitchLadder.fine(250, -10))
        val r = PitchLadder.fineRange(2000)
        assertEquals(2000, PitchLadder.at(PitchLadder.position(2000, r.first, r.last), r.first, r.last))
    }

    @Test fun `the octave check offers below and above, leaving out what falls off the range`() {
        assertEquals(listOf(OctaveCheck.Choice.LOWER to 3000, OctaveCheck.Choice.SAME to 6000), OctaveCheck.choices(6000).take(2))
        assertEquals(3, OctaveCheck.choices(6000).size)
        assertEquals(listOf(OctaveCheck.Choice.LOWER, OctaveCheck.Choice.SAME), OctaveCheck.choices(9000).map { it.first })
        assertEquals(listOf(OctaveCheck.Choice.SAME, OctaveCheck.Choice.HIGHER), OctaveCheck.choices(400).map { it.first })
        assertEquals(3000, OctaveCheck.resolve(6000, OctaveCheck.Choice.LOWER))
        assertEquals(12_000, OctaveCheck.resolve(7000, OctaveCheck.Choice.HIGHER))
    }

    @Test fun `a profile is a range across its matches, centred on the octave scale`() {
        val r = PitchRange.of(listOf(4000, 6000, 5000))!!
        assertEquals(4000, r.low); assertEquals(6000, r.high); assertEquals(3, r.count)
        assertEquals(4930, r.centre) // cube root of 4000 x 5000 x 6000
        assertNull(PitchRange.of(emptyList()))
        assertTrue(PitchRange.of(listOf(3000))!!.single)
    }

    @Test fun `the comfort band sits around every match, at least half an octave wide and within the ear`() {
        val one = ComfortBand.around(PitchRange.of(listOf(4000))!!)
        assertEquals(4000, one.centre); assertEquals(7, one.widthTenths) // two thirds of an octave
        val spread = ComfortBand.around(PitchRange.of(listOf(4000, 8000))!!)
        assertTrue(spread.low <= 4000 && spread.high >= 8000)
        val wide = ComfortBand.around(PitchRange.of(listOf(500, 11_000))!!)
        assertEquals(20, wide.widthTenths)
        val top = ComfortBand.around(PitchRange.of(listOf(12_000))!!)
        assertTrue("${top.high}", top.high <= ComfortBand.CEILING_HZ + 10)
    }

    @Test fun `the band layer plays at the target loudness and round-trips through a stored mix`() {
        for (centre in listOf(500, 4000, 10_000)) {
            val s = bandSource(centre, 7, rate, 3)
            var sum = 0.0; val n = rate * 2
            repeat(n) { val x = s.next(); sum += x * x }
            val db = 20 * log10(sqrt(sum / n) / TARGET_RMS)
            assertTrue("band at $centre is $db dB off", abs(db) < 1.5)
        }
        val band = Sound.Band(4930, 9)
        assertEquals(band, Sound.parse(band.key))
        assertNull(Sound.parse("band:99999/9")); assertNull(Sound.parse("band:4000"))
        assertEquals(listOf(MixLayer(band)), Mix.decode(Mix(listOf(MixLayer(band))).encode())!!.layers)
        val engine = SoundEngine(rate)
        engine.post(SoundEngine.Command.Add(band, 0.3f)); engine.post(SoundEngine.Command.Start)
        engine.render(FloatArray(2 * 512), 512)
        assertEquals(listOf<Sound>(band), engine.sounds)
    }

    private fun peak(voice: ProbeVoice, seconds: Double): Pair<Float, Float> {
        val frames = (rate * seconds).toInt(); val out = FloatArray(2 * frames)
        voice.render(out, frames)
        var l = 0f; var r = 0f
        for (i in 0 until frames) { l = maxOf(l, abs(out[2 * i])); r = maxOf(r, abs(out[2 * i + 1])) }
        return l to r
    }

    @Test fun `the search voice starts from silence, fades in, stays under its ceiling and cuts at once`() {
        val v = ProbeVoice(rate)
        v.set(Probe(4000, level = 0f)); v.start()
        assertEquals(0f, peak(v, 0.5).first) // starts from silence: level 0 plays nothing
        v.set(Probe(4000, level = 1f))
        val early = peak(v, 0.05).first
        val full = peak(v, 3.0).first
        assertTrue("fades in: $early then $full", early < full / 4)
        val ceiling = LevelRules.gainFor(1f) * ProbeVoice.CEILING * ProbeVoice.TONE_AMPLITUDE
        assertTrue("$full over $ceiling", full <= ceiling * 1.01f)
        v.tooLoud()
        peak(v, 0.025)
        assertEquals(0f, peak(v, 0.1).first)
        assertTrue(v.silent)
    }

    @Test fun `the search plays in the chosen ear, and its hiss is as loud at every pitch`() {
        val v = ProbeVoice(rate)
        v.set(Probe(1000, Character.TONE, Ear.LEFT, 0.5f)); v.start(); peak(v, 2.5)
        val (l, r) = peak(v, 0.2)
        assertTrue("$l / $r", l > 0f && r == 0f)
        for (centre in listOf(500, 2000, 8000)) {
            val h = ProbeVoice(rate, seed = 9)
            h.set(Probe(centre, Character.HISS, Ear.BOTH, 1f)); h.start(); peak(h, 2.5)
            val frames = rate; val out = FloatArray(2 * frames); h.render(out, frames)
            var sum = 0.0; for (i in 0 until frames) sum += out[2 * i] * out[2 * i]
            val expected = LevelRules.gainFor(1f) * ProbeVoice.CEILING * TARGET_RMS
            val db = 20 * log10(sqrt(sum / frames) / expected)
            assertTrue("hiss at $centre is $db dB off", abs(db) < 2.0)
        }
    }

    @Test fun `the signpost is stressed for one ear or a heartbeat rhythm, calm otherwise`() {
        assertTrue(Signpost.stressed(Side.LEFT, Rhythm.STEADY))
        assertTrue(Signpost.stressed(Side.BOTH, Rhythm.HEARTBEAT))
        assertTrue(!Signpost.stressed(Side.HEAD, Rhythm.PULSING))
        assertTrue(!Signpost.stressed(null, null))
    }

    private val profile = TinnitusProfile(
        id = "p1", name = "Invented whistle", character = Character.TONE, side = Side.BOTH, rhythm = Rhythm.STEADY,
        loudness = 4, bother = 3, updatedAt = 100,
        matches = listOf(PitchMatch("m1", "2026-01-03", 4000, Character.TONE, true), PitchMatch("m2", "2026-01-05", 4500, Character.HISS, false)),
    )

    @Test fun `profiles travel in the export and come back as they were`() {
        val file = ExportFormat.build("2026-01-06T00:00:00Z", "test", emptyList(), emptyList(), profiles = listOf(profile))
        val text = ExportFormat.encode(file)
        assertTrue(text, "\"schemaVersion\": 1" in text && "\"character\": \"tone\"" in text && "\"octaveChecked\": true" in text)
        assertEquals(listOf(profile), ExportFormat.toProfiles(ExportFormat.read(text)))
        // A file without profiles (every earlier one) still reads, and writes none.
        assertTrue("profiles" !in ExportFormat.encode(ExportFormat.build("x", "test", emptyList(), emptyList())))
        // A value a later Brume adds reads as unset rather than failing the file.
        val later = ExportFormat.toProfile(ProfileV1("p2", "x", character = "buzz", matches = listOf(MatchV1("m", "2026-01-01", 3000, "chirp"))))
        assertNull(later.character); assertTrue(later.matches.isEmpty())
    }

    private fun file(vararg p: ProfileV1) = ExportFile(exportedAt = "", appVersion = "", checkIns = emptyList(), sessions = emptyList(), profiles = p.toList())

    @Test fun `sync merges profiles by id, later save wins, matches are a union and a removal stays removed`() {
        val base = ExportFormat.toV1(profile)
        val renamed = base.copy(name = "Renamed", updatedAt = 200, matches = base.matches.take(1))
        val another = base.copy(updatedAt = 150, matches = base.matches + MatchV1("m3", "2026-01-07", 4200, "tone"))
        val merged = SyncMerge.merge(file(renamed), file(another), "", "")
        val p = merged.profiles!!.single()
        assertEquals("Renamed", p.name)
        assertEquals(listOf("m1", "m2", "m3"), p.matches.map { it.id })
        assertEquals(merged.profiles, SyncMerge.merge(file(another), file(renamed), "", "").profiles)

        val removed = ExportFormat.toV1(profile.tombstone(300))
        val after = SyncMerge.merge(file(removed), file(another), "", "").profiles!!.single()
        assertTrue(after.deleted && after.matches.isEmpty() && after.name.isEmpty())
        val changes = SyncMerge.changesFor(file(another), SyncMerge.merge(file(another), file(removed), "", ""))
        assertEquals(listOf(after), changes.profiles)
        assertTrue(SyncMerge.changesFor(file(renamed), file(renamed)).isEmpty)
    }
}
