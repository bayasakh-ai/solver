package com.bay.sudokusolver

/*
 * The privacy policy, shown inside the app (Settings > About; Google Play wants it
 * reachable there as well as on the store page). The text is docs/privacy-policy.md,
 * copied into assets/privacy-policy.md: one text for the web page and the app
 * (PolicyTextTest fails when the two copies differ). The app has no internet access, so
 * it cannot open the web page; it draws the same Markdown itself. Pure Kotlin.
 *
 * Only what that file uses is understood: `# title`, `## heading`, `- bullets` with
 * indented continuation lines, paragraphs wrapped over several lines, and **strong**,
 * *emphasis* and `code` inside a line.
 */
object PolicyText {

    enum class Kind { TITLE, HEADING, PARAGRAPH, BULLET }

    /** A run of text; [strong] = `**..**`, [emphasis] = `*..*`. Code spans come out plain. */
    data class Span(val text: String, val strong: Boolean = false, val emphasis: Boolean = false)

    data class Block(val kind: Kind, val spans: List<Span>) {
        val text: String get() = spans.joinToString("") { it.text }
    }

    fun parse(markdown: String): List<Block> {
        val blocks = ArrayList<Block>()
        var kind: Kind? = null
        val buffer = StringBuilder()
        fun flush() {
            val k = kind
            if (k != null && buffer.isNotBlank()) blocks.add(Block(k, spans(buffer.toString().trim())))
            kind = null
            buffer.clear()
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("## ") -> { flush(); blocks.add(Block(Kind.HEADING, spans(line.drop(3).trim()))) }
                line.startsWith("# ") -> { flush(); blocks.add(Block(Kind.TITLE, spans(line.drop(2).trim()))) }
                line.startsWith("- ") -> { flush(); kind = Kind.BULLET; buffer.append(line.drop(2).trim()) }
                kind == null -> { kind = Kind.PARAGRAPH; buffer.append(line.trim()) }
                else -> buffer.append(' ').append(line.trim()) // a wrapped line of the same paragraph or bullet
            }
        }
        flush()
        return blocks
    }

    /** `**strong**`, `*emphasis*` and `code` in one line of text. An unclosed marker is kept as typed. */
    fun spans(text: String): List<Span> {
        val out = ArrayList<Span>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) out.add(Span(plain.toString()))
            plain.clear()
        }
        var i = 0
        while (i < text.length) {
            val marker = when {
                text.startsWith("**", i) -> "**"
                text[i] == '*' || text[i] == '`' -> text[i].toString()
                else -> null
            }
            val end = if (marker == null) -1 else text.indexOf(marker, i + marker.length)
            if (marker == null || end < 0) {
                plain.append(text[i]); i++
                continue
            }
            val inner = text.substring(i + marker.length, end)
            if (marker == "`") plain.append(inner)
            else { flushPlain(); out.add(Span(inner, strong = marker == "**", emphasis = marker == "*")) }
            i = end + marker.length
        }
        flushPlain()
        return out
    }
}
