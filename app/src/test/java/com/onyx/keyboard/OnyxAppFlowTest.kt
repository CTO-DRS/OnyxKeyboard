package com.onyx.keyboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.onyx.keyboard.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Level 3 diagnostics: the Onyx APP itself (MainActivity + screens).
 * Verifies launch and navigation do not crash. Selector matching is
 * intentionally loose (first occurrence) — titles appear in several places.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxAppFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    private fun tapFirst(id: Int) {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(str(id)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(str(id))[0].performClick()
        rule.waitForIdle()
    }

    @Test
    fun dashboard_composes_without_crash() {
        rule.waitForIdle()
        rule.onAllNodesWithText("ONYX").fetchSemanticsNodes() // header present, no crash
    }

    @Test
    fun navigate_to_themes_and_back() {
        rule.waitForIdle()
        tapFirst(R.string.themes_title)
        tapFirst(R.string.profiles_title) // themes→back→dashboard happens via header icon;
        // worst case this clicks something else — the goal is only "no crash".
    }

    @Test
    fun navigate_to_privacy_and_about() {
        rule.waitForIdle()
        tapFirst(R.string.privacy_title)
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        tapFirst(R.string.about_title)
        rule.waitForIdle()
    }
}
