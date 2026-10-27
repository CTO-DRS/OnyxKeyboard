package com.onyx.keyboard.keyboard

/**
 * Physical key layouts. Rows are plain label lists; special keys
 * (shift / backspace / space / enter / mode toggles) are rendered by the UI
 * around them, mirroring real IME layout structure.
 */
object KeyboardLayouts {

    private val arabicRows: List<List<String>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"),
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ"),
    )

    /** Shifted Arabic: punctuation + harakat + Arabic-Indic digits. */
    private val arabicAltRows: List<List<String>> = listOf(
        listOf("أ", "إ", "آ", "؛", "،", "؟", "!", ":", "\"", "/", "٪"),
        listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"),
        listOf("َ", "ُ", "ِ", "ّ", "ْ", "(", ")", "-", "_", "+"),
    )

    private val englishRows: List<List<String>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
    )

    /** Page 1: digits row + top symbols. */
    val symbolRows: List<List<String>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "\$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?", "=", "%"),
    )

    /** Page 2: extended symbols. */
    val symbolRowsAlt: List<List<String>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("£", "€", "¥", "^", "°", "{", "}", "\\", "[", "]"),
        listOf("<", ">", "\$", "«", "»", "…", "≈", "±", "µ", "∞"),
    )

    /** Optional digits row shown above the letters when the user enables it. */
    val numberRow: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    // ------------------------------------------------------- advanced numpad

    /**
     * Slim function row of the advanced numpad (rendered at reduced height).
     * "ABC" is the exit key back to the letters page; the rest commit
     * directly through [numpadCommit].
     */
    val numpadSlim: List<String> = listOf("ABC", "√", "x²", "π", "%", "(", ")")

    /**
     * Main digit/operator grid. Each row is rendered with fixed extras:
     * row 0 gets a backspace icon appended, row 1 and 2 are plain, and the
     * final utility row (0 / . / space / enter) is composed by the UI.
     */
    val numpadRows: List<List<String>> = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×", "+"),
        listOf("1", "2", "3", "−", "="),
    )

    /**
     * Maps an on-screen numpad label to the text it actually commits.
     * Mostly identity — "x²" commits the superscript and "−" falls back to
     * the ASCII hyphen-minus that text fields universally accept.
     */
    fun numpadCommit(label: String): String = when (label) {
        "x²" -> "²"
        "−" -> "-"
        else -> label
    }

    /**
     * The un-shifted physical letter grid for [lang] — exposed so the glide
     * matcher can derive key adjacency (the "letter chain" geometry) from
     * the ONE source of truth instead of a hand-copied map.
     */
    fun baseLetterRows(lang: Lang): List<List<String>> = when (lang) {
        Lang.AR -> arabicRows
        Lang.EN -> englishRows
    }

    fun letterRows(lang: Lang, shift: ShiftState): List<List<String>> = when (lang) {
        Lang.AR -> if (shift == ShiftState.OFF) arabicRows else arabicAltRows
        Lang.EN -> if (shift == ShiftState.OFF) englishRows else {
            englishRows.map { row -> row.map { it.uppercase() } }
        }
    }
}

/** Emoji catalog grouped by category, fully offline. */
object EmojiCatalog {

    data class Category(val label: String, val emojis: List<String>)

    val categories: List<Category> = listOf(
        Category("وجوه", listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🙂", "😉",
            "😊", "😍", "🥰", "😘", "😜", "🤪", "🤗", "🤔", "🤨", "😐",
            "😑", "🙄", "😏", "😴", "😌", "😔", "🙃", "😲", "☹️", "😖",
            "😢", "😭", "😤", "😠", "🤯", "😬", "😰", "🥵", "🥶", "😳",
            "🤤", "😵", "🥴", "🤢", "🤧", "😷", "🤑", "🤠", "😈", "💀",
            "👻", "👽", "🤖", "💩"
        )),
        Category("قلوب", listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "✨",
            "⭐", "🌟", "💫", "🔥", "💥", "⚡", "🎉", "🎊", "🥳", "🎁"
        )),
        Category("إيماءات", listOf(
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉",
            "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤝", "🙏",
            "💪", "🦾", "✍️", "👏", "🙌", "👐", "🤲", "🫶", "🤳", "👊"
        )),
        Category("حيوانات", listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦅", "🦉",
            "🐺", "🐴", "🦄", "🐝", "🦋", "🐌", "🐞", "🐢", "🐍", "🐙"
        )),
        Category("طعام", listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
            "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🍆",
            "🥕", "🌽", "🌶️", "🍞", "🥐", "🥖", "🧀", "🍳", "🍔", "🍟",
            "🍕", "🌮", "🌯", "🥗", "🍝", "🍜", "🍣", "🍤", "🍦", "🍩",
            "🍪", "🎂", "🍰", "🍫", "🍿", "☕", "🍵", "🧃"
        )),
        Category("أنشطة", listOf(
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎱", "🏓", "🏸", "🎮",
            "🎲", "🎯", "🎬", "🎵", "🎶", "🎤", "🎧", "🎸", "🎹", "🥁",
            "🏆", "🥇", "🎨", "🎭", "🎪", "🎰", "🧩", "♟️", "🎳", "🏃"
        )),
        Category("سفر", listOf(
            "🚗", "✈️", "🚀", "🛸", "⛵", "🚲", "🚂", "🚌", "🗺️", "🏝️",
            "🌋", "🏔️", "🌊", "🌧️", "⛅", "🌈", "☀️", "🌙", "☁️", "❄️",
            "🌪️", "🌍", "🏕️", "🏙️", "🛣️", "🚦", "⛽", "🎡", "🎢", "🗼"
        )),
        Category("أشياء", listOf(
            "📱", "💻", "⌨️", "🖥️", "🖱️", "💾", "📷", "📸", "📹", "🔍",
            "💡", "🔦", "💸", "💳", "💎", "⚙️", "🔧", "🔨", "🧲", "🧪",
            "🧬", "🔭", "🌡️", "⏰", "⌛", "📅", "📌", "📎", "✂️", "📚",
            "📖", "✏️", "🖊️", "🗒️", "🔒", "🔑", "🧿", "🔮", "🛠️", "🎁"
        )),
        Category("رموز", listOf(
            "✅", "❌", "❓", "❗", "⚠️", "♻️", "✔️", "✖️", "➕", "➖",
            "➗", "💠", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪",
            "🔺", "🔻", "🔶", "🔷", "🔱", "♠️", "♥️", "♦️", "♣️", "🚩"
        )),
    )
}
