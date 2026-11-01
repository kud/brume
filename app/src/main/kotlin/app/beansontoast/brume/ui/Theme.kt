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
 * Brume's identity (Iris, 2026-11-01, direction A, karesansui): washi by day, graphite at night,
 * warm neutrals at very low chroma, meaning by line and weight. Earlier (2026-10-31): night mist. Cool blue-grey neutrals at low chroma carry
 * everything; the brand hue, a pale mist blue, lives only in `primary` (Gamme). The one strong
 * thing on Listen is the ink Play disc. The serif is the system's, kept for the wordmark and
 * screen titles; everything you read to act is the system sans.
 */
private val night = darkColorScheme(
    primary = Color(0xFFCFC8BA), onPrimary = Color(0xFF23211C),
    primaryContainer = Color(0xFF34322D), onPrimaryContainer = Color(0xFFE9E4DA),
    secondary = Color(0xFFBDB8AF), onSecondary = Color(0xFF22201C),
    secondaryContainer = Color(0xFF3E3C37), onSecondaryContainer = Color(0xFFF2EFE9),
    tertiary = Color(0xFFC4C0B6), onTertiary = Color(0xFF26241F),
    background = Color(0xFF0E0E0D), onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF0E0E0D), onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF2A2926), onSurfaceVariant = Color(0xFFADA89F),
    surfaceContainerLowest = Color(0xFF090908), surfaceContainerLow = Color(0xFF121211),
    surfaceContainer = Color(0xFF181816), surfaceContainerHigh = Color(0xFF20201D),
    surfaceContainerHighest = Color(0xFF2A2926),
    inverseSurface = Color(0xFFE6E2DA), inverseOnSurface = Color(0xFF181816), inversePrimary = Color(0xFF4A463F),
    outline = Color(0xFF807B73), outlineVariant = Color(0xFF3D3B37),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

private val dawn = lightColorScheme(
    primary = Color(0xFF4A463F), onPrimary = Color(0xFFFBF8F2),
    primaryContainer = Color(0xFFDDD6C8), onPrimaryContainer = Color(0xFF23211D),
    secondary = Color(0xFF5C5850), onSecondary = Color(0xFFFBF8F2),
    secondaryContainer = Color(0xFFD8D1C3), onSecondaryContainer = Color(0xFF1E1C19),
    tertiary = Color(0xFF5E5A52), onTertiary = Color(0xFFFBF8F2),
    background = Color(0xFFF2EDE3), onBackground = Color(0xFF23211D),
    surface = Color(0xFFF2EDE3), onSurface = Color(0xFF23211D),
    surfaceVariant = Color(0xFFE0DACD), onSurfaceVariant = Color(0xFF5A564F),
    surfaceContainerLowest = Color(0xFFFBF8F2), surfaceContainerLow = Color(0xFFEEE8DD),
    surfaceContainer = Color(0xFFE8E2D6), surfaceContainerHigh = Color(0xFFE2DBCE),
    surfaceContainerHighest = Color(0xFFDCD5C7),
    inverseSurface = Color(0xFF2E2C28), inverseOnSurface = Color(0xFFF2EDE3), inversePrimary = Color(0xFFCFC8BA),
    outline = Color(0xFF7A756C), outlineVariant = Color(0xFFCDC6B8),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
)

val BrumeColors = GammeColors(light = dawn, dark = night)

private fun brumeTypography(): Typography {
    val base = gammeTypography()
    val serif = FontFamily.Serif
    return base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = serif, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.Light),
        headlineSmall = base.headlineSmall.copy(fontFamily = serif, fontWeight = FontWeight.Light),
    )
}

/** Night forces dark, true black and the dim tier; otherwise the system decides. */
@Composable
fun BrumeTheme(dark: Boolean, night: Boolean, content: @Composable () -> Unit) {
    GammeTheme(colors = BrumeColors, darkTheme = dark || night, trueBlack = true, dim = night, typography = brumeTypography(), content = content)
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
