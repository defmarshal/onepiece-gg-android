package gg.opgg.android

import androidx.compose.ui.text.LinkAnnotation
import gg.opgg.android.ui.SITE_BASE
import gg.opgg.android.ui.deckDescriptionHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck descriptions are HTML on the site.
 *
 * A public deck's description is where a player records where they placed, and
 * the site stores that as a link:
 *
 *     <a href="/tournaments/egb-chinoizecup-123-wednesday">Place 1 on [EGB]
 *     ChinoizeCup #123 Wednesday</a>
 *
 * Printed as plain text that is what the user saw: raw markup, with a
 * tournament link they could not read and could not follow.
 */
class DeckDescriptionHtmlTest {

    @Test
    fun anAnchorBecomesItsText() {
        val out = deckDescriptionHtml(
            """<a href="/tournaments/egb-chinoizecup-123-wednesday">""" +
                """Place 1 on [EGB] ChinoizeCup #123 Wednesday</a>"""
        )
        assertEquals("Place 1 on [EGB] ChinoizeCup #123 Wednesday", out.text)
    }

    @Test
    fun theLinkIsMadeAbsolute() {
        // The site's links are relative to its own root, so they have to be
        // resolved or the browser gets a path with no host.
        val out = deckDescriptionHtml("""<a href="/tournaments/x">t</a>""")
        val links = out.getLinkAnnotations(0, out.length)
        assertEquals(1, links.size)
        val url = (links.first().item as LinkAnnotation.Url).url
        assertTrue("expected an absolute url, got $url", url.startsWith(SITE_BASE))
        assertTrue(url.endsWith("/tournaments/x"))
    }

    @Test
    fun anAbsoluteLinkIsLeftAlone() {
        val out = deckDescriptionHtml("""<a href="https://example.com/x">t</a>""")
        val links = out.getLinkAnnotations(0, out.length)
        assertEquals("https://example.com/x", (links.first().item as LinkAnnotation.Url).url)
    }

    @Test
    fun plainTextIsUnchanged() {
        val text = "Replace 2x 1C Otohime with 2x EB05-11 Otoime."
        assertEquals(text, deckDescriptionHtml(text).text)
    }

    @Test
    fun newlinesInPlainDescriptionsSurvive() {
        val text = "first line\n\nsecond line"
        assertEquals(text, deckDescriptionHtml(text).text)
    }

    @Test
    fun unknownTagsAreStrippedButTheirTextIsKept() {
        // The site only sends <a>, but a description must not degrade into
        // showing markup if that ever changes.
        val out = deckDescriptionHtml("<p>Hello <b>there</b></p>")
        assertEquals("Hello there", out.text.replace("\n", ""))
    }

    @Test
    fun aLineBreakTagBecomesALineBreak() {
        val out = deckDescriptionHtml("one<br>two")
        assertEquals("one\ntwo", out.text)
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("Ace & Newgate", deckDescriptionHtml("Ace &amp; Newgate").text)
    }

    @Test
    fun anUnterminatedTagDoesNotLoseTheRest() {
        val out = deckDescriptionHtml("before <a href=\"/x")
        assertTrue("kept the text before it", out.text.contains("before"))
    }
}
