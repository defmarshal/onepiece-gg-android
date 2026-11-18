package gg.opgg.android.data

/** Which card pool a deck is being built for. */
enum class RegulationMode {
    /** Tournament legal: banned cards out, and a rolling four-Block window. */
    STANDARD,

    /** The whole back catalogue. No bans, no Block window. */
    EXTRA,
}

/** A card that cannot be played in a given regulation, with the date it started. */
data class BanEntry(
    /** Canonical id, so every printing of the card is covered. */
    val canonicalId: String,
    /** First day the ban applies, as an epoch day. */
    val fromEpochDay: Long,
    val note: String,
    val source: String,
)

/**
 * Tournament regulations: the ban list and the Block Number window.
 *
 * Two things forced this to be hand-maintained rather than read from the API:
 *
 * - **Ban status is not in the API at all.** Ten candidate endpoints were probed
 *   and every one answered HTTP 200 with `Hacker! Go home!`, which is this API's
 *   reply to an unrecognised action. No card field mentions ban or restriction,
 *   and a known banned card (`OP14-020`) looks entirely ordinary in the payload
 *   with `BlockIcon=4`. So the list below is curated by hand.
 * - **Because it is hand-maintained it will go stale silently**, which is worse
 *   than having no list. [banListAsOf] is surfaced in the editor so nobody
 *   trusts it blindly.
 *
 * The Block window is *not* hand-maintained. Bandai rolls it every April, so it
 * is computed from the current date - see [standardBlocks].
 *
 * Pure and Android-free, so the date arithmetic is unit testable.
 */
object Regulation {

    // ---- dates ------------------------------------------------------------

    /**
     * Days since 1970-01-01 in the proleptic Gregorian calendar.
     *
     * Hand-rolled rather than `java.time`: the module targets minSdk 24 with no
     * core library desugaring, where `LocalDate` is not available.
     */
    fun epochDay(year: Int, month: Int, day: Int): Long {
        val m = month.coerceIn(1, 12)
        val d = day.coerceIn(1, 31)
        // Shift so that March is month 0, which puts the leap day at the end.
        val y = year - if (m <= 2) 1 else 0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097L + doe.toLong() - 719468L
    }

    /** Inverse of [epochDay], as `year to (month to day)`. */
    fun civil(epochDay: Long): Triple<Int, Int, Int> {
        var z = epochDay + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    fun todayEpochDay(): Long = System.currentTimeMillis() / 86_400_000L

    // ---- the Block Number window ------------------------------------------

    /**
     * The Blocks legal in Standard Regulation on [today].
     *
     * Bandai's published schedule: boosters 1-4 are Block 1, 5-8 are Block 2,
     * and the window is four Blocks wide, advancing every 1 April. So for the
     * Apr 2026 - Mar 2027 season the legal Blocks are 2-5, for Apr 2027 -
     * Mar 2028 they are 3-6, and so on.
     *
     * Computed rather than hardcoded so the app never needs a code change in
     * April. April 2026 is the first season, hence the `- 2024`.
     */
    fun standardBlocks(today: Long): IntRange {
        val (year, month, _) = civil(today)
        // January to March belongs to the window that opened the previous April.
        val seasonStartYear = if (month >= 4) year else year - 1
        val first = FIRST_SEASON_FIRST_BLOCK + (seasonStartYear - FIRST_SEASON_YEAR)
        return first..(first + 3)
    }

    /** Bandai's first Block Number season ran Apr 2026 - Mar 2027. */
    private const val FIRST_SEASON_YEAR = 2026

    /** ...and its window was Blocks 2-5. */
    private const val FIRST_SEASON_FIRST_BLOCK = 2

    // ---- the ban list -----------------------------------------------------

    /**
     * Cards that cannot be included in a Standard deck.
     *
     * Curated by hand: the API exposes no ban status (see the class comment).
     * Keep [BAN_LIST_AS_OF] in step with any edit here.
     */
    val banList: List<BanEntry> = listOf(
        BanEntry(
            canonicalId = "OP14-020",
            fromEpochDay = epochDay(2026, 10, 12),
            note = "Banned from 12 Oct 2026, parallels included. Bandai cited the " +
                "diversity of Leader selection, and its effect on {Slash} Leaders.",
            source = "en.onepiece-cardgame.com/news/restriction-261001.html",
        ),
    )

    /**
     * When the ban list above was last checked against the official notice.
     *
     * Shown in the editor on purpose: a hand-maintained list that silently goes
     * stale is worse than no list, so the age is always on screen.
     */
    val banListAsOf = epochDay(2026, 9, 28)

    // ---- queries ----------------------------------------------------------

    /**
     * The ban covering [printingOrCanonicalId] on [today], or null.
     *
     * Deliberately Standard-only. The notice is framed around tournament play and
     * Bandai's stated aim is that fans "enjoy numerous decks built around the
     * leader card", so a banned card stays playable in Extra rather than
     * vanishing from the game entirely.
     *
     * Accepts a **printing** id as well as a canonical one. The ban is stored
     * canonically so it covers every reprint - the notice calls the parallels out
     * explicitly - and the suffix is stripped here rather than left to each
     * caller, because getting that wrong silently exempts a banned parallel.
     */
    fun banFor(printingOrCanonicalId: String, today: Long, mode: RegulationMode): BanEntry? {
        if (mode != RegulationMode.STANDARD) return null
        val id = stripPrintingSuffix(printingOrCanonicalId)
        return banList.firstOrNull { it.canonicalId == id && today >= it.fromEpochDay }
    }

    fun isBanned(printingOrCanonicalId: String, today: Long, mode: RegulationMode): Boolean =
        banFor(printingOrCanonicalId, today, mode) != null

    private val PRINTING_SUFFIX = Regex("_[pP][0-9]+$|_[rR][0-9]+$")

    private fun stripPrintingSuffix(id: String): String = PRINTING_SUFFIX.replace(id, "")

    /**
     * Whether a card's `BlockIcon` is legal on [today].
     *
     * An **empty** `BlockIcon` counts as legal. Verified rather than assumed: of
     * the copies in 52 tournament decks, 1.8% were cards with no BlockIcon at
     * all - `OP01-016_p6`, `P-028_p2`, `OP05-005_p1` and similar parallels - so
     * an empty value cannot mean "banned". It is most likely an unset field.
     */
    fun isBlockAllowed(blockIcon: String?, today: Long, mode: RegulationMode): Boolean {
        if (mode != RegulationMode.STANDARD) return true
        val raw = blockIcon?.trim().orEmpty()
        if (raw.isEmpty()) return true
        // "X" marks Super Parallel Rares and same-number cards, which Bandai
        // states remain legal regardless of reprint.
        if (raw.equals("X", ignoreCase = true)) return true
        val n = raw.toIntOrNull() ?: return true
        return n in standardBlocks(today)
    }

    /** One line describing what the app is enforcing, for the editor. */
    fun describe(today: Long, mode: RegulationMode): String {
        val blocks = standardBlocks(today)
        val (y, m, d) = civil(banListAsOf)
        val asOf = "%04d-%02d-%02d".format(y, m, d)
        return when (mode) {
            RegulationMode.STANDARD ->
                "Standard: Blocks ${blocks.first}-${blocks.last}, ban list as of $asOf"
            RegulationMode.EXTRA ->
                "Extra: all Blocks, ban list not applied (as of $asOf)"
        }
    }
}
