package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.region.Country
import app.beansontoast.brume.ui.FnFrame
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.CountryChoice
import app.beansontoast.brume.ui.CountrySettings
import app.beansontoast.brume.ui.DoctorGroup
import app.beansontoast.brume.ui.LearnCardScreen
import app.beansontoast.brume.ui.LocalCountry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Country setting (Erwann, 2026-10-31): language and country apart. Erwann's case (French, UK),
 * French in France, English in the UK: the setting, when to see a doctor, the crisis and support
 * cards. 360x780, day. `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*CountryRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class CountryRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    @Test fun render() {
        val (language, iso, screen) = shot.split("-", limit = 3)
        RuntimeEnvironment.setQualifiers("$language-w360dp-h780dp-xxhdpi")
        val country = Country.valueOf(iso.uppercase())
        compose.setContent {
            BrumeTheme(dark = false, night = false) {
                CompositionLocalProvider(LocalCountry provides country) {
                    FnFrame(onClose = {}) {
                        when (screen) {
                            "setting" -> page { CountrySettings(CountryChoice(null, country) {}) }
                            "doctor" -> page { DoctorGroup(onSettings = {}) }
                            else -> LearnCardScreen(Learn.cards.first { it.id == screen }, backLabel = "Comprendre", onBack = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "country-$shot.png"))
    }

    @Composable
    private fun page(content: @Composable () -> Unit) =
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) { content() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf("fr-uk", "fr-fr", "en-uk")
            .flatMap { case -> listOf("setting", "doctor", "c6_1", "c6_3", "c7_2").map { "$case-$it" } }
            .map { arrayOf<Any>(it) }
    }
}
