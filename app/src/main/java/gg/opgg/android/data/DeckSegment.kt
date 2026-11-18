package gg.opgg.android.data

/**
 * Which view of the Decks tab is showing.
 *
 * Modelled as ONE enum rather than two booleans on purpose. With
 * `builderOpen` and `metaOpen` as independent flags, the state
 * "both true" is representable but meaningless, and combining them with a tap
 * handler that toggled both produced a real bug: tapping "Public" while on Meta
 * flipped `builderOpen` to true and showed the Builder instead. An enum makes
 * that state unrepresentable.
 */
enum class DeckSegment {
    /** Decks from onepiece.gg, everyone. */
    PUBLIC,

    /** The signed-in account's own decks. */
    MINE,

    /** The on-device deck builder. */
    BUILDER,

    /** The metagame. */
    META,
    ;

    val isDecks: Boolean get() = this == PUBLIC || this == MINE
    val usesMyDecks: Boolean get() = this == MINE
}

/**
 * Tapping a segment chip.
 *
 * Tapping a chip **selects** that segment; it never toggles the current one off.
 * A segmented control that deselects itself on a second tap makes it impossible
 * to tap "the one I am already on" without thinking about it.
 *
 * `decksMine` is only changed by the two deck segments, and is left alone
 * otherwise, so switching to the Builder and back does not silently re-point
 * the public list at your own decks.
 */
fun DeckSegment.tap(target: DeckSegment, decksMine: Boolean): DeckSegmentState =
    when (target) {
        DeckSegment.PUBLIC -> DeckSegmentState(DeckSegment.PUBLIC, decksMine = false)
        DeckSegment.MINE -> DeckSegmentState(DeckSegment.MINE, decksMine = true)
        DeckSegment.BUILDER -> DeckSegmentState(DeckSegment.BUILDER, decksMine)
        DeckSegment.META -> DeckSegmentState(DeckSegment.META, decksMine)
    }

data class DeckSegmentState(
    val segment: DeckSegment,
    val decksMine: Boolean,
) {
    val builderOpen: Boolean get() = segment == DeckSegment.BUILDER
    val metaOpen: Boolean get() = segment == DeckSegment.META
}
