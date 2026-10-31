package com.onyx.keyboard.data

/**
 * F17 (PRD) — استيراد الاختصارات من CSV.
 *
 * Parses a personal dictionary export (trigger, expansion per row) into
 * shortcut pairs. PURE logic — directly unit-testable, no Android APIs.
 *
 * Accepted shapes (first matching delimiter wins per file):
 *  - comma: `brb,"back right back"`
 *  - semicolon: `brb;back right back`
 *  - tab (spreadsheet copy/paste): `brb<TAB>back right back`
 * RFC-4180-style double quotes are unwrapped; "" inside quotes is a literal
 * quote. A header row (trigger/expansion/اختصار/المختصر…) is skipped.
 */
object CsvShortcuts {

    data class Row(val trigger: String, val expansion: String)

    private val headerHints = listOf(
        "trigger", "shortcut", "expansion", "phrase", "word", "text",
        "المختصر", "الاختصار", "التوسعة", "العبارة", "النص", "الكلمة"
    )

    /** Parses [text] into cleaned rows; malformed and header lines are skipped. */
    fun parse(text: String): List<Row> {
        if (text.isBlank()) return emptyList()
        val delimiter = pickDelimiter(text)
        val rows = mutableListOf<Row>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().trimEnd('\r').trim('"')
            if (line.isEmpty()) continue
            val fields = splitLine(line, delimiter)
            if (fields.size < 2) continue
            val trigger = unquote(fields[0]).trim()
            val expansion = unquote(fields[1]).trim()
            if (trigger.isEmpty() || expansion.isEmpty()) continue
            if (isHeader(trigger, expansion)) continue
            rows += Row(trigger, expansion)
            if (rows.size >= MAX_ROWS) break
        }
        return rows
    }

    /** Hard cap so a giant accidental paste cannot flood the dictionary. */
    const val MAX_ROWS = 500

    private fun pickDelimiter(text: String): Char {
        val probe = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val commas = probe.count { it == ',' }
        val semis = probe.count { it == ';' }
        val tabs = probe.count { it == '\t' }
        return when {
            tabs >= commas && tabs >= semis && tabs > 0 -> '\t'
            semis > commas -> ';'
            else -> ','
        }
    }

    /** Splits on [delimiter], honouring double-quoted fields with embedded separators. */
    private fun splitLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++ // escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    out += cur.toString(); cur.setLength(0)
                }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private fun unquote(s: String): String {
        var t = s.trim()
        if (t.length >= 2 && t.first() == '"' && t.last() == '"') {
            t = t.substring(1, t.length - 1).replace("\"\"", "\"")
        }
        return t
    }

    private fun isHeader(a: String, b: String): Boolean {
        val la = a.lowercase()
        val lb = b.lowercase()
        return headerHints.any { la == it || lb == it }
    }
}
