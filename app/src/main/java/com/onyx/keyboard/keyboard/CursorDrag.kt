package com.onyx.keyboard.keyboard

/**
 * Accumulates raw horizontal drag deltas and converts them into discrete
 * cursor steps of [stepPx] pixels — powers the slide-on-space-bar cursor
 * movement (drag left/right on the space key to walk the caret).
 *
 * Pure arithmetic, no Android imports — unit tested in OnyxPolishPackTest.
 */
class CursorDrag(private val stepPx: Float) {

    private var carry = 0f

    /** Feed one drag delta (px); returns how many characters to move (signed). */
    fun onDelta(dx: Float): Int {
        if (stepPx <= 0f) return 0
        carry += dx
        var steps = 0
        while (carry >= stepPx) { steps++; carry -= stepPx }
        while (carry <= -stepPx) { steps--; carry += stepPx }
        return steps
    }

    /** Call on a new gesture so leftover carry from the previous one is dropped. */
    fun reset() {
        carry = 0f
    }

    companion object {
        /** Pixels of drag per one-character cursor move (converted with density). */
        const val STEP_DP = 26f
    }
}
