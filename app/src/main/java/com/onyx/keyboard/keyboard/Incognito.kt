package com.onyx.keyboard.keyboard

import android.text.InputType

/**
 * F11 (PRD) — وضع التخفي واستثناء حقول كلمات المرور.
 *
 * In password fields Onyx must switch to incognito AUTOMATICALLY: no
 * learning, no suggestions, no predictions, no clipboard chips. The user can
 * also arm a manual temporary incognito mode from the toolbar/quick settings
 * (clearly indicated, never persisted — "مؤقت").
 *
 * PURE policy — the detection takes the raw `EditorInfo.inputType` int and
 * answers "should this field force incognito?" with no Android runtime
 * dependency beyond the constants, so it is directly unit-testable.
 */
object IncognitoPolicy {

    /**
     * True when [inputType] describes a password / secret field:
     *  - text class with the PASSWORD / VISIBLE_PASSWORD / WEB_PASSWORD variations,
     *  - number class with TYPE_NUMBER_VARIATION_PASSWORD.
     * Everything else (email, uri, phone, plain numbers…) is a normal field.
     */
    fun isPasswordField(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation ==
                InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

            else -> false
        }
    }
}
