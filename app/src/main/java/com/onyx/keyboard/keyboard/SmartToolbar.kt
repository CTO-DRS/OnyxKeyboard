package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/**
 * The strip above the keys: language chip + live suggestions + tool dock
 * (emoji / translate / clipboard / GIF / mic / quick settings).
 */
@Composable
fun SmartToolbar(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val suggestions by controller.suggestions.collectAsState()
    val lang by controller.lang.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Language chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(theme.accent.copy(alpha = 0.16f))
                .clickable { controller.switchLanguage() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (lang == Lang.AR) stringResource(R.string.kb_lang_ar)
                else stringResource(R.string.kb_lang_en),
                color = theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Suggestion chips (flexible middle)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (suggestions.isEmpty()) {
                Text(
                    text = "ONYX",
                    color = theme.accent.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            } else {
                suggestions.forEach { s ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (s.isCorrection) theme.glow.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.07f)
                            )
                            .clickable { controller.commitSuggestion(s) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = s.text,
                            color = if (s.isCorrection) theme.glow else theme.keyText,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Tool dock
        ToolIcon(controller, Icons.Filled.EmojiEmotions, stringResource(R.string.panel_emoji)) {
            controller.setPage(KeyboardPage.EMOJI)
        }
        ToolIcon(controller, Icons.Filled.Translate, stringResource(R.string.panel_translate)) {
            controller.setPage(KeyboardPage.TRANSLATE)
        }
        ToolIcon(controller, Icons.Filled.ContentPaste, stringResource(R.string.panel_clipboard)) {
            controller.setPage(KeyboardPage.CLIPBOARD)
        }
        ToolIcon(controller, Icons.Filled.Gif, stringResource(R.string.panel_gif)) {
            controller.setPage(KeyboardPage.GIF)
        }
        ToolIcon(controller, Icons.Filled.Mic, stringResource(R.string.panel_voice)) {
            controller.setPage(KeyboardPage.VOICE)
        }
        ToolIcon(controller, Icons.Filled.Tune, stringResource(R.string.panel_settings)) {
            controller.setPage(KeyboardPage.SETTINGS)
        }
    }
}

@Composable
private fun ToolIcon(
    controller: OnyxKeyboardController,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val theme by controller.theme.collectAsState()
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = theme.accent.copy(alpha = 0.9f),
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(5.dp)
    )
}
