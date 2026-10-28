package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R

@Composable
internal fun PanelHeader(controller: OnyxKeyboardController, title: String) {
    val theme by controller.theme.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = theme.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close panel",
            tint = theme.keyText.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable { controller.setPage(KeyboardPage.LETTERS) }
                .padding(4.dp)
        )
    }
}

/** Special category index for the recently-used emojis row. */
internal const val EMOJI_CATEGORY_RECENT = -1

/**
 * THE EMOJI PANEL — categorized shelves + RECENTS + live ARABIC SEARCH.
 *
 * Browsing (default): category chips + grid, 286dp tall.
 * Search (after tapping the field): the query lives in the controller's
 * internal capture (the IME's own keys can NEVER reach a Compose TextField
 * inside the IME surface), the grid becomes the match results, and the
 * letters keyboard (compact, glide disabled) renders UNDER the panel so the
 * user can actually type the query — the Gboard pattern. Picking a result
 * commits to the host field and KEEPS the search open for related picks;
 * leaving the page (close / any page switch) releases the capture.
 */
@Composable
fun EmojiPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val recent by controller.recentEmojis.collectAsState()
    val searchActive by controller.emojiSearch.collectAsState()
    val query = controller.internalCapture.collectAsState().value ?: ""
    var category by remember { mutableStateOf(0) }
    val cats = EmojiCatalog.categories
    val results = remember(query) { EmojiCatalog.search(query) }

    Column(Modifier.fillMaxWidth()) {
        PanelHeader(controller, stringResource(R.string.emoji_panel_title))

        // Search field — tap to engage capture mode; shows the live query.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.keyFill.copy(alpha = 0.55f))
                .clickable { if (!searchActive) controller.beginEmojiSearch() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = theme.accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = query.ifEmpty { stringResource(R.string.emoji_search_hint) },
                color = if (query.isEmpty()) theme.keyText.copy(alpha = 0.45f) else theme.keyText,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            if (searchActive) {
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.emoji_search_clear),
                        tint = theme.keyText.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { controller.clearEmojiSearch() }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.CloseFullscreen,
                        contentDescription = stringResource(R.string.emoji_search_close),
                        tint = theme.accent,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { controller.endEmojiSearch() }
                    )
                }
            }
        }

        if (searchActive) {
            // RESULTS grid (or recents while the query is still empty), a
            // compact window — the letters keyboard sits right below.
            Box(Modifier.fillMaxWidth().height(122.dp)) {
                val list = if (query.isEmpty()) recent else results
                if (list.isEmpty() && query.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.emoji_no_results),
                        color = theme.keyText.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    EmojiGrid(list, 6, controller)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (recent.isNotEmpty()) {
                    FilterChip(
                        selected = category == EMOJI_CATEGORY_RECENT,
                        onClick = { category = EMOJI_CATEGORY_RECENT },
                        label = { Text(stringResource(R.string.emoji_recent), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = theme.keyFill.copy(alpha = 0.6f),
                            selectedContainerColor = theme.accent.copy(alpha = 0.35f),
                            labelColor = theme.keyText.copy(alpha = 0.85f),
                            selectedLabelColor = theme.keyText
                        )
                    )
                }
                cats.forEachIndexed { i, cat ->
                    FilterChip(
                        selected = category == i,
                        onClick = { category = i },
                        label = { Text(cat.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = theme.keyFill.copy(alpha = 0.6f),
                            selectedContainerColor = theme.accent.copy(alpha = 0.35f),
                            labelColor = theme.keyText.copy(alpha = 0.85f),
                            selectedLabelColor = theme.keyText
                        )
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(214.dp)) {
                val emojis = if (category == EMOJI_CATEGORY_RECENT) recent
                else cats[category.coerceIn(0, cats.lastIndex)].emojis
                EmojiGrid(emojis, 8, controller)
            }
        }
    }

    if (searchActive) {
        // The typing surface for the query — compact (number row hidden),
        // glide disarmed inside the controller while capture is active.
        LettersPage(controller, compact = true)
    }
}

/** Shared emoji grid: [columns] cells, tap commits through [controller]. */
@Composable
private fun EmojiGrid(
    emojis: List<String>,
    columns: Int,
    controller: OnyxKeyboardController,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(emojis) { e ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { controller.insertEmoji(e) }
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = e, fontSize = 24.sp)
            }
        }
    }
}
