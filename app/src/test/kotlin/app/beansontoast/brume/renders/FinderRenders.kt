package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.finder.Character
import app.beansontoast.brume.core.finder.Ear
import app.beansontoast.brume.core.finder.OctaveCheck
import app.beansontoast.brume.core.finder.PitchMatch
import app.beansontoast.brume.core.finder.Rhythm
import app.beansontoast.brume.core.finder.Side
import app.beansontoast.brume.core.finder.TinnitusProfile
import app.beansontoast.brume.core.sound.MixLayer
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.FinderActions
import app.beansontoast.brume.ui.FinderDraft
import app.beansontoast.brume.ui.FinderPlace
import app.beansontoast.brume.ui.FinderScreen
import app.beansontoast.brume.ui.FinderStep
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.NowPlaying
import app.beansontoast.brume.ui.Tab
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * « Mon acouphène »: the conditions, the fine dial, the octave check, the safety note, a profile with
 * its range across three searches, and the comfort layer in the mixer. Invented values, French,
 * 360x780, night and day. `BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*FinderRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class FinderRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val profile = TinnitusProfile(
        id = "p1", name = "Sifflement du soir", character = Character.TONE, side = Side.BOTH, rhythm = Rhythm.STEADY,
        loudness = 4, bother = 3,
        matches = listOf(
            PitchMatch("m1", "2026-01-03", 4200, Character.TONE, true),
            PitchMatch("m2", "2026-01-06", 4760, Character.TONE, true),
            PitchMatch("m3", "2026-01-09", 4490, Character.HISS, false),
        ),
    )
    private val searching = FinderDraft(ear = Ear.LEFT, level = 0.3f, coarse = 4000, hz = 4490, searched = true)

    @Test fun render() {
        val night = shot.endsWith("-night")
        when (shot.removeSuffix("-night").removeSuffix("-day")) {
            "home" -> finder(FinderPlace.Home)
            "welcome" -> finder(FinderPlace.Journey(FinderStep.WELCOME))
            "conditions" -> finder(FinderPlace.Journey(FinderStep.CONDITIONS))
            "level" -> finder(FinderPlace.Journey(FinderStep.LEVEL, searching))
            "coarse" -> finder(FinderPlace.Journey(FinderStep.COARSE, searching))
            "pitch-dial" -> finder(FinderPlace.Journey(FinderStep.FINE, searching))
            "octave-check" -> finder(FinderPlace.Journey(FinderStep.OCTAVE, searching.copy(octave = OctaveCheck.Choice.SAME)))
            "describe" -> finder(FinderPlace.Journey(FinderStep.DESCRIBE, searching.copy(side = Side.LEFT, rhythm = Rhythm.STEADY)))
            "rate" -> finder(FinderPlace.Journey(FinderStep.RATE, searching.copy(loudness = 4)))
            "safety" -> finder(FinderPlace.Journey(FinderStep.SAFETY, searching.copy(side = Side.LEFT)))
            "profile-range" -> finder(FinderPlace.Profile("p1"))
            "comfort-layer" -> page {
                val band = profile.comfort!!
                val ui = PlayerUi(mix = listOf(MixLayer(Sound.Noise(NoiseColour.PINK)), MixLayer(Sound.Band(band.centre, band.widthTenths))),
                    levels = mapOf(Sound.Noise(NoiseColour.PINK) to 0.3f, Sound.Band(band.centre, band.widthTenths) to 0.25f))
                NowPlaying(ui, ListenActions())
            }
            else -> error(shot)
        }.let { show(night, it) }
    }

    private fun finder(place: FinderPlace): @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            FinderScreen(listOf(profile, profile.copy(id = "p2", name = "Souffle", matches = profile.matches.take(1))), FinderActions(), onBack = {}, start = place)
        }
    }

    private fun page(content: @Composable () -> Unit): @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
    }

    private fun show(night: Boolean, content: @Composable () -> Unit) {
        compose.setContent { BrumeTheme(dark = night, night = night) { BrumeScaffold(Tab.LEARN, {}, Modules()) { content() } } }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { it -> v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "finder-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf("home", "welcome", "conditions", "level", "coarse", "pitch-dial", "octave-check", "describe", "rate",
            "safety", "profile-range", "comfort-layer")
            .flatMap { listOf("$it-night", "$it-day") }
            .map { arrayOf<Any>(it) }
    }
}
