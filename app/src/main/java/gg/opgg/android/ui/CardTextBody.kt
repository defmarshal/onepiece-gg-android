package gg.opgg.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.opgg.android.data.CardText
import gg.opgg.android.data.CardTextPart

private val KeywordColour = AppColors.Accent
private val TypeColour = AppColors.TokenType
private val AttributeColour = AppColors.TokenAttribute

/**
 * Renders a card's rules text, understanding the site's own markup.
 *
 * Every consumer goes through here rather than calling `Text(card.Effect)`, so
 * the card detail, deck views and any future surface cannot drift apart and
 * start showing `<br>` again.
 *
 * @param showTokens style `{FILM}` and `<Slash>` as highlighted tokens. When
 *   false they render as their bare words, which loses the distinction between
 *   "a card of type {FILM}" and the literal word FILM.
 */
@Composable
internal fun CardTextBody(
    raw: String?,
    fontSize: TextUnit = 13.sp,
    colour: Color = AppColors.OnSurface,
    showTokens: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (raw.isNullOrEmpty()) return
    Text(
        text = cardTextAnnotated(raw, fontSize, colour, showTokens),
        fontSize = fontSize,
        color = colour,
        modifier = modifier,
    )
}

/**
 * Builds the styled string. Extracted from the composable so the styling is
 * exercised by unit tests without a device.
 *
 * A "pill" is a background on the span rather than a real composable, so the
 * text stays a single flowing paragraph and still wraps naturally - a row of
 * boxes would break mid-sentence and lose the paragraph.
 */
internal fun cardTextAnnotated(
    raw: String?,
    fontSize: TextUnit,
    base: Color,
    showTokens: Boolean = true,
): AnnotatedString {
    if (raw.isNullOrEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        for (part in CardText.parse(raw)) {
            when (part) {
                is CardTextPart.Plain -> append(part.text)

                is CardTextPart.Break -> append("\n")

                is CardTextPart.Keyword -> withSpan(
                    part.text,
                    KeywordColour,
                    bold = true,
                )

                is CardTextPart.Type -> withSpan(
                    part.text,
                    TypeColour,
                    bold = false,
                    pill = showTokens,
                )

                is CardTextPart.Attribute -> withSpan(
                    part.text,
                    AttributeColour,
                    bold = false,
                    pill = showTokens,
                )
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withSpan(
    text: String,
    tint: Color,
    bold: Boolean,
    pill: Boolean = true,
) {
    pushStyle(
        SpanStyle(
            color = tint,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            // A translucent tint reads as a highlight without needing a box, which
            // would fragment the paragraph and change the line height.
            background = if (pill) tint.copy(alpha = 0.14f) else Color.Unspecified,
        ),
    )
    append(text)
    pop()
}
