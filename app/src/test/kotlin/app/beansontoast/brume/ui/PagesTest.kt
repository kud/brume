package app.beansontoast.brume.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PagesTest {
    @Test fun rowsFillAPageThenTurn() =
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), pagesOf(listOf(100, 100, 100, 100), budget = 350))

    @Test fun aPageHoldsFourAtMost() =
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4)), pagesOf(List(5) { 10 }, budget = 1000))

    @Test fun aTallRowStillGetsItsPage() =
        assertEquals(listOf(listOf(0), listOf(1)), pagesOf(listOf(500, 100), budget = 300))

    @Test fun nothingMakesNoPages() = assertEquals(emptyList<List<Int>>(), pagesOf(emptyList(), budget = 300))
}

class SettleTickerTest {
    @Test fun aNewPageTicksOnceAndTheStartNever() {
        val t = SettleTicker(start = 0)
        // Settled pages as the pager reports them: where it starts, two moves, a settle repeated, a move back.
        val ticks = listOf(0, 1, 1, 2, 2, 2, 1).map(t::settled)
        assertEquals(listOf(false, true, false, true, false, false, true), ticks)
    }

    @Test fun startingElsewhereIsNoMove() {
        val t = SettleTicker(start = 2)
        assertEquals(false, t.settled(2))
        assertEquals(true, t.settled(0))
    }
}

class StatusLineTest {
    @Test fun shortPartsHoldTogether() =
        assertEquals("En lecture · encore 12 min · moyen", statusLine(listOf("En lecture", "encore 12 min", "moyen")))

    @Test fun aSentenceStillWraps() =
        assertEquals("Arrêté. Le prochain départ sera plus doux. · moyen", statusLine(listOf("Arrêté. Le prochain départ sera plus doux.", "moyen")))
}
