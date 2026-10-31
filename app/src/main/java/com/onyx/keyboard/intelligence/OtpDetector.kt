package com.onyx.keyboard.intelligence

/**
 * F14 (PRD) — رمز التحقق المكتشف (OTP detection).
 *
 * Scans clipboard text for a one-time verification code and surfaces it as a
 * one-tap insert chip in the smart toolbar. PURE logic — no Android APIs — so
 * the whole detector is directly unit-testable.
 *
 * Design rules (false positives are worse than a missed rare catch):
 *  - Codes are 4..8 digits, optionally split into two groups by a space or a
 *    dash ("123 456", "123-456").
 *  - A keyword (رمز، كود، التحقق، code، OTP…) anywhere in the message boosts
 *    confidence and unlocks longer messages.
 *  - Without a keyword the message must be SHORT and mostly the code itself —
 *    a bare "1234" or "5522 8890" pasted alone still inserts, but a random
 *    number buried in a long paragraph never triggers the chip.
 *  - Numbers that are part of a longer digit run (phone fragments, prices
 *    with thousand separators like 1,200,000) are ignored.
 *  - With a keyword and MULTIPLE candidates, a 6-8-digit run wins: real OTPs
 *    are 6-8 digits, while 4-digit runs next to them are usually sender
 *    prefixes ("G-5522", "FB-1234") or noise.
 */
object OtpDetector {

    /** Codes get useless on very long text — keep the chip for SMS-shaped messages. */
    private const val MAX_BOOSTED_LENGTH = 200
    private const val MAX_PLAIN_LENGTH = 48

    /** Words that signal a verification context (Arabic + English). */
    private val boosted = Regex(
        "(رمز|كود|الرمز|الكود|التحقق|التفعيل|تأكيد|مصادقة|code|otp|pin|verification|verify|passcode|confirm)",
        RegexOption.IGNORE_CASE
    )

    /** A standalone 4..8-digit run, not touching other digits on either side. */
    private val plain = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

    /** Two 3..4-digit groups joined by one space or dash: "123 456" / "1234-456". */
    private val split = Regex("(?<!\\d)(\\d{3,4})[ \\-](\\d{3,4})(?!\\d)")

    /**
     * Returns the code (digits only, separators stripped) or null when the
     * text holds no confident verification code.
     */
    fun detect(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null

        val hasKeyword = boosted.containsMatchIn(t)

        // Split form first — "123 456" is a stronger signal than its parts.
        split.find(t)?.let { m ->
            val digits = m.value.filter { it.isDigit() }
            // 6-8 digits is the real-world code range for split forms.
            if (digits.length in 6..8 && (hasKeyword || t.length <= MAX_PLAIN_LENGTH)) {
                return digits
            }
        }

        val matches = plain.findAll(t).toList()
        if (matches.isEmpty()) return null
        if (hasKeyword && t.length <= MAX_BOOSTED_LENGTH) {
            val cands = matches.map { it.groupValues[1] }
            // "G-5522 هو رمز التحقق 903411" — the sender prefix must not win
            // over the real code: prefer a 6-8-digit run, else keep the first.
            return cands.firstOrNull { it.length in 6..8 } ?: cands.first()
        }
        // No keyword: accept only when the text is basically the code itself.
        if (t.length <= MAX_PLAIN_LENGTH) {
            val first = matches.first()
            val digitsTotal = t.count { it.isDigit() }
            // The code dominates the message (a bare code, or a code with a
            // tiny label like "كودك: 5522"). Cuts years inside short lines.
            if (digitsTotal == first.groupValues[1].length) {
                return first.groupValues[1]
            }
        }
        return null
    }
}
