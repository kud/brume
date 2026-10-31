package app.beansontoast.brume.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.beansontoast.brume.R
import app.beansontoast.design.SelectChip
import androidx.compose.ui.unit.dp

/**
 * The app's language, read from and written to the system (per-app locale, Android 13+). Brume
 * stores nothing: the system is the source of truth, and the choice never syncs (ADR 0001).
 * [tag] is "" for the system default, else a language from `res/xml/locales_config.xml`.
 */
class LanguageChoice(val tag: String, val onPick: (String) -> Unit)

/** Each language is named in itself and reads the same in every locale; only "System default" is translated. */
private val languages = listOf("" to R.string.language_system, "en" to R.string.language_en, "fr" to R.string.language_fr)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageSettings(choice: LanguageChoice) {
    app.beansontoast.design.SettingsGroup(stringResource(R.string.language_title)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (tag, label) ->
                SelectChip(stringResource(label), choice.tag == tag, { if (choice.tag != tag) choice.onPick(tag) })
            }
        }
    }
}

@RequiresApi(33)
internal fun currentLanguage(context: Context): String {
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    return if (locales.isEmpty) "" else locales[0].language
}

@RequiresApi(33)
internal fun applyLanguage(context: Context, tag: String) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}
