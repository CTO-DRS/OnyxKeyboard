package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE LAST-RESORT KEYBOARD — deliberately tiny and independent.
 *
 * When the full OnyxKeyboardSurface fails to compose on some device (a theme
 * glitch, a panel bug, anything), the user must NEVER be left without a way
 * to type. This screen renders the plain letter rows + space/backspace/enter
 * with almost no dependencies: no particle field, no glide layer, no panels,
 * no animated toolbar, no theme-driven drawing. It shares ONLY the plain
 * layout data ([KeyboardLayouts.letterRows]) and the controller's commit
 * actions, so every keystroke lands exactly where the full keyboard would
 * put it.
 *
 * Safety rule: this file must stay boring. No new features land here.
 */
@Composable
fun OnyxFallbackScreen(controller: OnyxKeyboardController) {
    val lang by controller.lang.collectAsState()
    val shift by controller.shift.collectAsState()
    val rows = KeyboardLayouts.letterRows(lang, shift)
    val bg = Color(0xFF15121F)
    val key = Color(0xFF2A2438)
    val fg = Color(0xFFF2EDFF)
    val accent = Color(0xFFB37BFF)

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        // Status strip: language toggle + shift state mirror.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (lang == Lang.AR) "AR" else "EN",
                color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { controller.switchLanguage() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text(
                text = if (shift == ShiftState.OFF) "onyx" else "ONYX",
                color = fg.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 2.sp
            )
        }

        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth()) {
                if (index == 2) {
                    FallbackKey(
                        label = if (shift == ShiftState.OFF) "⇧" else "⇪",
                        bg = if (shift == ShiftState.OFF) key else accent.copy(alpha = 0.4f),
                        fg = fg, weight = 1.4f
                    ) { controller.toggleShift() }
                }
                row.forEach { k ->
                    FallbackKey(label = k, bg = key, fg = fg, weight = 1f) {
                        controller.onChar(k)
                    }
                }
                if (index == 2) {
                    FallbackKey(label = "⌫", bg = key, fg = fg, weight = 1.4f) {
                        controller.onBackspace()
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            FallbackKey(label = "?123", bg = key, fg = accent, weight = 1.4f) {
                controller.setPage(KeyboardPage.SYMBOLS)
            }
            FallbackKey(label = "␣", bg = key, fg = fg, weight = 5f) {
                controller.onSpace()
            }
            FallbackKey(label = "⏎", bg = accent.copy(alpha = 0.30f), fg = fg, weight = 1.7f) {
                controller.commitEnter()
            }
        }
    }
}

/** A plain rounded key — no glow, no animation, no theme plumbing. */
@Composable
private fun RowScope.FallbackKey(
    label: String,
    bg: Color,
    fg: Color,
    weight: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = fg, fontSize = 17.sp, maxLines = 1)
    }
}
