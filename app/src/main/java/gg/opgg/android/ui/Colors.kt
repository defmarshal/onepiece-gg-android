package gg.opgg.android.ui

import androidx.compose.ui.graphics.Color

/**
 * The app's colours, in one place.
 *
 * This object exists because the palette was already there and nobody had named
 * it: 25 unique hex values were hardcoded 185 times across the ui package, with
 * five of them carrying 129 uses. Naming them is a pure rename - the values are
 * unchanged - but a colour can now be changed once instead of in 46 places.
 *
 * The values here are the ones that were in use. Nothing was re-tinted.
 *
 * **When adding a colour, add it here rather than inlining a literal.** That is
 * the whole point of the file; see COSMETICS.md section 2.
 */
object AppColors {

    // ---- structure -------------------------------------------------------

    /** App background, the colour behind everything. */
    val Background = Color(0xFF13171B)

    /** App bars, card tiles, the standard raised surface. */
    val Surface = Color(0xFF1B2026)

    /**
     * A second raised level: panels, thumbnail cells, stat chips.
     *
     * Distinct from [Surface] and used deliberately in ~6 places. It is only
     * slightly lighter, so the two are easy to confuse - see COSMETICS.md, this
     * is a candidate to collapse.
     */
    val SurfaceRaised = Color(0xFF20252B)

    /** Selected cell within a raised surface: chosen Leader, active thumbnail. */
    val SurfaceSelected = Color(0xFF2A2130)

    /**
     * A 1dp outline for a framed surface, such as a card tile.
     *
     * Very faint on purpose - about 10% white, so it reads as an edge rather
     * than a line. The palette had no general border colour before this, which
     * is part of why the grid read as images floating on the background. Use
     * this for borders and [DividerChart] for gridlines; do not swap them.
     */
    val Hairline = Color(0x1AFFFFFF)

    /** Chart gridlines and in-panel separators. Lighter than [SurfaceRaised]. */
    val DividerChart = Color(0xFF2A323B)

    /** Small badge laid over card art, so it reads against any illustration. */
    val BadgeScrim = Color(0xCC13171B)

    /** The "+" / "-" button on a card tile. */
    val TileButtonBg = Color(0xE613171B)

    /** The same button when it is disabled. */
    val TileButtonBgOff = Color(0x6613171B)

    /** Background of the amber "collection is read-only" notice. */
    val NoticeBg = Color(0xFF3A2418)

    /** A track behind a share bar. */
    val Track = Color(0x22FFFFFF)

    // ---- accent ----------------------------------------------------------

    /** Primary accent. Loud pink on near-black; whether it stays is an open
     *  question - see COSMETICS.md section 5. */
    val Accent = Color(0xFFEE6DCA)

    /** Text or icon drawn on top of [Accent]. */
    val AccentOn = Color(0xFF23131E)

    /** Secondary accent, used for the power-distribution series. */
    val AccentAlt = Color(0xFF5865F2)

    // ---- text ------------------------------------------------------------

    /** Primary body text. */
    val OnSurface = Color(0xFFE6E9EE)

    /**
     * Secondary text: stats, counts, captions. By far the most-used colour in the
     * app at 46 call sites.
     */
    val TextDim = Color(0xFF8B95A1)

    /**
     * A third text tone, used only on the metagame detail rows.
     *
     * Sits between [OnSurface] and [TextDim], so the app currently has three
     * greys. A candidate to collapse - see COSMETICS.md section 3.
     */
    val TextSecondary = Color(0xFFCFD6DE)

    /** Inactive app bar / toolbar icon, such as the back arrow. */
    val IconInactive = Color(0xFFB0BAC5)

    /** Icon on a disabled button. */
    val IconDisabled = Color(0xFF5A6169)

    // ---- meaning ---------------------------------------------------------

    /** Legal, complete, improving: deck validity, "you own everything", up. */
    val Positive = Color(0xFF8BC34A)

    /** A mark or icon sitting on [Positive]. */
    val PositiveOn = Color(0xFF0B1A0B)

    /** Needs attention: rule violations, copy caps, an illegal colour. */
    val Warning = Color(0xFFFFC107)

    /** A badge or chip tinted with [Warning] at low opacity. */
    val WarningSoft = Color(0x33FFC107)

    /** Destructive, or a decline: delete, a losing record. */
    val Danger = Color(0xFFFF8A80)

    // ---- card text tokens ------------------------------------------------

    // Card rules text uses the site's own markup vocabulary (see CardText.kt).
    // These three are deliberately distinct: a keyword, a crew and an attribute
    // are different kinds of thing and the printed card prints them differently.
    val TokenKeyword = Accent
    val TokenType = Color(0xFF6FD3E8)
    val TokenAttribute = Color(0xFFFFB74D)
}
