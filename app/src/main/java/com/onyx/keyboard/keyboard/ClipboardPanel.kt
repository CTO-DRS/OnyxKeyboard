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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R

@Composable
fun ClipboardPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val clips by controller.clips.collectAsState()

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.panel_clipboard),
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "Clear all",
                tint = theme.keyText.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        clips.forEach { controller.deleteClip(it) }
                    }
                    .padding(4.dp)
            )
        }

        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.clipboard_empty),
                    color = theme.keyText.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(clips, key = { it.id }) { clip ->
                    val preview by produceState("", clip.id) {
                        value = controller.decryptClip(clip)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { controller.pasteClip(clip) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preview.ifBlank { "…" },
                            color = theme.keyText.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pin",
                            tint = if (clip.pinned) theme.accent
                            else theme.keyText.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { controller.toggleClipPin(clip) }
                                .padding(2.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Delete clip",
                            tint = theme.keyText.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { controller.deleteClip(clip) }
                                .padding(3.dp)
                        )
                    }
                }
            }
        }
    }
}
