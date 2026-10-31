package app.beansontoast.brume.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NightHoursTest {
    @Test fun `night runs from 22 to 6`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 22, 23), (0..23).filter(NightHours::contains))
    }
}
