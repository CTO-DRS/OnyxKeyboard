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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

/**
 * COMMUNITY THEME ARCHIVE PANEL — the shared shelf inside settings.
 *
 * A 3-column grid of community looks (the offline curated pack plus the
 * online catalog, merged by the controller). Tapping a card one-tap
 * imports + applies it; already-imported looks show a check and simply
 * re-apply. The refresh chip re-fetches the shared catalog; notes (offline
 * / refreshed / gallery full) flash in the top row.
 */
@Composable
fun CommunityArchivePanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val entries by controller.community.collectAsState()
    val note by controller.communityNote.collectAsState()
    val themes by controller.themes.collectAsState()

    // Re-keyed on every themes change so "مستورد" checkmarks appear the
    // moment an import lands in the gallery.
    val importedSignatures = remember(themes) {
        themes.filter { !it.builtin }.map { themeSignature(it) }.toSet()
    }

    LaunchedEffect(note) {
        if (note != null) {
            delay(3200)
            controller.communityNote.value = null
        }
    }

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        PanelHeader(controller, stringResource(R.string.panel_community))

        // Status row: count + note slot + manual refresh.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.community_count, entries.size),
                color = theme.keyText.copy(alpha = 0.55f),
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when (note) {
                    R.string.community_offline -> stringResource(R.string.community_offline)
                    R.string.community_refreshed -> stringResource(R.string.community_refreshed)
                    R.string.community_full -> stringResource(R.string.community_full)
                    else -> ""
                },
                color = if (note == R.string.community_full) theme.glow else theme.keyText.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2.2f, fill = false)
            )
            Spacer(Modifier.weight(0.4f))
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.community_refresh),
                tint = theme.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { controller.refreshCommunityArchive() }
                    .padding(4.dp)
                    .size(16.dp)
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
            items(entries, key = { it.signature }) { e ->
                CommunityCard(
                    entry = e,
                    imported = e.signature in importedSignatures,
                    accent = theme.accent,
                    onClick = { controller.importCommunityTheme(e) }
                )
            }
        }
    }
}

/** One community look: live palette preview + one-tap import. */
@Composable
private fun CommunityCard(
    entry: CommunityEntry,
    imported: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val t = entry.theme
    val miniRadius = (t.keyRadius.coerceIn(0, 26) * 0.18f).dp

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(t.surfaceTop), Color(t.surfaceBottom))
                )
            )
            .border(
                width = if (imported) 1.4.dp else 0.8.dp,
                color = if (imported) Color(t.accent)
                else Color(t.keyText).copy(alpha = 0.18f),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = t.name,
                color = Color(t.keyText),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.community_imported),
                tint = if (imported) Color(t.accent) else Color.Transparent,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = entry.by,
            color = Color(t.keyText).copy(alpha = 0.55f),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) {
                MiniKey(
                    fill = Color(t.keyFill).copy(alpha = 0.85f),
                    radius = miniRadius,
                    modifier = Modifier.weight(1f).height(9.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) {
                MiniKey(
                    fill = Color(t.keyFill).copy(alpha = 0.6f),
                    radius = miniRadius,
                    modifier = Modifier.weight(1f).height(7.dp)
                )
            }
            MiniKey(
                fill = Color(t.accent).copy(alpha = 0.85f),
                radius = miniRadius,
                modifier = Modifier.weight(1f).height(7.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        MiniKey(
            fill = Color(t.accent).copy(alpha = 0.9f),
            radius = miniRadius,
            modifier = Modifier.fillMaxWidth().height(5.dp)
        )
    }
}
