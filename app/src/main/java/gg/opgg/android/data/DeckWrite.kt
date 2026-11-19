package gg.opgg.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom

/**
 * Body of `POST /cgfw/savedeck?game=onepiece`, captured from the site's own
 * builder (`onepiece.gg/builder/<deckName>`) on 2026-11-19.
 *
 * Two things about this endpoint are not guessable from the JS bundles and cost
 * a long detour before they were captured:
 *
 * - **`deckname` is the identity.** It is a machine name (10 lowercase
 *   alphanumerics, e.g. `qxqgktmi49`), not the deck's title. Sending the same
 *   `deckname` again *updates* that deck; any other value creates a new one.
 *   `id` and `slug` are ignored as selectors, which is why every earlier probe
 *   produced a new deck.
 * - **`humanname` is the display title**, and the public `slug` is derived from
 *   it - so renaming changes the slug. `deckname` stays stable, which is why the
 *   app stores it rather than the slug.
 *
 * Every field is always sent by the site, and `savedeck` replaces the deck's
 * contents wholesale rather than merging. That is why no property here has a
 * default: kotlinx.serialization omits defaults, and a missing `public` or
 * `unrestricted` is not the same request the site makes.
 */
@Serializable
data class SaveDeckRequest(
    /** Printing id -> copies, Leader included. Counts are replaced, not merged. */
    val savedeck: Map<String, Int>,
    val savedeck_extra: Map<String, String>,
    /** Machine name; reuse it to update an existing site deck. */
    val deckname: String,
    val description: String,
    val format: String,
    /** The title the site displays. */
    val humanname: String,
    val manually_private: String,
    /** A string on the wire (`"0"`/`"1"`), not a boolean. */
    @SerialName("public") val publicDeck: String,
    val title_image: String,
    val unrestricted: String,
    val youtube: String,
) {
    companion object {
        /** A new deck's body, with the same values the site sends for a private deck. */
        fun of(deckname: String, humanname: String, cards: Map<String, Int>): SaveDeckRequest =
            SaveDeckRequest(
                savedeck = cards,
                savedeck_extra = emptyMap(),
                deckname = deckname,
                description = "",
                format = "Standard",
                humanname = humanname,
                manually_private = "",
                publicDeck = "0",
                title_image = "",
                unrestricted = "0",
                youtube = "",
            )
    }
}

@Serializable
data class SaveDeckResponse(
    val id: String = "",
    val deckname: String = "",
    val slug: String = "",
    val error: Boolean = false,
    val error_text: String? = null,
)

/**
 * Machine names the site generates for a new deck. Ten characters, lowercase
 * letters and digits - the same shape the site's own builder uses.
 */
object SiteDeckName {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun generate(): String = buildString(10) {
        repeat(10) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
