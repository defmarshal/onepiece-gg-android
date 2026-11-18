package gg.opgg.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

const val API_BASE = "https://api.dotgg.gg"
const val GAME = "onepiece"
const val SITE = "onepiece"
const val CARD_IMG_BASE = "https://static.dotgg.gg/onepiece/card"

/** Runs of whitespace, for tidying the pack names. */
private val WHITESPACE = Regex("\\s+")

/** The trailing "(...)" of a card name, which is where the site's own labels live. */
private val TRAILING_TAG = Regex("\\(([^()]*)\\)\\s*$")

/**
 * The name tags that mean a kind of printing rather than part of the name.
 *
 * Deliberately short: most trailing brackets in a card name are part of it -
 * "Chadros.Higelyges (Brownbeard)", "DON!! Card (Ace)", "DON!! Card (3D Text)".
 */
private val PRINTING_KIND_TAGS = mapOf(
    "parallel" to "Parallel",
    "alternate art" to "Alternate Art",
    "gold" to "Gold",
)

private const val PARALLEL_SUFFIX = "_p"
private const val REPRINT_SUFFIX = "_r"

const val ICON_BASE = "https://static.dotgg.gg/onepiece/icon"

@Serializable
data class Card(
    val id: String? = null,
    @SerialName("id_normal") val idNormal: String? = null,
    val rarity: String? = null,
    val cardType: String? = null,
    val name: String? = null,
    val Cost: String? = null,
    val Attribute: String? = null,
    val Power: String? = null,
    val Counter: String? = null,
    val Color: String? = null,
    val Type: String? = null,
    val Effect: String? = null,
    val CardSets: String? = null,
    val Life: String? = null,
    val Trigger: String? = null,
    val slug: String? = null,
    val price: String? = null,
    val marketIds: String? = null,
    val foilPrice: String? = null,
    val deltaPrice: String? = null,
    val deltaFoilPrice: String? = null,
    val delta7dPrice: String? = null,
    val delta7dPriceFoil: String? = null,
    val set: String? = null,
    val language: String? = null,
    val cmurl: String? = null,
    val cmid: String? = null,
    val cmPrice: String? = null,
    val cmFoilPrice: String? = null,
    val cmDeltaPrice: String? = null,
    val cmDeltaFoilPrice: String? = null,
    val cmDelta7dPrice: String? = null,
    val cmDelta7dPriceFoil: String? = null,
    val BlockIcon: String? = null,
    /**
     * The release date of this row's set, in Unix seconds.
     *
     * Stamped by `applyCards` from the `getsets` list after decode; the card
     * rows themselves carry no date. @Transient keeps it out of the on-disk
     * cache (the sets list is cached alongside), so an old cache cannot hold a
     * stale or missing value — it is re-derived on every load.
     *
     * "Default order" sorts on this, newest set first: the API's row order was
     * arbitrary (OP16-077 first, no pattern), so the unsorted grid looked
     * shuffled and "Default order" meant nothing.
     */
    @Transient
    val releaseDate: Long? = null,
) {
        val key: String get() = idNormal ?: id.orEmpty()

        /**
         * The artwork for *this* row.
         *
         * Uses the row's own `id`, not `idNormal`. The card database carries a
         * row per printing, and printings of the same card number have
         * genuinely different artwork - P-058 "Where the Wind Blows" is a dark
         * card in one printing and a completely different illustration in
         * `_p1`. The CDN serves one file per printing id
         * (`.../card/P-058_p1.webp`), so keying on `idNormal` threw the artwork
         * away and every printing collapsed onto the base card's image.
         *
         * That is not a cosmetic difference. 1,172 of the 3,063 cards - 38% of
         * the database - have more than one printing, and 2,477 of the 5,540
         * rows were showing the wrong picture.
         *
         * `key` deliberately still resolves to `idNormal`: that is what groups
         * printings under their card, and is used for ownership and the rules
         * engine. Only the image is per-printing.
         */
        val imageUrl: String
            get() = "$CARD_IMG_BASE/${id?.takeIf { it.isNotBlank() } ?: idNormal.orEmpty()}.webp"

        /**
         * The pack this printing's artwork comes from.
         *
         * `CardSets` holds the *pack* name - "ST-11 Uta Deck Battle Participation
         * Pack", "-Green Uta- [ST-16]" - and it is the only field that says which
         * product a particular artwork shipped in.
         *
         * It is not the same as [set]. The `set` code is the card's *series*
         * ("P", "OP03", "ST05") and is identical for every printing of a card,
         * so it cannot say which pack a printing came from. Across the 1,172
         * cards with more than one printing, `set` differs on **zero** and
         * `CardSets` differs on **783**.
         *
         * The raw value carries HTML entities (`&#039;`, `&amp;`) and stray
         * whitespace, so it is cleaned before display.
         */
        val packName: String?
            get() = CardSets
                ?.let { CardText.decodeEntities(it) }
                ?.replace(WHITESPACE, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        /**
         * What kind of printing this is: "Parallel", "Reprint" or "Standard".
         *
         * The API has **no flag for this**. There is no art column among the 34
         * in `getcards` and no art-variant endpoint, so it has to be inferred
         * from two signals that happen to agree:
         *
         * - the id suffix. `_pN` is the parallel family and `_rN` the reprints -
         *   2,012 and 465 rows respectively, and every one of them is a
         *   genuinely different picture (verified by hashing the artwork);
         * - the site's own name label. It appends "(Parallel)" to 91 rows, always
         *   in the `_p` family and never to a base row, so when it is present it
         *   is the more precise answer.
         *
         * The name is checked first, but only for a small set of known tags: most
         * trailing brackets in a card name are part of the name, not a kind -
         * "Chadros.Higelyges (Brownbeard)", "DON!! Card (3D Text)".
         */
        val printingKind: String
            get() {
                val tagged = name?.trim()?.let { n ->
                    TRAILING_TAG.find(n)?.groupValues?.get(1)?.trim()?.lowercase()
                }
                if (tagged != null) {
                    PRINTING_KIND_TAGS[tagged]?.let { return it }
                }
                val cardId = id ?: return "Standard"
                val normal = idNormal
                if (normal != null && cardId != normal) {
                    if (cardId.startsWith("$normal" + PARALLEL_SUFFIX)) return "Parallel"
                    if (cardId.startsWith("$normal" + REPRINT_SUFFIX)) return "Reprint"
                }
                return "Standard"
            }

        /**
         * Whether this printing exists only as foil.
         *
         * Parallel printings (`_pN`, and rows the site labels "(Parallel)") are
         * printed as foil only, so offering a "STD 0" counter for them asks for
         * copies that cannot exist. Base and reprint printings keep both
         * counters: foil copies of a normal printing do exist.
         */
        val foilOnly: Boolean
            get() = printingKind == "Parallel"
    fun attrIcon(): String? = Attribute?.let { "$ICON_BASE/$it.webp" }

    /**
     * All free-text searchable fields, lowercased ONCE at construction.
     *
     * The site matches textSearch against name, id_normal, id, CardSets,
     * cardtype, setId, color and Effect. Lowercasing those per card per
     * keystroke meant ~24,500 string allocations on every recomposition, so
     * it is precomputed here instead.
     *
     * @Transient matters: a body-declared val has a backing field, and kotlinx
     * serialises every property that has one. Without this the blob was written
     * into the on-disk cache too, which doubled its size to 6.2 MB and doubled
     * the cost of reading it back.
     */
    @Transient
    val searchBlob: String = buildString {
        append(name).append('\n')
        append(idNormal).append('\n')
        append(id).append('\n')
        append(CardSets).append('\n')
        append(cardType).append('\n')
        append(set).append('\n')
        append(Color).append('\n')
        append(Effect)
    }.lowercase()
}

@Serializable
data class CardSet(
    val id: String? = null,
    val code: String? = null,
    val name: String? = null,
    val cardsCount: String? = null,
    val ReleaseDate: String? = null,
)

@Serializable
data class ApiError(
    val error: Boolean = false,
    val error_text: String? = null,
)

/**
 * `getcards?mode=indexed` columnar format: the 34 field names appear once and
 * each row is a positional array. 3,148,059 bytes versus 5,230,704 for the
 * default array-of-objects, purely because it does not repeat 34 keys across
 * 5,540 rows. This is the format the website itself requests.
 */
@Serializable
data class IndexedCards(
    val names: List<String> = emptyList(),
    val data: List<List<JsonElement?>> = emptyList(),
)

@Serializable
data class CollectionCardEntry(
    val card: String,
    val type: String,
    val count: Int,
    /**
     * Wishlist count, sent by the website's own collection page in the same
     * batch rows: {card, type, count, extra, trade, wish, source}. Wishes live
     * only in the main collection - savebindercard/savebatchbindercard rows
     * have no wish field at all, which is why the app routes wishlist writes
     * to savebatchcollection even when a binder is being viewed.
     */
    val wish: Int? = null,
)

@Serializable
data class SaveBatchCollectionRequest(
    val cards: List<CollectionCardEntry>,
    val source: String? = null,
)

/**
 * Note `error:false` can still come back with a per-card failure in `errors`,
 * e.g. an unknown card id. Both have to be checked.
 */
@Serializable
data class SaveBatchCollectionResult(
    val error: Boolean = false,
    val error_text: String? = null,
    val message: String? = null,
    val processed: Int? = null,
    val total: Int? = null,
    val errors: List<String>? = null,
)

@Serializable
data class SaveBinderCardRequest(
    val card: String,
    val type: String,
    val count: Int,
    val binder_id: Int,
)

@Serializable
data class BinderCard(
    val card: String? = null,
    val type: String? = null,
    val count: Int? = null,
    val binder_id: Int? = null,
)

@Serializable
data class BinderCardsResponse(
    val collection: List<BinderCard>? = null,
)

/** Real shape of a collection row: counts arrive as strings. */
@Serializable
data class OwnedCard(
    val card: String? = null,
    val standard: String? = null,
    val foil: String? = null,
    val wish: String? = null,
    val wish_foil: String? = null,
    val trade: String? = null,
    val trade_foil: String? = null,
)

@Serializable
data class ApiUser(
    val user_id: Long? = null,
    val nickname: String? = null,
    // Deliberately NOT declared: the server sends "capabilities" as an object
    // ({"subscriber":true}), and typing it as a list made deserialization throw.
    // ignoreUnknownKeys drops it instead.
)

@Serializable
data class UserDataResponse(
    val user: ApiUser? = null,
    val collection: List<OwnedCard>? = null,
    /** Kept so we can tell "empty binder" apart from "auth rejected". */
    val error: Boolean = false,
    val error_text: String? = null,
)

@Serializable
data class ApiBinder(
    val id: Long = 0,
    val name: String? = null,
    val type: String? = null,
    val sort: Int? = null,
    val count: Int? = null,
    val color: String? = null,
    val slug: String? = null,
    val public: Int? = null,
    val parent_id: Long? = null,
    val created: Long? = null,
)

/**
 * `getbindercards` returns rows in the same shape as `getuserdata`'s
 * collection rows (string counts plus wish/trade), not the thin
 * `{card,type,count}` rows [BinderCard] describes. Parsing it as OwnedCard is
 * what lets binder and collection views share all the collapsing logic.
 */
@Serializable
data class BinderContentsResponse(
    val collection: List<OwnedCard>? = null,
    val error: Boolean = false,
    val error_text: String? = null,
)

@Serializable
data class CreateBinderRequest(
    val name: String,
    val color: String = "",
    val parent_id: Long = 0,
)

@Serializable
data class CreateBinderResponse(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
    val sort: Int? = null,
    val error: Boolean = false,
    val error_text: String? = null,
)

@Serializable
data class RenameBinderRequest(val binder_id: Long, val name: String)

@Serializable
data class BinderIdRequest(val binder_id: Long)

@Serializable
data class BinderColorRequest(val binder_id: Long, val color: String)

@Serializable
data class BinderPublicRequest(val binder_id: Long, val public: Int)

@Serializable
data class BinderParentRequest(val binder_id: Long, val parent_id: Long)

@Serializable
data class ReorderBindersRequest(val order: List<Long>)

@Serializable
data class MoveCardsRequest(
    val from_binder: Long,
    val to_binder: Long,
    val cards: List<String>,
)

@Serializable
data class MoveCardsResponse(
    val error: Boolean = false,
    val error_text: String? = null,
    val message: String? = null,
    val moved: Int? = null,
    val copies: Int? = null,
    val total: Int? = null,
)

@Serializable
data class BinderActionResponse(
    val error: Boolean = false,
    val error_text: String? = null,
    val message: String? = null,
    val id: Long? = null,
)

@Serializable
data class BinderListResponse(
    val binders: List<ApiBinder>? = null,
    /**
     * The site answers a rejected session with HTTP 200 and this body, so
     * without these fields the error was decoded into an *empty binder list* and
     * the app went on to tell the user they had no binder.
     */
    val error: Boolean = false,
    val error_text: String? = null,
)

@Serializable
data class Deck(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
    val humanname: String? = null,
    val author: String? = null,
    val authornick: String? = null,
    val date: String? = null,
    val date_edited: String? = null,
    val views: String? = null,
    val fingerprint: String? = null,
    val title_image: String? = null,
    val leader: String? = null,
    val format: String? = null,
    val top_set: String? = null,
    val archetype_name: String? = null,
    val archetype_format: String? = null,
    // Loosely typed for the same reason as in DeckDetail: the server varies the
    // JSON type between endpoints, and a wrong guess kills the whole screen.
    val archetype_placings: JsonElement? = null,
    val is_tournament: String? = null,
    val history_records: String? = null,
    val physical: String? = null,
    val color_purple: String? = null,
    val color_red: String? = null,
    val color_green: String? = null,
    val color_blue: String? = null,
    val color_black: String? = null,
    val color_yellow: String? = null,
    /** printing id -> count. Counts arrive as strings. */
    val deck: Map<String, String> = emptyMap(),
    val price: Double? = null,
    val priceeur: Double? = null,
) {
    val displayName: String get() = humanname?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    val cardCount: Int get() = deck.values.sumOf { it.toIntOrNull() ?: 0 }
    val uniqueCards: Int get() = deck.size
    val leaderNormal: String? get() = leader?.takeIf { it.isNotBlank() }
    val leaderImage: String? get() = leaderNormal?.let { "$CARD_IMG_BASE/$it.webp" }

    /**
     * Leader art, falling back to the deck's first card. Plenty of published
     * decks have no leader recorded, and a blank grey box reads as a bug.
     */
    val coverImage: String?
        get() = leaderImage ?: deck.keys.firstOrNull()?.let { "$CARD_IMG_BASE/$it.webp" }
}

@Serializable
data class DeckDetail(
    val id: Long? = null,
    val slug: String? = null,
    val humanname: String? = null,
    val author: String? = null,
    val authornick: String? = null,
    val description: String? = null,
    val public: String? = null,
    val views: String? = null,
    val manual_title: String? = null,
    val youtube: String? = null,
    val leader: String? = null,
    val format: String? = null,
    val top_set: String? = null,
    val archetype_name: String? = null,
    val archetype_format: String? = null,
    // Typed loosely: the server sends these as null, a number, or an object
    // depending on the deck, and a wrong guess here breaks the whole detail page.
    val archetype_placings: JsonElement? = null,
    val archetype_wins: JsonElement? = null,
    val history: JsonElement? = null,
    val deck: Map<String, String> = emptyMap(),
    val price: Double? = null,
) {
    val displayName: String get() = humanname?.takeIf { it.isNotBlank() } ?: slug.orEmpty()
    val cardCount: Int get() = deck.values.sumOf { it.toIntOrNull() ?: 0 }
    val leaderImage: String? get() = leader?.takeIf { it.isNotBlank() }?.let { "$CARD_IMG_BASE/$it.webp" }
}
