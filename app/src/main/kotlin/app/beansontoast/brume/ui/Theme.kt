package app.beansontoast.brume.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import app.beansontoast.brume.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.design.GammeColors
import app.beansontoast.design.GammeTheme
import app.beansontoast.design.gammeTypography
import app.beansontoast.brume.ui.launch.mistLines
import kotlin.math.PI
import kotlin.math.sin

/*
 * Brume's identity (Iris, 2026-11-01, the misty-landscape direction that replaces karesansui):
 * soft neutrals with a breath of lavender, dusty blue for the one action, peach and lavender in
 * the sky and the symbols only. Night is the same family at dusk, deep blue-lavender, never black.
 * Meaning never rests on hue: every pair here was checked in greyscale (Erwann is colourblind).
 * One typeface, Instrument Sans (OFL, res/font/instrument_sans.ttf, a variable font).
 */
private val dusk = darkColorScheme(
    primary = Color(0xFFB8C9E8), onPrimary = Color(0xFF1A2740),
    primaryContainer = Color(0xFF34405E), onPrimaryContainer = Color(0xFFDCE4F6),
    secondary = Color(0xFFC9C2E6), onSecondary = Color(0xFF26223F),
    secondaryContainer = Color(0xFF3A3657), onSecondaryContainer = Color(0xFFE8E3F8),
    tertiary = Color(0xFFEBC3B3), onTertiary = Color(0xFF3F2A22),
    tertiaryContainer = Color(0xFF5A4048), onTertiaryContainer = Color(0xFFF8E0D6),
    background = Color(0xFF1B1E33), onBackground = Color(0xFFE9E7F3),
    surface = Color(0xFF1B1E33), onSurface = Color(0xFFE9E7F3),
    surfaceVariant = Color(0xFF353A55), onSurfaceVariant = Color(0xFFB9B8D0),
    surfaceContainerLowest = Color(0xFF161829), surfaceContainerLow = Color(0xFF202339),
    surfaceContainer = Color(0xFF262A42), surfaceContainerHigh = Color(0xFF2D314B),
    surfaceContainerHighest = Color(0xFF353A55),
    inverseSurface = Color(0xFFE9E7F3), inverseOnSurface = Color(0xFF1B1E33), inversePrimary = Color(0xFF4A6890),
    outline = Color(0xFF8D8EA8), outlineVariant = Color(0xFF41456A),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

private val mist = lightColorScheme(
    primary = Color(0xFF4A6890), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4F1), onPrimaryContainer = Color(0xFF1C2A40),
    secondary = Color(0xFF615B88), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E0F0), onSecondaryContainer = Color(0xFF24203A),
    tertiary = Color(0xFF85584A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6DDD2), onTertiaryContainer = Color(0xFF3A2219),
    background = Color(0xFFF3EFF3), onBackground = Color(0xFF23263A),
    surface = Color(0xFFF3EFF3), onSurface = Color(0xFF23263A),
    surfaceVariant = Color(0xFFE4E0EA), onSurfaceVariant = Color(0xFF555970),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F5F8),
    surfaceContainer = Color(0xFFEEEAF0), surfaceContainerHigh = Color(0xFFE8E4EC),
    surfaceContainerHighest = Color(0xFFE2DEE8),
    inverseSurface = Color(0xFF2E3046), inverseOnSurface = Color(0xFFF3EFF3), inversePrimary = Color(0xFFB8C9E8),
    outline = Color(0xFF74788E), outlineVariant = Color(0xFFD3CFDC),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
)

val BrumeColors = GammeColors(light = mist, dark = dusk)

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: Int) = Font(
    R.font.instrument_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Instrument Sans at the four weights Brume uses, all from the one variable file. */
val InstrumentSans = FontFamily(instrument(400), instrument(500), instrument(600), instrument(700))

private fun brumeTypography(): Typography {
    val base = gammeTypography()
    fun TextStyle.sans(weight: FontWeight? = null, tracking: Float? = null) =
        copy(fontFamily = InstrumentSans, fontWeight = weight ?: fontWeight, letterSpacing = tracking?.sp ?: letterSpacing)
    return base.copy(
        displayLarge = base.displayLarge.sans(FontWeight.Medium, -0.5f),
        displayMedium = base.displayMedium.sans(FontWeight.Medium, -0.5f),
        displaySmall = base.displaySmall.sans(FontWeight.Medium, -0.25f),
        headlineLarge = base.headlineLarge.sans(FontWeight.Medium, -0.25f),
        headlineMedium = base.headlineMedium.sans(FontWeight.Medium, 0f),
        headlineSmall = base.headlineSmall.sans(FontWeight.Medium, 0f),
        titleLarge = base.titleLarge.sans(FontWeight.Medium),
        titleMedium = base.titleMedium.sans(FontWeight.SemiBold),
        titleSmall = base.titleSmall.sans(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(FontWeight.Medium),
        labelMedium = base.labelMedium.sans(FontWeight.Medium),
        labelSmall = base.labelSmall.sans(FontWeight.Medium),
    )
}

/** Night forces the dusk scheme and the dim tier; otherwise the system decides. Never true black. */
@Composable
fun BrumeTheme(dark: Boolean, night: Boolean, content: @Composable () -> Unit) {
    GammeTheme(colors = BrumeColors, darkTheme = dark || night, trueBlack = false, dim = night, typography = brumeTypography(), content = content)
}

/** A tiny deterministic sequence for the textures, so a sound always looks the same. */
private class Seq(seed: Int) {
    private var s = seed * 2654435761L + 1013904223L
    fun next(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33) % 10_000L).toFloat() / 10_000f }
}

private fun DrawScope.wave(y: Float, amplitude: Float, wavelength: Float, phase: Float, from: Float, to: Float, color: Color, width: Float) {
    val path = Path()
    var x = from
    path.moveTo(x, y + amplitude * sin(2 * PI.toFloat() * x / wavelength + phase))
    while (x < to) {
        x += 4f
        path.lineTo(x, y + amplitude * sin(2 * PI.toFloat() * x / wavelength + phase))
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/**
 * The mist: long soft lines drifting across. Drawn, not an image. [strength] lifts the lines
 * a little while sound plays; the words say so too, the lines only echo it.
 */
@Composable
fun Mist(modifier: Modifier, strength: Float = 0.5f, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier) {
        // The lines are mistLines (ui/launch), so the launch intro lands exactly on them.
        for (line in mistLines(size.width, size.height, strength)) {
            wave(line.y, line.amplitude, line.wavelength, line.phase, line.from, line.to, color.copy(alpha = line.alpha), 1.5f * density)
        }
    }
}

/** Each sound's own quiet texture. Not a glyph on a tile: the lines are the card's surface. */
fun DrawScope.texture(noise: NoiseColour?, ambience: Ambience?, color: Color) {
    val w = size.width; val h = size.height; val px = density
    when {
        noise == NoiseColour.WHITE -> { // fine, even grain: every frequency at once
            val r = Seq(1)
            repeat(70) {
                val x = r.next() * w; val y = r.next() * h; val l = (3f + 5f * r.next()) * px
                drawLine(color, Offset(x, y), Offset((x + l).coerceAtMost(w), y), 1.2f * px, StrokeCap.Round)
            }
        }
        noise == NoiseColour.PINK -> for (i in 0 until 4) wave(h * (0.2f + 0.2f * i), h * 0.07f, w * 0.32f, i * 1.1f, 0f, w, color, 1.4f * px)
        noise == NoiseColour.BROWN -> for (i in 0 until 2) wave(h * (0.35f + 0.32f * i), h * 0.14f, w * 0.9f, i * 2.2f, 0f, w, color, 2.2f * px)
        noise == NoiseColour.GREY -> for (i in 0 until 3) wave(h * (0.25f + 0.25f * i), h * 0.025f, w * 0.5f, i * 0.8f, 0f, w, color, 1.6f * px)
        ambience == Ambience.RAIN -> {
            val r = Seq(7)
            repeat(26) {
                val x = r.next() * w; val y = r.next() * h * 0.8f; val l = (6f + 6f * r.next()) * px
                drawLine(color, Offset(x, y), Offset(x - l * 0.25f, y + l), 1.3f * px, StrokeCap.Round)
            }
        }
        ambience == Ambience.SEA -> for (i in 0 until 3) {
            val y = h * (0.3f + 0.25f * i)
            val path = Path().apply {
                moveTo(0f, y)
                var x = 0f
                val swell = w / 2.2f
                while (x < w) { quadraticBezierTo(x + swell * 0.5f, y - h * 0.22f, x + swell, y); x += swell }
            }
            drawPath(path, color, style = Stroke(1.4f * px, cap = StrokeCap.Round))
        }
        ambience == Ambience.WIND -> {
            val r = Seq(3)
            repeat(6) { i ->
                val y = h * (0.15f + 0.14f * i); val x0 = r.next() * w * 0.4f; val x1 = x0 + w * (0.4f + 0.4f * r.next())
                wave(y, h * 0.03f, w * 0.7f, i.toFloat(), x0, x1.coerceAtMost(w), color, 1.3f * px)
            }
        }
    }
}
