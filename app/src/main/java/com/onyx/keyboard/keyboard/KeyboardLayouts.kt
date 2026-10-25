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
