package app.beansontoast.brume.core

import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Cue
import app.beansontoast.brume.core.breath.LengthUnit
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.breath.Phase
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.breath.Variant
import app.beansontoast.brume.core.breath.Variants
import kotlin.math.abs
import org.junit.Test

class BreathTest {
    private fun Pattern.timing() = steps.map { it.phase to it.seconds }

    @Test fun `the set is in order with the researched timings`() {
        assertEquals(listOf(Technique.LONG_OUT, Technique.SIGH, Technique.HUM, Technique.BOX, Technique.FOUR_SEVEN_EIGHT), Technique.entries)
        assertEquals(Technique.LONG_OUT, Technique.DEFAULT)
        val long = Technique.LONG_OUT
        assertEquals(listOf(Phase.IN to 4.0, Phase.OUT to 6.0), long.pattern(Variant.STANDARD).timing())
        assertEquals(listOf(Phase.IN to 3.0, Phase.OUT to 5.0), long.pattern(Variant.BEGINNER).timing())
        assertEquals(listOf(Phase.IN to 4.0, Phase.HOLD to 2.0, Phase.OUT to 6.0), long.pattern(Variant.HOLD).timing())
        assertEquals(listOf(Phase.IN to 2.0, Phase.TOP to 1.0, Phase.OUT to 6.0), Technique.SIGH.pattern().timing())
        assertEquals(listOf(Phase.IN to 4.0, Phase.OUT to 8.0), Technique.HUM.pattern(Variant.STANDARD).timing())
        assertEquals(listOf(Phase.IN to 3.0, Phase.OUT to 6.0), Technique.HUM.pattern(Variant.BEGINNER).timing())
        assertEquals(listOf(Phase.IN to 4.0, Phase.HOLD to 4.0, Phase.OUT to 4.0, Phase.HOLD to 4.0), Technique.BOX.pattern(Variant.STANDARD).timing())
        assertEquals(listOf(Phase.IN to 4.0, Phase.HOLD to 2.0, Phase.OUT to 4.0, Phase.HOLD to 2.0), Technique.BOX.pattern(Variant.BEGINNER).timing())
        assertEquals(listOf(Phase.IN to 4.0, Phase.HOLD to 7.0, Phase.OUT to 8.0), Technique.FOUR_SEVEN_EIGHT.pattern(Variant.STANDARD).timing())
        assertEquals(listOf(Phase.IN to 2.0, Phase.HOLD to 3.5, Phase.OUT to 4.0), Technique.FOUR_SEVEN_EIGHT.pattern(Variant.BEGINNER).timing())
    }

    @Test fun `beginner 4-7-8 keeps the ratio`() {
        val (a, b) = Technique.FOUR_SEVEN_EIGHT.let { it.pattern(Variant.BEGINNER).steps to it.pattern(Variant.STANDARD).steps }
        a.zip(b).forEach { (x, y) -> assertEquals(0.5, x.seconds / y.seconds) }
    }

    @Test fun `holds start at beginner, the rest at standard`() {
        Technique.entries.forEach { t ->
            val holds = t.pattern().holds
            if (holds) assertEquals(Variant.BEGINNER, t.defaultVariant, "$t")
            if (t.variants.values.any { s -> s.any { it.phase == Phase.HOLD } } && t != Technique.LONG_OUT) assertEquals(Variant.BEGINNER, t.defaultVariant)
        }
        assertFalse(Technique.LONG_OUT.pattern().holds) // the hold is a variant, never the default
        assertFalse(Technique.SIGH.pattern().holds); assertFalse(Technique.HUM.pattern().holds)
    }

    @Test fun `only the techniques without holds suit sleep`() {
        assertEquals(listOf(Technique.LONG_OUT, Technique.SIGH), Technique.entries.filter { it.sleep })
        Technique.entries.filter { it.sleep }.forEach { t -> assertFalse(t.pattern().holds, "$t") }
    }

    @Test fun `each phase has its own cue`() {
        assertEquals(listOf(Cue.IN_NOSE, Cue.TOP_NOSE, Cue.OUT_MOUTH_LONG), Technique.SIGH.pattern().steps.map { it.cue })
        assertEquals(listOf(Cue.IN_NOSE, Cue.HUM), Technique.HUM.pattern().steps.map { it.cue })
        assertEquals(listOf(Cue.IN_NOSE, Cue.HOLD_FULL, Cue.OUT_SLOW, Cue.HOLD_EMPTY), Technique.BOX.pattern().steps.map { it.cue })
        assertEquals(listOf(Cue.IN_NOSE, Cue.HOLD_FULL, Cue.OUT_MOUTH_BLOW), Technique.FOUR_SEVEN_EIGHT.pattern().steps.map { it.cue })
    }

    @Test fun `stored ids come back, and 5-5 and unknown names move to souffle long`() {
        listOf("LONG_OUT", "SIGH", "BOX", "FOUR_SEVEN_EIGHT").forEach { assertEquals(it, Technique.fromStored(it).name) }
        assertEquals(Technique.LONG_OUT, Technique.fromStored("EVEN"))
        assertEquals(Technique.LONG_OUT, Technique.fromStored(null))
        assertEquals(Technique.LONG_OUT, Technique.fromStored("nonsense"))
        Technique.entries.forEach { assertEquals(it, Technique.fromStored(it.name)) }
    }

    @Test fun `variants round-trip and fall back to the default`() {
        val v = Variants().with(Technique.BOX, Variant.STANDARD).with(Technique.LONG_OUT, Variant.HOLD)
        assertEquals(v, Variants.decode(v.encode()))
        assertEquals(Variant.STANDARD, v[Technique.BOX]); assertEquals(Variant.HOLD, v[Technique.LONG_OUT])
        assertEquals(Variant.BEGINNER, v[Technique.FOUR_SEVEN_EIGHT])
        // Nothing stored (every phone before this version), junk, or a variant a technique doesn't offer.
        listOf(null, "", "junk", "SIGH=HOLD", "BOX=NOPE,EVEN=STANDARD").forEach { text ->
            val d = Variants.decode(text)
            Technique.entries.forEach { t -> assertEquals(t.defaultVariant, d[t], "$text $t") }
        }
        assertEquals(Variant.STANDARD, Technique.SIGH.pattern(Variant.BEGINNER).variant)
    }

    @Test fun `lengths are minutes or rounds, in range, and a stored length finds the nearest`() {
        assertEquals(LengthUnit.MINUTES, Technique.LONG_OUT.length.unit)
        assertEquals(3, Technique.LONG_OUT.length.options.min()); assertEquals(20, Technique.LONG_OUT.length.options.max())
        assertEquals(5, Technique.LONG_OUT.length.default)
        assertEquals(LengthUnit.ROUNDS, Technique.HUM.length.unit)
        assertEquals(listOf(5, 7, 9, 11), Technique.HUM.length.options); assertEquals(7, Technique.HUM.length.default)
        assertEquals(1, Technique.BOX.length.options.min()); assertEquals(5, Technique.BOX.length.default)
        assertEquals(listOf(4), Technique.FOUR_SEVEN_EIGHT.length.options)
        assertEquals(3, Technique.LONG_OUT.length.nearest(2)) // the old 2 min
        assertEquals(5, Technique.HUM.length.nearest(4))
    }

    @Test fun `timed patterns run whole breaths to the chosen length`() {
        assertEquals(180.0, BreathPlan.of(Technique.LONG_OUT.pattern(), 3).totalSeconds)
        assertEquals(297.0, BreathPlan.of(Technique.SIGH.pattern(), 5).totalSeconds) // 33 breaths of 9 s
        assertEquals(304.0, BreathPlan.of(Technique.BOX.pattern(Variant.STANDARD), 5).totalSeconds) // 19 breaths of 16 s
        assertEquals(60.0, BreathPlan.of(Technique.BOX.pattern(Variant.BEGINNER), 1).totalSeconds) // 5 breaths of 12 s
    }

    @Test fun `rounds run that many breaths, and 4-7-8 is capped at four`() {
        assertEquals(7, BreathPlan.of(Technique.HUM.pattern(), 7).breaths)
        assertEquals(84.0, BreathPlan.of(Technique.HUM.pattern(), 7).totalSeconds)
        listOf(2, 4, 5, 10).forEach { n ->
            assertEquals(4, BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(Variant.STANDARD), n).breaths)
            assertEquals(76.0, BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(Variant.STANDARD), n).totalSeconds)
        }
        assertEquals(38.0, BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(), 4).totalSeconds)
    }

    @Test fun `phases follow each other, half seconds included`() {
        val sigh = BreathPlan.of(Technique.SIGH.pattern(), 3)
        assertEquals(Phase.IN, sigh.at(0.5).phase); assertEquals(Phase.TOP, sigh.at(2.5).phase); assertEquals(Phase.OUT, sigh.at(3.5).phase)
        val box = BreathPlan.of(Technique.BOX.pattern(Variant.STANDARD), 3)
        assertEquals(listOf(Phase.IN, Phase.HOLD, Phase.OUT, Phase.HOLD), listOf(1.0, 5.0, 9.0, 13.0).map { box.at(it).phase })
        assertTrue(box.at(16.5).breath == 1 && box.at(16.5).phase == Phase.IN)
        val gentle = BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(), 4)
        assertEquals(Phase.HOLD, gentle.at(5.4).phase); assertEquals(Phase.OUT, gentle.at(5.5).phase)
        assertEquals(4, gentle.at(2.0).count) // 3.5 s reads 4, 3, 2, 1
    }

    @Test fun `steps start where the clock says`() {
        val plan = BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(), 4)
        val starts = plan.starts().toList()
        assertEquals(12, starts.size)
        starts.forEach { s -> val m = plan.at(s.at + 1e-6); assertEquals(s.breath, m.breath); assertEquals(s.index, m.step) }
        assertEquals(listOf(0.0, 2.0, 5.5, 9.5), starts.take(4).map { it.at })
    }

    @Test fun `hold progress runs from 0 to 1 within the hold`() {
        val box = BreathPlan.of(Technique.BOX.pattern(Variant.STANDARD), 3)
        assertEquals(0f, box.at(4.0).progress, 1e-3f); assertEquals(0.5f, box.at(6.0).progress, 1e-3f); assertTrue(box.at(7.99).progress > 0.99f)
    }

    @Test fun `fullness never jumps, rests full or empty while held, and ends empty`() {
        Technique.entries.flatMap { t -> t.offered.map { t.pattern(it) } }.forEach { p ->
            val plan = BreathPlan.of(p, p.technique.length.options.first())
            var last = plan.at(0.0).fullness
            var t = 0.0
            while (t < plan.totalSeconds) {
                t += 0.01
                val f = plan.at(t).fullness
                assertTrue(abs(f - last) < 0.02f, "$p jumps at $t: $last -> $f")
                assertTrue(f in 0f..1f)
                last = f
            }
            assertEquals(0f, plan.at(plan.totalSeconds).fullness)
        }
        val box = BreathPlan.of(Technique.BOX.pattern(Variant.STANDARD), 3)
        assertEquals(1f, box.at(6.0).fullness); assertEquals(0f, box.at(14.0).fullness)
        val sigh = BreathPlan.of(Technique.SIGH.pattern(), 3)
        assertEquals(0.8f, sigh.at(2.0).fullness, 1e-3f) // the first in-breath stops short; the top-up fills it
        assertEquals(1f, sigh.at(3.0).fullness, 1e-3f)
    }

    @Test fun `the count reads down to one`() {
        val plan = BreathPlan.of(Technique.LONG_OUT.pattern(), 3)
        assertEquals(listOf(4, 3, 2, 1), listOf(0.1, 1.1, 2.1, 3.1).map { plan.at(it).count })
    }
}
