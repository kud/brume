package app.beansontoast.brume.core.region

import java.time.LocalDate

/**
 * Where someone lives, kept apart from the language they read (Erwann, 2026-10-31: « je suis
 * français et j'habite à Londres »). It picks the numbers and care routes Brume shows; the language
 * only picks the words around them. [iso] is ISO 3166-1 alpha-2, as the SIM and the locale give it.
 * [OTHER] is every country without a table entry: it gets the generic fallback.
 */
enum class Country(val iso: String) {
    FR("FR"),
    UK("GB"),
    OTHER("");

    companion object {
        /** The country for an ISO code, any case; "UK" is accepted beside "GB". Unknown or blank is [OTHER]. */
        fun fromIso(iso: String?): Country {
            val code = iso?.trim()?.uppercase().orEmpty()
            if (code == "UK") return UK
            return entries.firstOrNull { it.iso.isNotEmpty() && it.iso == code } ?: OTHER
        }
    }
}

/** What a line of help is for. A screen asks for needs; each country answers with its own lines. */
enum class Need { EMERGENCY, TEXT_EMERGENCY, URGENT, CRISIS, SUDDEN_HEARING, CARE, ASSOCIATION, EARPLUGS }

/**
 * A number as written ([shown]) and as dialled ([dial]: digits, with a leading + when it is meant
 * to be called from abroad). [text] marks a number that takes text messages, so the app opens a
 * message rather than the dialler. Nothing is ever placed or sent automatically.
 */
data class Phone(val shown: String, val text: Boolean = false) {
    val dial: String get() = shown.filter { it.isDigit() || it == '+' }
}

/**
 * One line of help. Its words are the string resource `region_<key>`, in English and French, with
 * `%1$s`, `%2$s`… standing for [phones] in order; the facts (numbers) live here, the sentences in
 * the app. [checked] is when a person last checked it against its [sources]; a test fails once any
 * line is more than a year old, so a stale number can't ship quietly.
 */
data class Resource(
    val key: String,
    val need: Need,
    val phones: List<Phone> = emptyList(),
    val checked: LocalDate,
    val sources: String,
)

/**
 * The bundled table: offline, updated by release, never fetched (Brume has no network). Facts follow
 * atelier's regional-resources.md and its "Verified 2026-10-31" section; nothing still marked
 * unverified there is shipped (UK 112 equivalence, Northern Ireland's route, NHS cover for custom
 * plugs, findahelpline.com, France Acouphènes' closing time).
 */
object Regions {
    private val verified = LocalDate.of(2026, 10, 31)

    private val table: Map<Country, List<Resource>> = mapOf(
        Country.UK to listOf(
            Resource("uk_emergency", Need.EMERGENCY, listOf(Phone("999")), verified, "NHS"),
            Resource("uk_text_emergency", Need.TEXT_EMERGENCY, listOf(Phone("999", text = true), Phone("999", text = true)), verified, "emergencySMS · RNID"),
            Resource("uk_urgent", Need.URGENT, listOf(Phone("111")), verified, "nhs.uk"),
            Resource("uk_crisis", Need.CRISIS, listOf(Phone("111"), Phone("116 123")), verified, "nhs.uk · Samaritans"),
            Resource("uk_sudden", Need.SUDDEN_HEARING, listOf(Phone("111"), Phone("999")), verified, "nhs.uk · NICE NG98 §1.1.2"),
            Resource("uk_care", Need.CARE, checked = verified, sources = "nhs.uk · NHS audiology"),
            Resource("uk_association", Need.ASSOCIATION,
                listOf(Phone("0800 018 0527"), Phone("0786 000 8241", text = true), Phone("0808 808 0123"), Phone("07360 268 988", text = true)),
                verified, "Tinnitus UK · RNID"),
            Resource("uk_earplugs", Need.EARPLUGS, checked = verified, sources = "Control of Noise at Work Regulations 2005"),
        ),
        Country.FR to listOf(
            Resource("fr_emergency", Need.EMERGENCY, listOf(Phone("15"), Phone("112")), verified, "sante.fr"),
            Resource("fr_text_emergency", Need.TEXT_EMERGENCY, listOf(Phone("114", text = true)), verified, "gendarmerie.interieur.gouv.fr"),
            Resource("fr_urgent", Need.URGENT, listOf(Phone("116 117"), Phone("15")), verified, "CSP R6315-3"),
            Resource("fr_crisis", Need.CRISIS, listOf(Phone("3114")), verified, "3114.fr · sante.fr"),
            Resource("fr_sudden", Need.SUDDEN_HEARING, listOf(Phone("15")), verified, "La Revue du praticien"),
            Resource("fr_care", Need.CARE, checked = verified, sources = "ameli.fr · Mon soutien psy"),
            Resource("fr_association", Need.ASSOCIATION, listOf(Phone("01 42 05 01 46")), verified, "France Acouphènes"),
            Resource("fr_earplugs", Need.EARPLUGS, checked = verified, sources = "Code du travail R4431-2, R4434-7"),
        ),
        Country.OTHER to listOf(
            Resource("other_emergency", Need.EMERGENCY, listOf(Phone("112")), verified, "European Commission, 112"),
            Resource("other_urgent", Need.URGENT, checked = verified, sources = "NICE NG155"),
            Resource("other_crisis", Need.CRISIS, checked = verified, sources = "WHO"),
            Resource("other_sudden", Need.SUDDEN_HEARING, checked = verified, sources = "NICE NG98 · AAO-HNSF"),
            Resource("other_care", Need.CARE, checked = verified, sources = "NICE NG155"),
            Resource("other_association", Need.ASSOCIATION, checked = verified, sources = "Tinnitus UK · France Acouphènes"),
            Resource("other_earplugs", Need.EARPLUGS, checked = verified, sources = "WHO–ITU H.870"),
        ),
    )

    /**
     * What a country's association offers a reader of its language who lives elsewhere: listening
     * lines and support only, dialled from abroad. Never an emergency, urgent or crisis number, which
     * only work at home.
     */
    private val abroad: Map<Country, List<Resource>> = mapOf(
        Country.FR to listOf(
            Resource("fr_association_abroad", Need.ASSOCIATION, listOf(Phone("+33 1 42 05 01 46")), verified, "France Acouphènes"),
        ),
    )

    /**
     * The country a language comes from, where that is one country. English has none: it is spoken
     * at home in too many places, and the UK's helplines are freephone numbers that don't take calls
     * from abroad.
     */
    private val homeOf = mapOf("fr" to Country.FR)

    /** Every line, for tests: the table and the abroad lines. */
    val all: List<Resource> get() = table.values.flatten() + abroad.values.flatten()

    /** The countries with a table of their own, for the picker. */
    val supported: List<Country> = listOf(Country.FR, Country.UK)

    /** A country's lines for [needs], in the order asked. A need the country lacks falls back to [Country.OTHER]'s. */
    fun lines(country: Country, needs: List<Need>): List<Resource> = needs.flatMap { need ->
        table.getValue(country).filter { it.need == need }.ifEmpty { table.getValue(Country.OTHER).filter { it.need == need } }
    }

    /**
     * "Also in your language": the home country's association when the reader's [language] belongs
     * to a country other than the one they live in. Empty otherwise.
     */
    fun alsoInYourLanguage(country: Country, language: String): List<Resource> {
        val home = homeOf[language.lowercase()] ?: return emptyList()
        return if (home == country) emptyList() else abroad[home].orEmpty()
    }

    /**
     * The first guess, before anyone chooses: the SIM's country, which follows where someone lives,
     * then the phone's region, which follows the language more often (a French phone keeps FR in
     * London), then the fallback.
     */
    fun guess(simIso: String?, localeRegion: String?): Country = when {
        !simIso.isNullOrBlank() -> Country.fromIso(simIso)
        !localeRegion.isNullOrBlank() -> Country.fromIso(localeRegion)
        else -> Country.OTHER
    }

    /** Which needs a Learn card carries under its text. */
    fun forCard(cardId: String): List<Need> = when (cardId) {
        "c5_5" -> listOf(Need.EARPLUGS)
        "c6_1" -> listOf(Need.SUDDEN_HEARING, Need.EMERGENCY, Need.TEXT_EMERGENCY)
        "c6_2" -> listOf(Need.CARE, Need.URGENT)
        "c6_3" -> listOf(Need.CRISIS)
        "c7_2" -> listOf(Need.ASSOCIATION)
        else -> emptyList()
    }

    /** The red flags' block, on Learn and in the introduction: sudden hearing loss, then the emergency and crisis lines. */
    val redFlags: List<Need> = listOf(Need.SUDDEN_HEARING, Need.EMERGENCY, Need.CRISIS)
}
