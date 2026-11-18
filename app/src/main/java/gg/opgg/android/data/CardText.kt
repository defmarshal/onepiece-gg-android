package gg.opgg.android.data

/**
 * One piece of a card's rules text after the site's own markup is understood.
 *
 * The site does not send plain text. Measured across the live card database,
 * `Effect` and `Trigger` together contain:
 *
 *   `<br>`      1,737   a line break between keyword blocks
 *   `<Slash>`       42   an *attribute* reference, e.g. "by <Slash> cards"
 *   `<Strike>`       6
 *   `<Special>`      1
 *   `{...}`         61 distinct tokens, every one of them a **crew** name
 *   `[...]`          keyword markers, e.g. `[On Play]`, `[Blocker]`
 *   `&#039;`       207   a plain apostrophe
 *
 * Note `<Slash>` is not an HTML tag and `{Straw Hat Crew}` is not HTML either:
 * they are the site's own token syntax, which is why this is a small parser
 * rather than a call to an HTML renderer. There is **no** `{COLOUR}` form - all
 * 61 brace tokens are crew names, verified against the card database.
 */
sealed interface CardTextPart {
    data class Plain(val text: String) : CardTextPart

    /** `<br>` or a real newline in the payload. */
    data object Break : CardTextPart

    /** `[On Play]`, `[Blocker]`, `[DON!! x1]` - also used for card names. */
    data class Keyword(val text: String) : CardTextPart

    /** `{Straw Hat Crew}` - a crew the text refers to. */
    data class Type(val text: String) : CardTextPart

    /** `<Slash>`, `<Strike>`, `<Special>` - an attribute the text refers to. */
    data class Attribute(val text: String) : CardTextPart
}

object CardText {

    private val BREAK = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("<([A-Za-z][A-Za-z ]{0,30})>")
    private val TYPE = Regex("\\{([^{}]{1,60})\\}")
    private val KEYWORD = Regex("\\[([^\\[\\]]{1,60})\\]")

    /** Only the entities the payload actually uses, plus the obvious neighbours. */
    private val ENTITIES = mapOf(
        "&#039;" to "'",
        "&apos;" to "'",
        "&#34;" to "\"",
        "&quot;" to "\"",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&nbsp;" to " ",
    )

    /**
     * Splits [raw] into styled parts. Never returns an empty leading part, and
     * never loses text: concatenating every part reproduces the input once
     * entities are unescaped and the delimiters are dropped.
     */
    fun parse(raw: String?): List<CardTextPart> {
        if (raw.isNullOrEmpty()) return emptyList()
        val out = ArrayList<CardTextPart>()
        var buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                out += CardTextPart.Plain(buffer.toString())
                buffer = StringBuilder()
            }
        }

        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\n' || c == '\r' -> {
                    flush()
                    out += CardTextPart.Break
                    // Treat \r\n as one break. The trailing i++ is required: this
                    // branch is the only one that does not advance on its own, and
                    // without it a \r\n loops forever appending Break.
                    if (c == '\r' && i + 1 < raw.length && raw[i + 1] == '\n') i++
                    i++
                }

                c == '<' -> {
                    val rest = raw.substring(i)
                    val br = BREAK.find(rest)
                    if (br != null && br.range.first == 0) {
                        flush()
                        out += CardTextPart.Break
                        i += br.value.length
                    } else {
                        val attr = ATTRIBUTE.find(rest)
                        if (attr != null && attr.range.first == 0) {
                            flush()
                            out += CardTextPart.Attribute(attr.groupValues[1].trim())
                            i += attr.value.length
                        } else {
                            buffer.append(c); i++
                        }
                    }
                }

                c == '{' -> {
                    val m = TYPE.matchAt(raw, i)
                    if (m != null && m.range.first == i) {
                        flush()
                        out += CardTextPart.Type(m.groupValues[1].trim())
                        i += m.value.length
                    } else {
                        buffer.append(c); i++
                    }
                }

                c == '[' -> {
                    val m = KEYWORD.matchAt(raw, i)
                    if (m != null && m.range.first == i) {
                        flush()
                        out += CardTextPart.Keyword(m.groupValues[1].trim())
                        i += m.value.length
                    } else {
                        buffer.append(c); i++
                    }
                }

                c == '&' -> {
                    val hit = ENTITIES.entries.firstOrNull { raw.startsWith(it.key, i) }
                    if (hit != null) {
                        buffer.append(hit.value)
                        i += hit.key.length
                    } else {
                        buffer.append(c); i++
                    }
                }

                else -> {
                    buffer.append(c); i++
                }
            }
        }
        flush()
        return out
    }

    /** True when [raw] carries any markup this parser would act on. */
    fun hasMarkup(raw: String?): Boolean =
        raw != null && (BREAK.containsMatchIn(raw) || TYPE.containsMatchIn(raw) ||
            KEYWORD.containsMatchIn(raw) || ENTITIES.keys.any { raw.contains(it) })

    /**
     * Unescapes the HTML entities the site sends, and nothing else.
     *
     * Exposed on its own because the same entities turn up outside the rules
     * text - the pack names in `CardSets` carry `&#039;` and `&amp;` too, and
     * those are not card text, so they must not be run through the token parser.
     */
    fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '&') {
                val hit = ENTITIES.entries.firstOrNull { raw.startsWith(it.key, i) }
                if (hit != null) {
                    out.append(hit.value)
                    i += hit.key.length
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
