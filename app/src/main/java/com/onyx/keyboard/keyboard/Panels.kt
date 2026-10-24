package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
fun EmojiPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    var category by remember { mutableStateOf(0) }
    val cats = EmojiCatalog.categories

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        PanelHeader(controller, "الرموز التعبيرية")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(cats[category.coerceIn(0, cats.lastIndex)].emojis) { e ->
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
}
