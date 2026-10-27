package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R
import com.onyx.keyboard.data.db.ThemeEntity
import kotlinx.coroutines.delay

/**
 * DEDICATED THEMES PANEL — the in-keyboard upgrade of the theme section.
 *
 * Replaces "dig through quick settings" with a 3-column gallery: a create
 * card opens the CUSTOM THEME EDITOR, every user theme carries an edit
 * pencil, and all factory presets render a live mini-keyboard preview in
 * their own palette, a light/dark badge (auto day/night awareness) and a
 * check ring on the active look. Tapping a card applies it instantly
 * (haptic + Room persist).
 */
@Composable
fun ThemesPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val themes by controller.themes.collectAsState()

    val createLabel = stringResource(R.string.themes_create)
    val myThemePrefix = stringResource(R.string.editor_my_theme)
    val importLabel = stringResource(R.string.themes_import)
    val badCodeLabel = stringResource(R.string.themes_import_bad_code)
    val custom = themes.filter { !it.builtin }
    val builtin = themes.filter { it.builtin }

    // Clipboard import feedback — flashes when the paste holds no valid code.
    var importError by remember { mutableStateOf(false) }
    LaunchedEffect(importError) {
        if (importError) {
            delay(2600)
            importError = false
        }
    }

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        PanelHeader(controller, stringResource(R.string.panel_themes))

        // Import row — one tap turns an ONYX1. code on the clipboard into a
        // new, immediately-applied custom theme.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (importError) {
                Text(
                    text = badCodeLabel,
                    color = theme.glow,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = importLabel,
                color = theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.accent.copy(alpha = 0.14f))
                    .clickable {
                        controller.haptic()
                        if (!controller.importFromClipboard()) importError = true
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "create") {
                CreateThemeCard(
                    label = createLabel,
                    accent = theme.accent,
                    keyText = theme.keyText,
                    onClick = {
                        controller.haptic()
                        controller.openThemeEditor(
                            seed = null,
                            suggestedName = "$myThemePrefix ${custom.size + 1}"
                        )
                    }
                )
            }
            items(custom, key = { it.id }) { t ->
                MiniThemeCard(
                    theme = t,
                    selected = t.id == theme.id,
                    onEdit = {
                        controller.haptic()
                        controller.openThemeEditor(t, "")
                    },
                    onClick = {
                        controller.haptic()
                        controller.switchTheme(t.id)
                    }
                )
            }
            items(builtin, key = { it.id }) { t ->
                MiniThemeCard(
                    theme = t,
                    selected = t.id == theme.id,
                    onEdit = null,
                    onClick = {
                        controller.haptic()
                        controller.switchTheme(t.id)
                    }
                )
            }
        }
    }
}

/** The dashed-feel "+" card that opens the custom theme editor. */
@Composable
private fun CreateThemeCard(
    label: String,
    accent: Color,
    keyText: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.1.dp,
                color = accent.copy(alpha = 0.55f),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = keyText.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MiniThemeCard(
    theme: ThemeEntity,
    selected: Boolean,
    onEdit: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(theme.surfaceTop), Color(theme.surfaceBottom))
                )
            )
            .border(
                width = if (selected) 1.8.dp else 0.8.dp,
                color = if (selected) Color(theme.accent)
                else Color(theme.keyText).copy(alpha = 0.18f),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = theme.name,
                color = Color(theme.keyText),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onEdit != null) {
                // Custom theme → an edit pencil that opens the editor
                // (inner clickable consumes the tap, card click untouched).
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.editor_edit_theme),
                    tint = Color(theme.accent).copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .clickable { onEdit() }
                        .padding(1.dp)
                )
            }
            if (theme.bgUri.isNotEmpty()) {
                // Background-photo badge — this look carries a picture.
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = stringResource(R.string.editor_bg_image),
                    tint = Color(theme.accent).copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(11.dp)
                        .padding(start = 2.dp)
                )
            }
            Icon(
                imageVector = if (theme.isLight()) Icons.Filled.WbSunny else Icons.Filled.DarkMode,
                contentDescription = if (theme.isLight()) stringResource(R.string.themes_mode_light)
                else stringResource(R.string.themes_mode_dark),
                tint = Color(theme.accent).copy(alpha = 0.9f),
                modifier = Modifier
                    .size(11.dp)
                    .padding(start = 2.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Mini key rows — a tiny faithful keyboard rendered in the preset's colors.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) {
                MiniKey(
                    fill = Color(theme.keyFill).copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f).height(9.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) {
                MiniKey(
                    fill = Color(theme.keyFill).copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f).height(7.dp)
                )
            }
            MiniKey(
                fill = Color(theme.accent).copy(alpha = 0.85f),
                modifier = Modifier.weight(1f).height(7.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        MiniKey(
            fill = Color(theme.accent).copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth().height(5.dp)
        )

        if (selected) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.current_theme),
                    tint = Color(theme.accent),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = stringResource(R.string.current_theme),
                    color = Color(theme.accent),
                    fontSize = 8.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniKey(fill: Color, modifier: Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.5.dp))
            .background(fill)
    )
}
