package app.beansontoast.brume.core.learn

/**
 * How well a card's point is supported. Shown as a shape plus a word, never a hue
 * (atelier experience.md, "A guide, not a settings panel"). Stored nowhere: renaming is free.
 */
enum class Evidence { STRONG, MIXED, LITTLE, MYTH }

/** A journal tag that invites a reader to one Learn card. Its token is what the note stores. */
enum class Tag(val token: String, val card: String) {
    CAFFEINE("#caffeine", "c3_1"),
    ALCOHOL("#alcohol", "c3_2"),
    STRESS("#stress", "c3_5"),
    POOR_SLEEP("#poor-sleep", "c4_1"),
}

/**
 * One Learn card. Its words live in the app's string resources as `learn_<id>_title`,
 * `learn_<id>_body` and, when [tryThis], `learn_<id>_try`, in English and French; the app's
 * guard test checks every card has them and that none makes a claim. [sources] are names of
 * documents, the same in every language. [evidence] is null where the card gives a referral
 * rule or lived experience rather than a finding.
 */
data class Card(
    val id: String,
    val topic: Int,
    val evidence: Evidence?,
    val sources: String,
    val tryThis: Boolean = false,
)

/**
 * The library, in reading order: also the path the home tab walks, one card a day. Facts follow
 * atelier's learn-content.md (2026-10-31), corrected by the "Verified 2026-10-31" sections of
 * decibels.md and regional-resources.md. Left out until their facts are checked: custom plugs
 * in France (5.8, prices and cover unverified). Numbers and care routes live in `:core` `region/`, by country.
 */
object Learn {
    const val TOPICS = 7

    val cards: List<Card> = listOf(
        Card("c1_1", 1, Evidence.STRONG, "HAS 2026 · NICE NG155"),
        Card("c1_2", 1, Evidence.STRONG, "Tinnitus UK, Taming Tinnitus (2026)"),
        Card("c2_1", 2, Evidence.STRONG, "HAS 2026 · NICE NG155 · Cochrane CD012614"),
        Card("c2_2", 2, Evidence.STRONG, "European guideline 2019 · UNITI, Nat Commun 2025"),
        Card("c2_3", 2, Evidence.MIXED, "Cochrane CD013094 · Tinnitus UK (2024) · AAO-HNSF 2026", tryThis = true),
        Card("c3_1", 3, Evidence.MYTH, "St Claire et al., Int J Audiol 2010 · Glicksman et al., Am J Med 2014", tryThis = true),
        Card("c3_2", 3, Evidence.LITTLE, "Biswas et al., JARO 2023", tryThis = true),
        Card("c3_3", 3, Evidence.MIXED, "Veile et al., BMJ Open 2018 · Biswas et al., JARO 2023"),
        Card("c3_4", 3, Evidence.LITTLE, "Cochrane CD012173 · CD015244"),
        Card("c3_5", 3, Evidence.STRONG, "McKenna et al., Psychother Psychosom 2017", tryThis = true),
        Card("c3_6", 3, Evidence.MIXED, "Carpenter-Thompson et al., PLoS One 2015"),
        Card("c3_7", 3, Evidence.LITTLE, "Cochrane CD013514 · CD009832 · HAS 2026", tryThis = true),
        Card("c3_8", 3, Evidence.STRONG, "RNID · Harvard Health"),
        Card("c3_9", 3, Evidence.MIXED, "Trajano da Silva et al., J Clin Med 2023 · Michiels et al. 2016"),
        Card("c3_10", 3, Evidence.STRONG, "NHS · RNID"),
        Card("c4_1", 4, Evidence.STRONG, "Marks et al., Cogn Behav Ther 2023", tryThis = true),
        Card("c4_2", 4, Evidence.MIXED, "Theodoroff et al., AJA 2017 · Tinnitus UK"),
        Card("c5_1", 5, Evidence.STRONG, "NIDCD, How Loud is Too Loud? · EU SCENIHR"),
        Card("c5_2", 5, Evidence.STRONG, "NIDCD · US DoD HCE · décret 2017-1244"),
        Card("c5_3", 5, Evidence.STRONG, "WHO–ITU H.870 (2022)", tryThis = true),
        Card("c5_4", 5, Evidence.MIXED, "NIOSH (2017) · AOSP, Sound dose"),
        Card("c5_5", 5, Evidence.STRONG, "HearingTracker · AudiologyOnline", tryThis = true),
        Card("c5_6", 5, Evidence.STRONG, "OSHA · AudiologyOnline"),
        Card("c5_7", 5, Evidence.STRONG, "HearingTracker · Le Quotidien du Médecin"),
        Card("c5_9", 5, Evidence.STRONG, "NIDCD · WHO–ITU H.870"),
        Card("c5_10", 5, Evidence.STRONG, "NHS hyperacusis leaflets (2024) · Jastreboff"),
        Card("c6_1", 6, null, "NICE NG155"),
        Card("c6_2", 6, null, "NICE NG155"),
        Card("c6_3", 6, null, "NICE NG155 · AAO-HNSF 2026"),
        Card("c7_1", 7, null, "France Acouphènes"),
        Card("c7_2", 7, null, "France Acouphènes · Tinnitus UK"),
    )

    fun card(id: String): Card? = cards.firstOrNull { it.id == id }

    /** The cards of one topic, in reading order. */
    fun topic(n: Int): List<Card> = cards.filter { it.topic == n }
}
