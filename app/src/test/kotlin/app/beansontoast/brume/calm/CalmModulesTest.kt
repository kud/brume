package app.beansontoast.brume.calm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.beansontoast.brume.Modules
import app.beansontoast.brume.Settings
import app.beansontoast.brume.core.calm.Guided
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.lines
import app.beansontoast.brume.ui.shows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Calm (ADR 0003): existing Breathe choices carry over, an older sync file never switches Meditation off. */
@RunWith(RobolectricTestRunner::class)
class CalmModulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Test fun `a fresh install has both practices and the Calm tab`() {
        prefs().edit().clear().commit()
        val m = Settings(context).modules.value
        assertTrue(m.breath && m.meditation && m.tones)
        assertTrue(m.shows(Tab.CALM))
    }

    @Test fun `Breathe switched off before Calm stays off, and Meditation arrives on`() {
        // Written by dev.9: only the two switches it knew, and when they changed.
        prefs().edit().clear().putBoolean("module_breath", false).putBoolean("module_tones", true).putLong("modules_at", 1_000L).commit()
        val settings = Settings(context)
        val m = settings.modules.value
        assertFalse(m.breath)
        assertTrue(m.meditation)
        assertTrue("Calm shows for Meditation", m.shows(Tab.CALM))
        assertEquals(1_000L, settings.modulesAt)
    }

    @Test fun `Breathe left on before Calm stays on`() {
        prefs().edit().clear().putBoolean("module_breath", true).putLong("modules_at", 1_000L).commit()
        assertTrue(Settings(context).modules.value.breath)
    }

    @Test fun `switches survive a restart`() {
        prefs().edit().clear().commit()
        Settings(context).setModules(Modules(breath = true, meditation = false, tones = false))
        assertEquals(Modules(breath = true, meditation = false, tones = false), Settings(context).modules.value)
    }

    @Test fun `Calm goes only when both practices are off`() {
        assertTrue(Modules(breath = false, meditation = true).shows(Tab.CALM))
        assertTrue(Modules(breath = true, meditation = false).shows(Tab.CALM))
        assertFalse(Modules(breath = false, meditation = false).shows(Tab.CALM))
    }

    @Test fun `an older sync file without Meditation never switches it off`() {
        val old = mapOf("TONES" to true, "BREATH" to false)
        assertEquals(Modules(tones = true, breath = false, meditation = true), Modules.fromWire(old))
        val now = Modules(breath = false, meditation = false).toWire()
        assertEquals(false, now["MEDITATION"])
        assertEquals(Modules(breath = false, meditation = false), Modules.fromWire(now))
    }

    @Test fun `every guided step has its line, and the first says it can stop`() {
        Guided.entries.forEach { g ->
            val lines = g.lines()
            assertEquals("$g", g.steps.size, lines.size)
            assertEquals("$g lines are distinct", lines.size, lines.toSet().size)
            listOf("End", "Terminer").zip(listOf(context, localized("fr"))).forEach { (word, c) ->
                val first = c.getString(lines.first())
                assertTrue("$g: \"$first\"", first.contains(word))
            }
        }
    }

    private fun localized(tag: String): Context {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config)
    }
}
