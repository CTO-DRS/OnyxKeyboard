package com.onyx.keyboard

import android.text.InputType
import com.onyx.keyboard.keyboard.IncognitoPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F11 (PRD) — وضع التخفي واستثناء حقول كلمات المرور: the pure policy.
 */
class IncognitoPolicyTest {

    private fun text(variation: Int) =
        InputType.TYPE_CLASS_TEXT or variation

    @Test
    fun `password variations force incognito`() {
        assertTrue(IncognitoPolicy.isPasswordField(text(InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertTrue(IncognitoPolicy.isPasswordField(text(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        assertTrue(IncognitoPolicy.isPasswordField(text(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
    }

    @Test
    fun `number password forces incognito`() {
        assertTrue(
            IncognitoPolicy.isPasswordField(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )
        )
    }

    @Test
    fun `normal fields are never incognito`() {
        assertFalse(IncognitoPolicy.isPasswordField(text(InputType.TYPE_TEXT_VARIATION_NORMAL)))
        assertFalse(IncognitoPolicy.isPasswordField(text(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)))
        assertFalse(IncognitoPolicy.isPasswordField(text(InputType.TYPE_TEXT_VARIATION_URI)))
        assertFalse(
            IncognitoPolicy.isPasswordField(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
            )
        )
        assertFalse(IncognitoPolicy.isPasswordField(InputType.TYPE_CLASS_PHONE))
        assertFalse(IncognitoPolicy.isPasswordField(InputType.TYPE_CLASS_DATETIME))
        assertFalse(IncognitoPolicy.isPasswordField(0))
    }

    @Test
    fun `email field is not a password field`() {
        assertFalse(
            IncognitoPolicy.isPasswordField(
                text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            )
        )
    }
}
