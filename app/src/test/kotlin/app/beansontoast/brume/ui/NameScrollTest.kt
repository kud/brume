package app.beansontoast.brume.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The scene names' row brings the chosen name in whole, with its margin, and moves no further. */
class NameScrollTest {
    private val widths = listOf(300, 280, 330, 290) // Sleep, Settle, Breathe, Tones at xxhdpi
    private val margin = 36f
    private val viewport = 1080 // content is 1272 wide, so 192 to scroll

    @Test fun lastNameIsWholeWithItsMargin() = assertEquals(192, nameScroll(widths, 3, margin, viewport, 0))
    @Test fun firstNameScrollsBackToTheStart() = assertEquals(0, nameScroll(widths, 0, margin, viewport, 192))
    @Test fun aNameAlreadyInViewDoesNotMove() = assertEquals(40, nameScroll(widths, 1, margin, viewport, 40))
    @Test fun neverPastTheEnd() = assertEquals(0, nameScroll(listOf(100, 100), 1, margin, viewport, 0))
    @Test fun unknownNameStays() = assertEquals(12, nameScroll(widths, -1, margin, viewport, 12))
}
