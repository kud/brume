package app.beansontoast.brume.launch

import app.beansontoast.brume.ui.launch.IntroKind
import app.beansontoast.brume.ui.launch.LaunchCounter
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which launch gets the title card's full hold, the short one, or none. */
class LaunchKindTest {
    @Test fun firstActivityOfAProcessIsColdThenWarmAndRecreatedIsNone() {
        val c = LaunchCounter()
        assertEquals(IntroKind.COLD, c.kindFor(restored = false))
        assertEquals(IntroKind.WARM, c.kindFor(restored = false))
        assertEquals(IntroKind.NONE, c.kindFor(restored = true))
    }
}
