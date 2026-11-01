package app.beansontoast.brume.core

import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Cue
import app.beansontoast.brume.core.breath.Phase
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.breath.Variant
import app.beansontoast.brume.core.calm.GUIDED_MINUTES
import app.beansontoast.brume.core.calm.Guided
import app.beansontoast.brume.core.calm.Part
import app.beansontoast.brume.core.guide.BreathGuide
import app.beansontoast.brume.core.guide.Line
import app.beansontoast.brume.core.guide.MeditationGuide
import app.beansontoast.brume.core.guide.Spoken
import app.beansontoast.brume.core.guide.Thinning
import org.junit.Test

class GuideTest {
    private val allPatterns = Technique.entries.flatMap { t -> t.offered.map { t.pattern(it) } }

    @Test fun `thinning speaks the first three breaths, then every third`() {
        val t = Thinning()
        assertEquals(listOf(0, 1, 2, 5, 8, 11), (0..12).filter { t.speaks(it) })
        assertEquals((0..12).toList(), (0..12).filter { Thinning(fullBreaths = 100).speaks(it) })
    }

    @Test fun `each spoken breath says every step's cue at the step's start`() {
        val plan = BreathPlan.of(Technique.BOX.pattern(Variant.STANDARD), 3)
        val lines = BreathGuide(plan).lines
        assertEquals(listOf(0.0 to Cue.IN_NOSE, 4.0 to Cue.HOLD_FULL, 8.0 to Cue.OUT_SLOW, 12.0 to Cue.HOLD_EMPTY),
            lines.take(4).map { it.at to it.what })
        // Breaths 1, 2, 3 then 6: nothing is said in breaths 4 and 5.
        assertEquals(listOf(0, 1, 2, 5, 8), lines.map { (it.at / 16).toInt() }.distinct())
    }

    @Test fun `nothing is ever said inside a hold, only at its start`() {
        allPatterns.forEach { p ->
            val plan = BreathPlan.of(p, p.technique.length.options.last())
            BreathGuide(plan, Thinning(fullBreaths = 1000)).lines.forEach { s ->
                val m = plan.at(s.at)
                assertEquals(0.0, m.stepElapsed, "$p at ${s.at}")
                if (m.phase == Phase.HOLD) assertTrue(s.what == Cue.HOLD_FULL || s.what == Cue.HOLD_EMPTY)
            }
        }
    }

    @Test fun `a ticking clock hears every line once`() {
        val plan = BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(), 4)
        val guide = BreathGuide(plan)
        val heard = mutableListOf<Spoken<Cue>>()
        var last = -1.0; var now = 0.0
        while (now <= plan.totalSeconds + 1) { heard += guide.between(last, now); last = now; now += 0.033 }
        assertEquals(guide.lines, heard)
    }

    @Test fun `the current line shows for a while, then goes`() {
        val guide = MeditationGuide(Guided.BREATH, 5)
        assertEquals(null, guide.current(1.0, 12.0))
        assertEquals(Line.ARRIVE_POSTURE, guide.current(5.0, 12.0)?.what)
        assertEquals(null, guide.current(160.0, 12.0))
    }

    @Test fun `every guided meditation opens, sparsely guides and closes, inside its time`() {
        Guided.entries.forEach { kind ->
            GUIDED_MINUTES.forEach { m ->
                val g = MeditationGuide(kind, m)
                val lines = g.lines
                val total = g.timeline.totalSeconds
                assertEquals(Line.ARRIVE_POSTURE, lines.first().what)
                assertTrue(lines.first().at >= 3.0, "after the start bell has swelled")
                assertEquals(listOf(Line.CLOSE_RETURN, Line.CLOSE_OPEN), lines.takeLast(2).map { it.what })
                assertTrue(lines.last().at <= total - 8.0, "$kind $m: the last line is said before the end bell")
                assertTrue(lines.zipWithNext().all { (a, b) -> b.at - a.at >= 6.0 }, "$kind $m: lines have room, ${lines.map { it.at }}")
                // After the opening and the anchor, at most one line a stretch: silence is most of it.
                val wanders = g.timeline.parts.count { it == Part.WANDER }
                assertEquals(3 + 2 + wanders + 2, lines.size, "$kind $m")
                val opening = lines.filter { it.what.name.startsWith("ARRIVE") }
                assertTrue(opening.last().at <= 45.0)
                val middle = lines.filter { it.at > 100 && it.at < total - 40 }.zipWithNext().map { (a, b) -> b.at - a.at }
                assertTrue(middle.all { it in 40.0..135.0 }, "$kind $m: one cue every one to two minutes, $middle")
            }
        }
    }

    @Test fun `the body is walked from the feet up, and sounds always carry the attention cue`() {
        assertEquals(listOf(Line.BODY_LOWER, Line.BODY_UPPER), MeditationGuide(Guided.BODY, 3).lines.map { it.what }.filter { it.name.startsWith("BODY_") && it != Line.BODY_ANCHOR && it != Line.BODY_NOTICE })
        assertEquals(Line.BODY_FACE, MeditationGuide(Guided.BODY, 10).lines.map { it.what }.last { it.name.startsWith("BODY_") })
        GUIDED_MINUTES.forEach { m -> assertTrue(Line.SOUNDS_WANDER_2 in MeditationGuide(Guided.SOUNDS, m).lines.map { it.what }) }
        GUIDED_MINUTES.forEach { m -> assertTrue(MeditationGuide(Guided.BREATH, m).lines.none { it.what.name.startsWith("BODY") || it.what.name.startsWith("SOUNDS") }) }
    }
}
