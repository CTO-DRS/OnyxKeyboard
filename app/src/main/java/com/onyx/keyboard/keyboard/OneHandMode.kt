package com.onyx.keyboard.keyboard

/**
 * ONE-HANDED MODE — shrinks the whole keyboard (letters, symbols, every
 * panel) to ~84% width and docks it to one screen edge so the thumb of the
 * hand that actually holds the phone can reach every key.
 *
 * The value is persisted in the settings table ("one_hand") and streamed
 * into [OnyxKeyboardController.oneHand] by the IME service, exactly like
 * the number-row toggle.
 */
enum class OneHand(val id: String) {
    OFF("off"),
    LEFT("left"),
    RIGHT("right");

    /** The opposite dock side (OFF stays OFF) — powers the grip flip button. */
    val flipped: OneHand
        get() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
            OFF -> OFF
        }

    companion object {
        /** Tolerant parse: trims, lowercases, unknown/absent values fall to OFF. */
        fun from(raw: String?): OneHand =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: OFF
    }
}
