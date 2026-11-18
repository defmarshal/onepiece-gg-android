package gg.opgg.android.ui

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The app's type sizes, in one place.
 *
 * Before this there were **11 literal sizes across 154 call sites**, and two of
 * them - 11.sp and 12.sp - were 98 of those. That is why the screens read flat: a
 * card name and a statistic were nearly the same size, so nothing looked more
 * important than anything else.
 *
 * Five roles, each with a job:
 *
 * - [caption] - the smallest legible text: timestamps, "as of" dates, the "max N"
 *   under a card tile's + button. Absorbs the old 8/9/10.sp strays, which existed
 *   only because captions were added late and went ad hoc.
 * - [meta] - stats, counts, subtitles. The workhorse.
 * - [body] - card names, effect text, anything being read rather than scanned.
 * - [title] - screen titles and dialog titles.
 * - [display] - the few genuinely large numbers.
 *
 * Deliberately kept as a plain object of [TextUnit] rather than a Material
 * `Typography`, so an existing `fontSize = 12.sp` becomes `fontSize = AppType.body`
 * with no other change at each of the 154 sites.
 */
object AppType {
    /** 10.sp - timestamps, counts-aside, the "max N" caption. */
    val caption: TextUnit = 10.sp

    /** 11.sp - stats, counts, subtitles. */
    val meta: TextUnit = 11.sp

    /** 13.sp - card names, effect text, body copy. */
    val body: TextUnit = 13.sp

    /** 15.sp - screen and dialog titles. */
    val title: TextUnit = 15.sp

    /** 20.sp - the rare large number. */
    val display: TextUnit = 20.sp
}
