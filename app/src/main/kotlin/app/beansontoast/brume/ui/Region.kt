package app.beansontoast.brume.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.region.Country
import app.beansontoast.brume.core.region.Need
import app.beansontoast.brume.core.region.Phone
import app.beansontoast.brume.core.region.Regions
import app.beansontoast.brume.core.region.Resource
import app.beansontoast.design.SelectChip
import app.beansontoast.design.SettingsGroup

/** The country whose numbers Brume shows: the one chosen in Settings, else the guess. Set once, at the top. */
val LocalCountry = staticCompositionLocalOf { Country.OTHER }

/** The guess: the SIM's country (no permission needed), then the system's region, never the app's language. */
internal fun guessCountry(context: Context): Country = Regions.guess(
    context.getSystemService(TelephonyManager::class.java)?.simCountryIso,
    Resources.getSystem().configuration.locales[0]?.country,
)

/** The chosen country (null: follow [guess]) and how to change it. */
class CountryChoice(val chosen: Country?, val guess: Country, val onPick: (Country?) -> Unit)

@Composable
internal fun countryName(country: Country): String = stringResource(
    when (country) {
        Country.FR -> R.string.country_fr
        Country.UK -> R.string.country_uk
        Country.OTHER -> R.string.country_other
    },
)

/** Settings › Country, beside Language and as plain: the guess first, then each country, then « Autre pays ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountrySettings(choice: CountryChoice) {
    SettingsGroup(stringResource(R.string.country_title)) {
        Text(stringResource(R.string.country_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(stringResource(R.string.country_auto, countryName(choice.guess)), choice.chosen == null, { if (choice.chosen != null) choice.onPick(null) })
            (Regions.supported + Country.OTHER).forEach { c ->
                SelectChip(countryName(c), choice.chosen == c, { if (choice.chosen != c) choice.onPick(c) })
            }
        }
    }
}

@SuppressLint("DiscouragedApi") // Lines are keyed by `:core`'s table, like Learn's cards; RegionStringsTest pins both.
private fun template(context: Context, r: Resource): String {
    val id = context.resources.getIdentifier("region_${r.key}", "string", context.packageName)
    return if (id == 0) "" else context.getString(id)
}

private val slot = Regex("%(\\d+)[$]s")

/** A line's words with each number in place, underlined and tappable: the underline, not a hue, says it's a link. */
@Composable
internal fun regionText(r: Resource): AnnotatedString {
    val context = LocalContext.current
    val text = template(context, r)
    val link = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
    return buildAnnotatedString {
        var at = 0
        slot.findAll(text).forEach { m ->
            append(text.substring(at, m.range.first))
            val phone = r.phones.getOrNull(m.groupValues[1].toInt() - 1)
            if (phone == null) append(m.value)
            else withLink(LinkAnnotation.Clickable("tel:${phone.dial}", link) { reach(context, phone) }) { append(phone.shown) }
            at = m.range.last + 1
        }
        append(text.substring(at))
    }
}

/** Opens the dialler, or a new message for a text line, with the number filled in. Nothing is placed or sent. */
internal fun reach(context: Context, phone: Phone) {
    val intent = if (phone.text) Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.dial}"))
    else Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.dial}"))
    try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
}

/**
 * The country's lines for [needs], one paragraph each; for associations, the « Aussi dans votre
 * langue » line follows when the language belongs to another country (never an urgent number).
 */
@Composable
internal fun RegionLines(needs: List<Need>, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val country = LocalCountry.current
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty()
    val lines = Regions.lines(country, needs)
    val also = if (Need.ASSOCIATION in needs) Regions.alsoInYourLanguage(country, language) else emptyList()
    Column(modifier) {
        lines.forEachIndexed { i, r ->
            Text(regionText(r), style = style, modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp))
        }
        if (also.isNotEmpty()) {
            Text(stringResource(R.string.region_also), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            also.forEach { Text(regionText(it), style = style, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

/** « Numéros pour : Royaume-Uni · Changer »: which country the numbers above are for, and the way to Settings. */
@Composable
internal fun RegionFor(onChange: (() -> Unit)?, modifier: Modifier = Modifier) {
    val name = countryName(LocalCountry.current)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val change = stringResource(R.string.region_change)
    val link = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
    val text = buildAnnotatedString {
        append(stringResource(R.string.region_for, name))
        if (onChange != null) {
            append(" · ")
            withLink(LinkAnnotation.Clickable("country", link) { onChange() }) { append(change) }
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = muted, modifier = modifier)
}
