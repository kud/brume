package app.beansontoast.brume.core

import app.beansontoast.brume.core.learn.Decibels
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.learn.Measure
import app.beansontoast.brume.core.learn.Advice
import app.beansontoast.brume.core.learn.Rounded
import app.beansontoast.brume.core.learn.SafeTime
import app.beansontoast.brume.core.learn.Strain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecibelsTest {
    private fun hours(db: Int) = (Decibels.safeTime(db) as SafeTime.Daily).seconds / 3600

    @Test fun `niosh's rule, 85 dBA for 8 h halved every 3 dB`() {
        assertEquals(8.0, hours(85), 1e-9)
        assertEquals(4.0, hours(88), 1e-9)
        assertEquals(1.0, hours(94), 1e-9)
        assertEquals(0.25, hours(100), 1e-9)
        assertEquals(2.52, hours(90), 0.01)
        assertEquals(9.0, Decibels.safeTime(120).let { (it as SafeTime.Daily).seconds }, 0.2)
    }

    @Test fun `quiet has no limit, the ceiling has no time`() {
        assertEquals(SafeTime.NoLimit, Decibels.safeTime(20))
        assertEquals(SafeTime.NoLimit, Decibels.safeTime(70))
        assertEquals(SafeTime.MoreThanEightHours, Decibels.safeTime(80))
        assertEquals(SafeTime.Never, Decibels.safeTime(140))
    }

    @Test fun `times round for reading`() {
        assertEquals(Rounded(hours = 2, minutes = 30), Decibels.rounded(hours(90) * 3600))
        assertEquals(Rounded(hours = 8), Decibels.rounded(8 * 3600.0))
        assertEquals(Rounded(minutes = 15), Decibels.rounded(15 * 60.0))
        assertEquals(Rounded(seconds = 9), Decibels.rounded(8.86))
        assertEquals(Rounded(underASecond = true), Decibels.rounded(0.3))
        assertEquals(Rounded(hours = 1), Decibels.rounded(3599.0 + 120))
    }

    @Test fun `the scale holds the verified levels, and the rocket is marked unweighted`() {
        val by = Decibels.everyday.associateBy { it.id }
        assertEquals(20, by.getValue("leaf").db)
        assertEquals(103, by.getValue("drums").db)
        assertEquals(136, by.getValue("rocket").db)
        assertEquals(Measure.UNWEIGHTED, by.getValue("rocket").measure)
        assertEquals(listOf("rocket"), Decibels.everyday.filter { it.measure == Measure.UNWEIGHTED }.map { it.id })
        assertEquals(Decibels.everyday.sortedBy { it.db }, Decibels.everyday)
    }

    @Test fun `the dial snaps to whole detents and shows the nearest sound`() {
        assertEquals(90, Decibels.snap(91.4f))
        assertEquals(140, Decibels.snap(170f))
        assertEquals(0, Decibels.snap(-3f))
        assertEquals(null, Decibels.nearest(10))
        assertEquals("leaf", Decibels.nearest(20)?.id)
        assertEquals("conversation", Decibels.nearest(60)?.id)
        assertEquals("rocket", Decibels.nearest(135)?.id)
        (Decibels.MIN..Decibels.MAX step Decibels.STEP).forEach { Decibels.safeTime(it); Decibels.strain(it) }
    }

    @Test fun `strain, sand and loudness move one way`() {
        assertEquals(Strain.REST, Decibels.strain(60))
        assertEquals(Strain.TIRING, Decibels.strain(90))
        assertEquals(Strain.FAST, Decibels.strain(100))
        assertEquals(Strain.TOO_LOUD, Decibels.strain(140))
        assertEquals(1f, Decibels.sand(80))
        assertEquals(0f, Decibels.sand(140))
        val levels = (0..140 step 5).toList()
        assertTrue(levels.zipWithNext().all { (a, b) -> Decibels.strain(a) <= Decibels.strain(b) && Decibels.sand(a) >= Decibels.sand(b) })
        assertEquals(2.0, Decibels.loudness(70), 1e-9)
    }

    @Test fun `every advice card leads to a Learn card, once each`() {
        Advice.cards.forEach { assertTrue(it.id, Learn.card(it.card) != null) }
        assertEquals(Advice.cards.size, Advice.cards.map { it.id }.toSet().size)
        assertEquals(10, Advice.cards.size)
    }
}
