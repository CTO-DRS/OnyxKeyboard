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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/**
 * DEDICATED THEMES PANEL — the in-keyboard upgrade of the theme section.
 *
 * Replaces "dig through quick settings" with a 3-column gallery of all 28
 * presets: every card renders a live mini-keyboard preview in the preset's
 * own palette (surface gradient, key row, accent spacebar), a light/dark
 * badge (auto day/night awareness) and a check ring on the active look.
 * Tapping a card applies the preset instantly (haptic + Room persist).
 */
@Composable
fun ThemesPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val themes by controller.themes.collectAsState()

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        PanelHeader(controller, stringResource(R.string.panel_themes))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(themes, key = { it.id }) { t ->
                MiniThemeCard(
                    theme = t,
                    selected = t.id == theme.id,
                    onClick = {
                        controller.haptic()
                        controller.switchTheme(t.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniThemeCard(
    theme: ThemeEntity,
    selected: Boolean,
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
