package gg.opgg.android.data

/**
 * Pure helpers for acting on a local deck: renaming and duplicating.
 *
 * Kept out of the ViewModel so the awkward parts - blank names, colliding copy
 * names, and the requirement that a duplicate not share mutable state with its
 * original - are unit testable without a device.
 */
object LocalDeckOps {

    const val COPY_SUFFIX = " (copy)"

    /**
     * Cleans a name typed by the user.
     *
     * Blank, or made only of whitespace, falls back to [fallback] rather than
     * being accepted: an unnamed deck is much harder to find in a list than a
     * generically named one.
     */
    fun cleanName(raw: String?, fallback: String = "New deck"): String {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.ifEmpty { fallback }
    }

    /**
     * A copy's name that does not collide with anything already in [taken].
     *
     * `Beast` becomes `Beast (copy)`, and duplicating that again becomes
     * `Beast (copy 2)` - the number goes *inside* the brackets, so it does not
     * read as a second suffix applied to the first.
     */
    fun uniqueCopyName(base: String, taken: Collection<String>): String {
        val existing = taken.toSet()
        val stem = base.trim().ifEmpty { "New deck" }
        val first = stem + COPY_SUFFIX
        if (first !in existing) return first
        var n = 2
        while (copyName(stem, n) in existing) n++
        return copyName(stem, n)
    }

    private fun copyName(stem: String, n: Int): String = "$stem (copy $n)"

    /**
     * A copy of [deck] under [newId].
     *
     * The card map is rebuilt rather than reused. LocalDeck holds an immutable
     * Map so sharing would in fact be safe, but a copy that aliases its source
     * is the kind of thing that breaks the moment either side is edited, so the
     * new deck gets its own map.
     *
     * `createdAt` and `updatedAt` are both [now]: a copy is a new deck, and
     * leaving the original's timestamps makes the list sort wrongly.
     *
     * The site link is dropped on purpose. `savedeck` keys on `deckname`, so a
     * copy that inherited it would silently overwrite the original on
     * onepiece.gg the first time it was saved; a copy must publish as its own
     * deck instead.
     */
    fun duplicate(
        deck: LocalDeck,
        newId: String,
        now: Long,
        existingNames: Collection<String>,
    ): LocalDeck = deck.copy(
        id = newId,
        name = uniqueCopyName(deck.name, existingNames),
        cards = deck.cards.toMap(),
        createdAt = now,
        updatedAt = now,
        siteDeckname = null,
        siteSlug = null,
    )
}
