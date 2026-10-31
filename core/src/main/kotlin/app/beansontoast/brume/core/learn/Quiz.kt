package app.beansontoast.brume.core.learn

/** What a quiz statement turns out to be. Shown as a glyph and a word, never a hue. */
enum class Verdict { TRUE, MYTH }

/**
 * One « True or myth? » card. Its words live in the strings as `quiz_<id>_q` and `quiz_<id>_a`;
 * [card] is the Learn card it is drawn from, whose evidence mark it wears and which it links to.
 */
data class QuizItem(val id: String, val card: String, val verdict: Verdict)

/**
 * Learn as play (Iris, 2026-10-31, from Erwann's « comme un quiz, mais différentes cartes »): a
 * deck of five statements, each faithful to its card. Never graded: no streak, no score kept.
 */
object Quiz {
    const val DECK = 5

    val pool: List<QuizItem> = listOf(
        QuizItem("coffee", "c3_1", Verdict.MYTH),
        QuizItem("time", "c5_3", Verdict.TRUE),
        QuizItem("in_head", "c1_2", Verdict.MYTH),
        QuizItem("ten_db", "c5_1", Verdict.TRUE),
        QuizItem("plugs_always", "c5_10", Verdict.MYTH),
        QuizItem("supplements", "c3_7", Verdict.MYTH),
        QuizItem("stress", "c3_5", Verdict.TRUE),
        QuizItem("cotton_buds", "c3_10", Verdict.MYTH),
        QuizItem("cbt", "c2_1", Verdict.TRUE),
        QuizItem("aids_no_loss", "c2_2", Verdict.MYTH),
    )

    /** Five in a row from the pool, starting [round] decks in, wrapping round: a fresh mix each time. */
    fun deck(round: Int = 0): List<QuizItem> {
        val start = Math.floorMod(round * DECK, pool.size)
        return List(DECK) { pool[(start + it) % pool.size] }
    }
}
