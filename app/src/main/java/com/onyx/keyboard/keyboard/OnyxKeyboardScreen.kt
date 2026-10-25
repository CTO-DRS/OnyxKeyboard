package com.onyx.keyboard.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.ui.components.ParticleField

/**
 * The full Onyx IME surface: particle background, smart toolbar,
 * and animated page switching between letters / symbols / panels.
 */
@Composable
fun OnyxKeyboardScreen(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val page by controller.page.collectAsState()

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(theme.surfaceTop, theme.surfaceBottom))
            )
    ) {
        if (controller.particles.collectAsState().value) {
            ParticleField(
                // CRITICAL FIX (keyboard floating at top of screen):
                // fillMaxSize() made this Canvas fill the entire IME window's
                // max constraints, so the Box (and therefore the IME window)
                // measured to FULL SCREEN height — keys rendered at the top
                // with a huge empty particle area below. matchParentSize()
                // sizes the overlay to the keyboard content WITHOUT being
                // measured by the Box, so the window wraps the keys and the
                // IME docks at the bottom as expected.
                modifier = Modifier.matchParentSize(),
                color = theme.particle,
                count = 22
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            SmartToolbar(controller)

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(180)) { it / 8 } + fadeOut(tween(120)))
                },
                label = "kbPage"
            ) { p ->
                when (p) {
                    KeyboardPage.LETTERS -> LettersPage(controller)
                    KeyboardPage.SYMBOLS -> SymbolsPage(controller)
                    KeyboardPage.EMOJI -> EmojiPanel(controller)
                    KeyboardPage.CLIPBOARD -> ClipboardPanel(controller)
                    KeyboardPage.TRANSLATE -> TranslatePanel(controller)
                    KeyboardPage.GIF -> GifPanel(controller)
                    KeyboardPage.VOICE -> VoicePanel(controller)
                    KeyboardPage.SETTINGS -> SettingsPanel(controller)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ pages

@Composable
private fun LettersPage(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val lang by controller.lang.collectAsState()
    val shift by controller.shift.collectAsState()
    val rows = KeyboardLayouts.letterRows(lang, shift)

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth()) {
                if (index == 2) {
                    KeyIcon(
                        icon = when (shift) {
                            ShiftState.OFF -> Icons.Filled.ArrowUpward
                            ShiftState.ON -> Icons.Filled.ArrowUpward
                            ShiftState.CAPS -> Icons.Filled.Lock
                        },
                        contentDescription = "Shift",
                        accent = if (shift != ShiftState.OFF) theme.accent else theme.accent.copy(alpha = 0.6f),
                        glow = theme.glow,
                        keyFill = if (shift != ShiftState.OFF) theme.accent.copy(alpha = 0.35f) else theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.toggleShift() },
                        weight = 1.4f
                    )
                }
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.onChar(key) },
                        height = 50.dp
                    )
                }
                if (index == 2) {
                    KeyIcon(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.onBackspace() },
                        onLongClick = { controller.onBackspaceWord() },
                        holdRepeat = { controller.onBackspace() },
                        weight = 1.4f
                    )
                }
            }
        }
        BottomRow(controller)
    }
}

@Composable
private fun SymbolsPage(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    var alt by remember { mutableStateOf(false) }
    val rows = if (alt) KeyboardLayouts.symbolRowsAlt else KeyboardLayouts.symbolRows

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.commitText(key) },
                        height = 50.dp,
                        fontSize = 16
                    )
                }
                if (index == 2) {
                    KeyIcon(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.onBackspace() },
                        onLongClick = { controller.onBackspaceWord() },
                        holdRepeat = { controller.onBackspace() },
                        weight = 1.4f,
                        height = 50.dp
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton(
                label = if (alt) "1#" else "#+=",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.accent.copy(alpha = 0.25f),
                keyText = theme.keyText,
                onClick = { alt = !alt },
                weight = 1.6f,
                height = 50.dp,
                fontSize = 14
            )
            KeyButton(
                label = "ABC",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.keyFill,
                keyText = theme.accent,
                onClick = { controller.setPage(KeyboardPage.LETTERS) },
                weight = 1.6f,
                height = 50.dp,
                fontSize = 14
            )
            KeyIcon(
                icon = Icons.Filled.SpaceBar,
                contentDescription = "Space",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.keyFill,
                keyText = theme.keyText,
                onClick = { controller.onSpace() },
                weight = 4.5f,
                height = 50.dp
            )
            KeyIcon(
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = "Enter",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.accent.copy(alpha = 0.28f),
                keyText = theme.keyText,
                onClick = { controller.commitEnter() },
                weight = 1.7f,
                height = 50.dp
            )
        }
    }
}

@Composable
private fun BottomRow(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()

    Row(Modifier.fillMaxWidth()) {
        KeyButton(
            label = "?123",
            accent = theme.accent,
            glow = theme.glow,
            keyFill = theme.keyFill,
            keyText = theme.accent,
            onClick = { controller.setPage(KeyboardPage.SYMBOLS) },
            weight = 1.4f,
            height = 50.dp
        )
        // Cursor cluster — replaces the old bottom language key (the toolbar
        // language chip still switches AR/EN). Hold to keep scrolling.
        Box(modifier = Modifier.weight(1.5f)) {
            Row(modifier = Modifier.fillMaxSize()) {
                KeyIcon(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Cursor left",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText,
                    onClick = { controller.moveCursor(-1) },
                    weight = 1f,
                    height = 50.dp,
                    iconSize = 22.dp,
                    holdRepeat = { controller.moveCursor(-1) }
                )
                KeyIcon(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Cursor right",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText,
                    onClick = { controller.moveCursor(1) },
                    weight = 1f,
                    height = 50.dp,
                    iconSize = 22.dp,
                    holdRepeat = { controller.moveCursor(1) }
                )
            }
        }
        KeyIcon(
            icon = Icons.Filled.SpaceBar,
            contentDescription = "Space",
            accent = theme.accent,
            glow = theme.glow,
            keyFill = theme.keyFill,
            keyText = theme.keyText.copy(alpha = 0.75f),
            onClick = { controller.onSpace() },
            weight = 5f,
            height = 50.dp
        )
        KeyIcon(
            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
            contentDescription = "Enter",
            accent = theme.accent,
            glow = theme.glow,
            keyFill = theme.accent.copy(alpha = 0.28f),
            keyText = theme.keyText,
            onClick = { controller.commitEnter() },
            weight = 1.7f,
            height = 50.dp
        )
    }
}
