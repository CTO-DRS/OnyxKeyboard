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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.onyx.keyboard.data.db.ClipEntity
import kotlinx.coroutines.delay

/**
 * IMPROVED CLIPBOARD PANEL
 *
 * Builds on the persisted, encrypted clip history with:
 *  - a live "paste now" chip for whatever is currently on the system clipboard,
 *  - an in-panel search field (filters decrypted previews, case-insensitive),
 *  - Pinned / Recent sections (pins survive trimming and float to the top),
 *  - one-tap re-copy of any old clip back to the system clipboard,
 *  - a two-tap confirm guard on "clear all" (no more accidental wipes),
 *  - an explicit capture-off state wired to the privacy toggle.
 */
@Composable
fun ClipboardPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val clips by controller.clips.collectAsState()
    val quickClip by controller.quickClip.collectAsState()
    val captureOn by controller.clipsEnabled.collectAsState()

    var search by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3000)
            confirmClear = false
        }
    }

    // Batch-decrypt previews once per history change (search + rows share it).
    val previews by produceState(mapOf<Long, String>(), clips) {
        value = clips.associate { it.id to controller.decryptClip(it) }
    }
    fun matches(clip: ClipEntity): Boolean {
        if (search.isBlank()) return true
        return previews[clip.id]?.contains(search.trim(), ignoreCase = true) == true
    }

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        // ---------------------------------------------------------- header
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
            if (clips.isNotEmpty()) {
                if (confirmClear) {
                    Text(
                        text = stringResource(R.string.clipboard_confirm_clear),
                        color = theme.glow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                confirmClear = false
                                controller.clearClips()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear all",
                    tint = if (confirmClear) theme.glow
                    else theme.keyText.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (confirmClear) {
                                confirmClear = false
                                controller.clearClips()
                            } else {
                                confirmClear = true
                            }
                        }
                        .padding(4.dp)
                )
            }
        }

        // ---------------------------------------------------------- search
        TextField(
            value = search,
            onValueChange = { search = it },
            placeholder = {
                Text(
                    stringResource(R.string.clipboard_search_hint),
                    color = theme.keyText.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = theme.keyText),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.07f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedTextColor = theme.keyText,
                unfocusedTextColor = theme.keyText,
                cursorColor = theme.accent,
                focusedIndicatorColor = theme.accent.copy(alpha = 0.6f),
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )

        // --------------------------------------------- quick paste (live clip)
        quickClip?.let { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(theme.accent.copy(alpha = 0.18f))
                    .clickable { controller.pasteText(q) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = stringResource(R.string.clipboard_paste_now),
                    tint = theme.accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = " " + stringResource(R.string.clipboard_paste_now) + ": " + q.take(60),
                    color = theme.keyText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ------------------------------------------------------------- body
        when {
            !captureOn -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.clipboard_capture_off),
                        color = theme.keyText.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            clips.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.clipboard_empty),
                        color = theme.keyText.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
            else -> {
                val pinned = clips.filter { it.pinned && matches(it) }
                val recent = clips.filter { !it.pinned && matches(it) }
                if (pinned.isEmpty() && recent.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.clipboard_no_match),
                            color = theme.keyText.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // Resolved in composable scope — LazyListScope is not @Composable.
                    val pinnedLabel = stringResource(R.string.clipboard_pinned)
                    val recentLabel = stringResource(R.string.clipboard_recent)
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sections collapse into one flat list while searching;
                        // the Recent label only appears when pins are shown too.
                        val sections = when {
                            search.isNotBlank() -> listOf("" to (pinned + recent))
                            pinned.isNotEmpty() -> listOf(
                                pinnedLabel to pinned,
                                recentLabel to recent
                            )
                            else -> listOf("" to recent)
                        }
                        sections.forEach { (label, list) ->
                            if (list.isEmpty()) return@forEach
                            if (label.isNotEmpty()) {
                                item(key = "header_$label") {
                                    Text(
                                        text = label,
                                        color = theme.accent.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                                    )
                                }
                            }
                            items(list, key = { it.id }) { clip ->
                                ClipRow(
                                    controller = controller,
                                    clip = clip,
                                    preview = previews[clip.id].orEmpty()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipRow(
    controller: OnyxKeyboardController,
    clip: ClipEntity,
    preview: String,
) {
    val theme by controller.theme.collectAsState()
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
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Copy again",
            tint = theme.keyText.copy(alpha = 0.5f),
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable { controller.copyClip(clip) }
                .padding(2.dp)
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
