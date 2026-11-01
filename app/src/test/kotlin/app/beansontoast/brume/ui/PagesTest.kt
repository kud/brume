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
