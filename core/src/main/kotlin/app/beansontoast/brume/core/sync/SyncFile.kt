package app.beansontoast.brume.core.sync

/**
 * Which file in the chosen folder is the sync file (pastille's SyncFolder rule, 2026-10-29): an
 * exact, case-sensitive match on [NAME] among files. Several matches use the most recently
 * modified; an unknown time counts as oldest; a tie keeps the provider's order. No match: create it.
 * Never a second one: a provider that appends " (1)" is only asked to create when nothing matches.
 */
object SyncFile {
    const val NAME = "brume-sync.json"
    const val MIME = "application/json"

    data class Child(val documentId: String, val name: String?, val modified: Long?, val isDirectory: Boolean = false)

    sealed interface Choice {
        data class Use(val documentId: String, val matches: Int) : Choice
        data object Create : Choice
    }

    fun choose(children: List<Child>): Choice {
        val matches = children.filter { !it.isDirectory && it.name == NAME }
        val newest = matches.withIndex()
            .maxWithOrNull(compareBy<IndexedValue<Child>> { it.value.modified ?: Long.MIN_VALUE }.thenByDescending { it.index })
            ?.value ?: return Choice.Create
        return Choice.Use(newest.documentId, matches.size)
    }
}
