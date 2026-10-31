package app.beansontoast.brume.launch

import app.beansontoast.brume.ui.launch.Choreographies
import app.beansontoast.brume.ui.launch.IntroKind
import app.beansontoast.brume.ui.launch.Key
import app.beansontoast.brume.ui.launch.Landing
import app.beansontoast.brume.ui.launch.LaunchCounter
import app.beansontoast.brume.ui.launch.LaunchPose
import app.beansontoast.brume.ui.launch.Lockup
import app.beansontoast.brume.ui.launch.MARK_LINES
import app.beansontoast.brume.ui.launch.Track
import app.beansontoast.brume.ui.launch.choreographyFor
import app.beansontoast.brume.ui.launch.mistLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/** The launch intro's timing and geometry, and that the splash vector and the overlay draw the same lockup. */
class LaunchChoreographyTest {
    private val all = listOf(Choreographies.rise, Choreographies.welcome, Choreographies.warm, Choreographies.coldInPlace, Choreographies.fade)

    @Test fun trackHoldsEndsAndInterpolates() {
        val t = Track(Key(100f, 0f), Key(300f, 10f))
        assertEquals(0f, t.at(0f), 0f)
        assertEquals(5f, t.at(200f), 1e-4f)
        assertEquals(10f, t.at(900f), 0f)
        assertEquals(300f, t.endMs, 0f)
    }

    @Test fun durationsAreTheAgreedOnes() {
        assertEquals(800f, Choreographies.rise.durationMs, 0f)
        assertEquals(800f, Choreographies.welcome.durationMs, 0f)
        assertEquals(300f, Choreographies.warm.durationMs, 0f)
        assertEquals(200f, Choreographies.fade.durationMs, 0f)
    }

    @Test fun everyChoreographyStartsOnTheSplashsOwnPicture() {
        for (c in all) assertEquals("$c at 0 ms", LaunchPose.Resting, c.pose(0f))
    }

    @Test fun everyChoreographyEndsWithNothingLeftOnScreen() {
        for (c in all) {
            val end = c.pose(c.durationMs)
            assertTrue("$c ground gone", end.ground == 0f || end.lift >= 1f)
            assertEquals("$c mark", 0f, end.mark, 1e-4f)
            assertTrue("$c word", end.word <= 1e-4f)
        }
    }

    @Test fun reducedMotionOnlyFades() {
        val c = Choreographies.fade
        for (ms in 0..200 step 10) {
            val p = c.pose(ms.toFloat())
            assertEquals(0f, p.travel, 0f); assertEquals(0f, p.rise, 0f); assertEquals(0f, p.lift, 0f)
        }
    }

    @Test fun choosesByKindMotionAndLanding() {
        assertNull(choreographyFor(IntroKind.NONE, false, Landing.MIST))
        assertEquals(Choreographies.fade, choreographyFor(IntroKind.COLD, true, Landing.WELCOME))
        assertEquals(Choreographies.welcome, choreographyFor(IntroKind.COLD, false, Landing.WELCOME))
        assertEquals(Choreographies.rise, choreographyFor(IntroKind.COLD, false, Landing.MIST))
        assertEquals(Choreographies.coldInPlace, choreographyFor(IntroKind.COLD, false, Landing.NOWHERE))
        assertEquals(Choreographies.warm, choreographyFor(IntroKind.WARM, false, Landing.MIST))
    }

    @Test fun firstActivityOfAProcessIsColdThenWarmAndRecreatedIsNone() {
        val c = LaunchCounter()
        assertEquals(IntroKind.COLD, c.kindFor(restored = false))
        assertEquals(IntroKind.WARM, c.kindFor(restored = false))
        assertEquals(IntroKind.NONE, c.kindFor(restored = true))
    }

    @Test fun lockupFitsTheSplashsVisibleCircle() {
        fun inside(x: Float, y: Float) = assertTrue("($x, $y)", hypot(x - 54f, y - 54f) <= Lockup.SPLASH_RADIUS)
        val mx = { v: Float -> Lockup.MARK_DX + Lockup.MARK_SCALE * v }
        val my = { v: Float -> Lockup.MARK_DY + Lockup.MARK_SCALE * v }
        for (x in listOf(Lockup.BAND_LEFT, Lockup.BAND_RIGHT)) for (y in listOf(Lockup.BAND_TOP, Lockup.BAND_BOTTOM)) inside(mx(x), my(y))
        val wx = { v: Float -> Lockup.WORD_DX + Lockup.WORD_SCALE * v }
        val wy = { v: Float -> Lockup.WORD_DY + Lockup.WORD_SCALE * v }
        for (x in listOf(Lockup.WORD_LEFT, Lockup.WORD_RIGHT)) for (y in listOf(Lockup.WORD_TOP, Lockup.WORD_BOTTOM)) inside(wx(x), wy(y))
        // Centred: the mark's top and the wordmark's foot sit evenly about the icon's middle.
        assertEquals(54f, (my(Lockup.BAND_TOP) + wy(Lockup.WORD_BOTTOM)) / 2f, 0.3f)
        assertEquals(54f, (wx(Lockup.WORD_LEFT) + wx(Lockup.WORD_RIGHT)) / 2f, 0.1f)
    }

    @Test fun splashVectorIsTheOverlaysLockup() {
        val xml = File("src/main/res/drawable/splash_lockup.xml").readText()
        fun group(name: String, attr: String) =
            Regex("""android:name="$name"[^>]*android:$attr="([-\d.]+)"""").find(xml)!!.groupValues[1].toFloat()
        assertEquals(Lockup.MARK_SCALE, group("mark", "scaleX"), 1e-5f)
        assertEquals(Lockup.MARK_SCALE, group("mark", "scaleY"), 1e-5f)
        assertEquals(Lockup.MARK_DX, group("mark", "translateX"), 1e-4f)
        assertEquals(Lockup.MARK_DY, group("mark", "translateY"), 1e-4f)
        assertEquals(Lockup.WORD_SCALE, group("wordmark", "scaleX"), 1e-7f)
        assertEquals(Lockup.WORD_DX, group("wordmark", "translateX"), 1e-4f)
        assertEquals(Lockup.WORD_DY, group("wordmark", "translateY"), 1e-4f)
        val paths = Regex("""pathData="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(Lockup.MARK_PATHS + Lockup.WORD_PATH, paths)
        val mark = Regex("""pathData="([^"]+)"""").findAll(File("src/main/res/drawable/ic_splash_mark.xml").readText()).map { it.groupValues[1] }.toList()
        assertEquals("the lockup's Horizon is the splash mark's", mark, Lockup.MARK_PATHS)
    }

    @Test fun markLinesStayOnTheirOutlines() {
        // The simplified lines the morph starts from sit inside the outlines' band, top to bottom.
        assertEquals(3, MARK_LINES.size)
        for (l in MARK_LINES) {
            assertTrue(l.x0 >= Lockup.BAND_LEFT && l.x1 <= Lockup.BAND_RIGHT)
            assertTrue(minOf(l.y0, l.y1) >= Lockup.BAND_TOP && maxOf(l.y0, l.y1) <= Lockup.BAND_BOTTOM)
        }
    }

    @Test fun mistLinesAreTheMistsOwn() {
        // The values Mist drew before it read mistLines: the intro lands on exactly these.
        val lines = mistLines(1080f, 396f, 0.35f)
        assertEquals(5, lines.size)
        assertEquals(396f * 0.15f, lines[0].y, 1e-3f)
        assertEquals(1080f * 0.55f, lines[0].wavelength, 1e-3f)
        assertEquals((0.10f + 0.22f * 0.35f), lines[2].alpha, 1e-5f)
        assertEquals((0.10f + 0.22f * 0.35f) * 0.5f, lines[4].alpha, 1e-5f)
        assertEquals(1080f * 0.04f, lines[1].from, 1e-3f)
        assertEquals(1080f * (1f - 0.05f * 2), lines[1].to, 1e-3f)
    }
}
