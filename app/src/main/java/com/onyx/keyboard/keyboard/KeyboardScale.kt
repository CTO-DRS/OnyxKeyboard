package com.onyx.keyboard.keyboard

import kotlin.math.abs
import kotlin.math.round

/**
 * PURE geometry of the keyboard's AREA control (مساحة اللوحة) and the text
 * SELECTION mode — no Android imports, no Compose, so every rule here is
 * directly unit-testable and both features stay deterministic at the edges.
 */

/**
 * The global key-height scale behind the RESIZE panel (toolbar's
 * «مساحة اللوحة» button). Every key on the surface multiplies its designed
 * height by this factor through [LocalOnyxKeyScale].
 *
 * The range is deliberately conservative: below ~80% the neon keys stop
 * being reachable with a thumb, above ~130% a full page risks colliding
 * with [com.onyx.keyboard.keyboard.OnyxInputView.Companion.MAX_KEYBOARD_HEIGHT_DP]
 * (620dp) on short screens — the cap still guards, but staying inside the
 * range keeps every panel comfortable.
 */
object KeyboardScale {
    const val MIN = 0.80f
    const val MAX = 1.30f
    const val STEP = 0.05f
    const val DEFAULT = 1.0f

    /**
     * Snaps [value] onto the 5% grid and clamps it into [MIN, MAX].
     * Slider drags produce continuous floats; persisted strings from older
     * builds (or hand-edited settings) coerce safely instead of poisoning
     * the surface with e.g. 17f.
     */
    fun clamp(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return DEFAULT
        val snapped = round(value / STEP) * STEP
        return snapped.coerceIn(MIN, MAX)
    }

    /** 1.0f → 100 (the percentage the resize panel renders). */
    fun percent(value: Float): Int = round(clamp(value) * 100).toInt()
}

/**
 * Pure selection arithmetic for the SELECTION MODE strip (تحديد النص):
 * a FIXED anchor edge plus a focus edge that moves with the extend arrows;
 * the visible selection is always the normalized [start, end] span, even
 * when the focus crosses over the anchor.
 */
object SelectionMath {

    data class ExtendResult(
        val anchor: Int,
        val focus: Int,
        /** Normalized left bound — always <= [end]. */
        val start: Int,
        /** Normalized right bound — always >= [start]. */
        val end: Int,
    )

    /**
     * Extends a selection by [delta] characters (negative = left) while the
     * [anchor] stays pinned. The focus edge moves and the span is
     * normalized; every coordinate is clamped into [0, textLength] so a
     * hostile delta can never push setSelection out of the document (most
     * editors tolerate it, but some crash the frame).
     */
    fun extend(anchor: Int, focus: Int, delta: Int, textLength: Int): ExtendResult {
        val len = textLength.coerceAtLeast(0)
        val a = anchor.coerceIn(0, len)
        val f = (focus + delta).coerceIn(0, len)
        return ExtendResult(
            anchor = a,
            focus = f,
            start = minOf(a, f),
            end = maxOf(a, f),
        )
    }

    /** The threshold (in px) a horizontal drag on the backspace key must
     *  travel before it counts as «swipe = delete the whole word». */
    fun swipeDeletesWord(dragPx: Float, thresholdPx: Float): Boolean =
        abs(dragPx) >= thresholdPx
}
