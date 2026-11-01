package app.beansontoast.brume.core.learn

/**
 * One piece of advice. Its words live in the strings as `advice_<id>_title` and `advice_<id>_body`;
 * [card] is the Learn card it comes from, a tap away for whoever wants the evidence.
 */
data class AdviceItem(val id: String, val card: String)

/**
 * Advice cards (Iris, 2026-11-01, from Erwann: « J'aime pas du tout le quiz… On est là pour
 * relaxer les gens, pas pour leur faire avoir tort ou avoir raison… Donner des conseils, c'est bien
 * en format carte »). The quiz's ten facts, said as gentle, practical advice: nothing to guess, no
 * right or wrong, no score, nothing to turn over. Ordered from the most reassuring to the practical.
 */
object Advice {
    val cards: List<AdviceItem> = listOf(
        AdviceItem("real", "c1_2"),
        AdviceItem("stress", "c3_5"),
        AdviceItem("plugs", "c5_10"),
        AdviceItem("hours", "c5_3"),
        AdviceItem("notches", "c5_1"),
        AdviceItem("wax", "c3_10"),
        AdviceItem("coffee", "c3_1"),
        AdviceItem("supplements", "c3_7"),
        AdviceItem("cbt", "c2_1"),
        AdviceItem("aids", "c2_2"),
    )
}
