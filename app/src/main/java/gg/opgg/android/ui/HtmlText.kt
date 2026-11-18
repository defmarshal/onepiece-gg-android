package gg.opgg.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import gg.opgg.android.data.CardText

/** The site whose decks and cards this app reads. */
const val SITE_BASE = "https://onepiece.gg"

/**
 * Deck descriptions, which the site stores as **HTML**.
 *
 * A public deck's description is how a player records where they placed, and the
 * site stores that as a link:
 *
 *     <a href="/tournaments/egb-chinoizecup-123-wednesday">Place 1 on [EGB]
 *     ChinoizeCup #123 Wednesday</a>
 *
 * Rendering that as plain text shows the user the raw markup, which is what
 * "the text on top of the screen is broken" was.
 *
 * Only `<a href>` occurs in practice, but the parser is not built around that:
 * any other tag is stripped and its contents kept, `<br>` becomes a line break,
 * and entities are decoded. That way a description the site changes the format of
 * still reads as text rather than as markup.
 *
 * Links are site-relative (`/tournaments/...`), so they are resolved against
 * [SITE_BASE]; a link that is already absolute is left alone.
 */
internal fun deckDescriptionHtml(
    html: String,
    linkColor: Color = AppColors.Accent,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    var pendingLink: String? = null

    fun appendText(text: String) {
        if (text.isEmpty()) return
        val url = pendingLink
        if (url != null) {
            builder.withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                )
            ) {
                append(text)
            }
        } else {
            builder.append(text)
        }
    }

    while (i < html.length) {
        val c = html[i]
        if (c == '<') {
            val close = html.indexOf('>', i)
            if (close < 0) {
                // Unterminated tag: treat the rest as text rather than losing it.
                appendText(html.substring(i))
                break
            }
            val tag = html.substring(i + 1, close)
            val name = tag.trimStart('/').substringBefore(' ').substringBefore('\t').lowercase()
            when {
                name == "a" && !tag.startsWith("/") -> {
                    val href = HREF.find(tag)?.groupValues?.get(1)
                    pendingLink = href?.let { resolveSiteUrl(it) }
                }
                name == "/a" -> pendingLink = null
                name == "br" -> builder.append('\n')
                name == "p" -> builder.append('\n')
            }
            i = close + 1
            continue
        }
        // Plain text up to the next tag.
        val next = html.indexOf('<', i)
        val end = if (next < 0) html.length else next
        appendText(CardText.decodeEntities(html.substring(i, end)))
        i = end
    }
    return builder.toAnnotatedString()
}

/** The `href` of a tag, quoted either way. */
private val HREF = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)

/**
 * Makes a link absolute.
 *
 * The site's links are relative to the site root. Anything that is already
 * absolute, or that is not a site link at all, is passed through untouched.
 */
private fun resolveSiteUrl(href: String): String {
    val trimmed = href.trim()
    if (trimmed.isEmpty()) return trimmed
    if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        return trimmed
    }
    if (trimmed.startsWith("//")) return "https:$trimmed"
    if (trimmed.startsWith("/")) return SITE_BASE + trimmed
    return SITE_BASE + "/" + trimmed
}
