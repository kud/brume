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
        // (moved first, settled page) as the pager reports them: where it starts, two moves, a settle repeated, a move back.
        val ticks = listOf(false to 0, true to 1, false to 1, true to 2, false to 2, false to 2, true to 1)
            .map { (moved, page) -> if (moved) t.moving(); t.settled(page) }
        assertEquals(listOf(false, true, false, true, false, false, true), ticks)
    }

    @Test fun startingElsewhereIsNoMove() {
        val t = SettleTicker(start = 2)
        assertEquals(false, t.settled(2))
        t.moving()
        assertEquals(true, t.settled(0))
    }

    @Test fun aPageLostWithoutAMoveNeverTicks() {
        val t = SettleTicker(start = 1)
        // The mixer drops a sound: two pages become one, and the pager rests on page 0 untouched.
        assertEquals(false, t.settled(0))
        // The next real swipe, back where it is, is no new page either.
        t.moving()
        assertEquals(false, t.settled(0))
    }

    @Test fun aMoveBackToTheSamePageIsNoTick() {
        val t = SettleTicker(start = 0)
        t.moving()
        assertEquals(false, t.settled(0))
    }
}

class StatusLineTest {
    @Test fun shortPartsHoldTogether() =
        assertEquals("En lecture · encore 12 min · moyen", statusLine(listOf("En lecture", "encore 12 min", "moyen")))

    @Test fun aSentenceStillWraps() =
        assertEquals("Arrêté. Le prochain départ sera plus doux. · moyen", statusLine(listOf("Arrêté. Le prochain départ sera plus doux.", "moyen")))
}
