package com.onyx.keyboard.keyboard

/**
 * Long-press alternative characters (accents & related glyphs).
 *
 * A core keyboard feature that was missing: holding a letter key pops a
 * strip of alternative characters above the toolbar. The user can either
 * tap an alternative or slide onto one and release to type it.
 *
 * - Latin (EN): accented vowels/consonants + common typographic symbols.
 * - Arabic (AR): hamza carriers and alef variants that share a base key.
 * - [harakat]: Arabic diacritics surfaced by long-pressing the SPACE key —
 *   they are appended to the word being composed exactly like letters.
 */
object AltChars {

    /** EN letter key -> alternatives (order = popup order). */
    val latin: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ã", "ä", "å", "æ", "ª"),
        "c" to listOf("ç", "ć", "č", "©"),
        "d" to listOf("đ"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė"),
        "g" to listOf("ğ"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "l" to listOf("ł", "£"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø", "œ", "º"),
        "r" to listOf("ř", "®"),
        "s" to listOf("ś", "š", "ß", "§"),
        "t" to listOf("þ", "ŧ", "™"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "w" to listOf("ŵ"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
    )

    /** AR letter key -> alternatives (hamza carriers & alef forms). */
    val arabic: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ٱ"),
        "ه" to listOf("ة"),
        "و" to listOf("ؤ"),
        "ي" to listOf("ئ", "ى"),
    )

    /**
     * Arabic diacritics (tashkeel) offered by long-pressing the space key:
     * fatha, damma, kasra, shadda, sukun, fathatan, dammatan, kasratan.
     */
    val harakat: List<String> = listOf("َ", "ُ", "ِ", "ّ", "ْ", "ً", "ٌ", "ٍ")

    /** Alternatives for a key in the current language, or null if none. */
    fun alternativesFor(lang: Lang, key: String): List<String>? = when (lang) {
        Lang.EN -> latin[key.lowercase()]
        Lang.AR -> arabic[key]
    }

    /** True when the character is an Arabic diacritic from [harakat]. */
    fun isHaraka(c: Char): Boolean = c.code in 0x064B..0x0652

    /** Strips harakat from a word (used before recording it for learning). */
    fun stripHarakat(word: String): String =
        if (word.any { isHaraka(it) }) word.filterNot { isHaraka(it) } else word
}
