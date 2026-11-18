package gg.opgg.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The metagame snapshot from `getmetasnapshot`.
 *
 * The app already fetched and modelled this to build the related-cards index,
 * but kept only eight fields and then discarded the rest. This is the full
 * shape the payload actually carries, for the meta screen.
 *
 * Two things about the data that shape the UI, both measured from a live
 * snapshot on 2026-09-28 (51 archetypes, 737 decks, 33 events, 1,448 matches,
 * 157 KB, and it works signed out):
 *
 * - **`tier` is populated on only 9 of 51 archetypes.** The rest are null, so
 *   tiers cannot be the backbone of the screen; ranking comes from `rank`,
 *   `share` and `winrate`, with tier as a garnish where it exists. `tier_reason`
 *   is populated on all 51 and is the text worth reading.
 * - **The snapshot only covers the staples** - about 446 of 3,063 cards appear
 *   in it - so it describes the competitive slice rather than the game, and the
 *   archetype list rotates (51 one day, 53 the next).
 */
@Serializable
data class MetaCardEntry(
    val id: String? = null,
    val name: String? = null,
    /** Average copies of this card per deck in the archetype. */
    @SerialName("in_deck") val inDeck: String? = null,
    /** Inclusion rate, 0..1. */
    val weight: Double? = null,
) {
    /** [inDeck] arrives as a string; the copy count is the interesting part. */
    val copies: Double? get() = inDeck?.toDoubleOrNull()
}

/**
 * One recorded finish, from the `form` array. **This is not prose** - the name
 * suggests a deck write-up but it is a list of tournament results, which is
 * easy to get wrong from the key alone.
 */
@Serializable
data class MetaFormEntry(
    val event: String? = null,
    @SerialName("event_slug") val eventSlug: String? = null,
    val place: Int? = null,
    val players: Int? = null,
    /** Epoch, unlike [MetaEvidence.date] which is a string. */
    val date: Long? = null,
    val entries: Int? = null,
    /** Cut line, when one applied. */
    val cut: String? = null,
)

/**
 * A cited source behind a tier or a record.
 *
 * [date] is a **date string** like `2026-09-14`, not an epoch. The other date
 * fields in this payload are epochs, so mixing them up throws at decode time -
 * which is what the live test caught.
 */
@Serializable
data class MetaEvidence(
    val title: String? = null,
    val url: String? = null,
    val date: String? = null,
)

/** A recorded finish. */
@Serializable
data class MetaFinish(
    val place: Int? = null,
    val event: String? = null,
    val players: Int? = null,
    val date: Long? = null,
)

@Serializable
data class MetaArchetype(
    val id: Long? = null,
    val name: String? = null,
    /** Leader card id, printing form. */
    val leader: String? = null,
    @SerialName("set_code") val setCode: String? = null,
    val slug: String? = null,

    /** "S" / "A" / "B" / "C" - present on only a minority of archetypes. */
    val tier: String? = null,
    @SerialName("tier_score") val tierScore: Double? = null,
    /** Why that tier, written against the field. Populated on all of them. */
    @SerialName("tier_reason") val tierReason: String? = null,
    /** "conversion" or whatever basis the tier was judged on. */
    @SerialName("tier_basis") val tierBasis: String? = null,

    /** Percentage of placed decks, 0..100. */
    val share: Double? = null,
    @SerialName("share_delta") val shareDelta: Double? = null,
    val decks: Int? = null,
    val events: Int? = null,
    val rank: Int? = null,

    /** 0..1. */
    val winrate: Double? = null,
    @SerialName("winrate_n") val winrateN: Int? = null,
    @SerialName("winrate_method") val winrateMethod: String? = null,
    @SerialName("top8_rate") val top8Rate: Double? = null,
    @SerialName("topq_rate") val topqRate: Double? = null,
    @SerialName("cut_rate") val cutRate: Double? = null,
    @SerialName("cut_n") val cutN: Int? = null,

    /** "up" / "down" / "flat" - only set where it changed. */
    val momentum: String? = null,
    val watch: Boolean? = null,
    val colors: List<String> = emptyList(),

    /** Recent tournament finishes, newest first. Placements, not prose. */
    val form: List<MetaFormEntry> = emptyList(),
    /** Free-text notes from the snapshot's own research. Present on 24 of 51. */
    @SerialName("research_note") val researchNote: String? = null,
    val evidence: List<MetaEvidence> = emptyList(),
    val titles: List<MetaEvidence> = emptyList(),
    val partner: String? = null,

    val cards: List<MetaCardEntry> = emptyList(),
    @SerialName("best_decks") val bestDecks: List<MetaFinish> = emptyList(),

    /** Present, but no notes array ships with it - matchups need another call. */
    @SerialName("matchup_notes_total") val matchupNotesTotal: Int? = null,
) {
    /**
     * Stable identity for this archetype, for use as a UI key.
     *
     * The snapshot's ids are printing ids, so this is the identity the rest of
     * the app normalises through; falling back to slug then name keeps it usable
     * even on a partial payload.
     */
    val key: String get() = id?.toString() ?: slug.orEmpty().ifEmpty { name.orEmpty() }

    /** True when there is enough here to be worth expanding. */
    val hasDetail: Boolean
        get() = form.isNotEmpty() || !researchNote.isNullOrBlank() ||
            evidence.isNotEmpty() || cards.isNotEmpty()
}

/** How many decks, events and matches the snapshot was measured from. */
@Serializable
data class MetaSample(
    val decks: Int? = null,
    val events: Int? = null,
    val matches: Int? = null,
    val from: Long? = null,
    val to: Long? = null,
)

/** Which optional blocks the snapshot actually carries. */
@Serializable
data class MetaAvailable(
    val winrate: Boolean? = null,
    val matchups: Boolean? = null,
    @SerialName("matchup_notes") val matchupNotes: Boolean? = null,
    /** False at time of writing, so no tier-over-time chart is possible. */
    @SerialName("tier_history") val tierHistory: Boolean? = null,
)

@Serializable
data class MetaSnapshot(
    val game: String? = null,
    val format: String? = null,
    val window: String? = null,
    @SerialName("set_code") val setCode: String? = null,
    @SerialName("generated_at") val generatedAt: Long? = null,
    /** "measured" or whatever lane this came from. */
    val lane: String? = null,
    val premium: Boolean? = null,
    val sample: MetaSample = MetaSample(),

    /**
     * Field-wide benchmarks, so a rate can be read in context rather than in the
     * abstract. On the 2026-09-28 snapshot these were 0.2521 and 0.4329 - which is
     * why an archetype at 20% share can be "S" without the field being strong.
     *
     * Flat on the snapshot rather than nested under a `field` object, because
     * that is how the payload sends them. A nested class silently parses to null.
     */
    @SerialName("field_top8_rate") val fieldTop8Rate: Double? = null,
    @SerialName("field_topq_rate") val fieldTopqRate: Double? = null,
    @SerialName("field_cut_rate") val fieldCutRate: Double? = null,

    val available: MetaAvailable = MetaAvailable(),
    val archetypes: List<MetaArchetype> = emptyList(),
) {
    /**
     * Archetypes in the order the screen shows them: by rank, falling back to
     * share and then name so the list is never randomly ordered.
     */
    fun ranked(): List<MetaArchetype> =
        archetypes.sortedWith(
            compareBy(
                { it.rank ?: Int.MAX_VALUE },
                { -(it.share ?: 0.0) },
                { it.name.orEmpty() },
            ),
        )
}
