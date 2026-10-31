package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R
import com.onyx.keyboard.data.db.FontEntity

/**
 * F10 (PRD) — لوحة الخطوط: استيراد ملفات TTF/OTF من التخزين (عبر النشاط
 * الشفاف مع تحذير حقوق النسخ)، ومعاينة حية، والتطبيق على كل حروف اللوحة،
 * والحذف. النمط نفسه الذي تستخدمه لوحة الثيمات ومساحة اللوحة.
 */
@Composable
fun FontsPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val fonts by controller.fonts.collectAsState()
    val selectedId by controller.selectedFontId.collectAsState()
    val note by controller.fontsNote.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .height(286.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        PanelHeader(controller, stringResource(R.string.panel_fonts))

        // Import row — launches the transparent picker + copyright warning.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.accent.copy(alpha = 0.14f))
                .clickable { controller.requestFontImport() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.fonts_import),
                tint = theme.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.fonts_import),
                color = theme.keyText.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Text(
                text = "TTF · OTF",
                color = theme.accent.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }

        // Transient note (imported / failed / full).
        note?.let { resId ->
            Text(
                text = stringResource(resId),
                color = theme.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        // The default face row — always first so the user can undo a pick.
        FontRow(
            controller = controller,
            label = stringResource(R.string.fonts_default),
            previewFont = null,
            selected = selectedId == 0L,
            font = null,
        )

        fonts.forEach { font ->
            FontRow(
                controller = controller,
                label = font.name,
                previewFont = rememberFontFamily(font.path),
                selected = selectedId == font.id,
                font = font,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Loads a private-storage TTF/OTF into a Compose [FontFamily] for the row
 * previews (one decode per path, off the UI thread). Null when the file is
 * gone or undecodable — the preview then falls back to the default face.
 */
@Composable
private fun rememberFontFamily(path: String): FontFamily? {
    val family by produceState<FontFamily?>(initialValue = null, key1 = path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = java.io.File(path)
                if (!f.exists() || f.length() == 0L) null
                else FontFamily(android.graphics.Typeface.createFromFile(f))
            }.getOrNull()
        }
    }
    return family
}

@Composable
private fun FontRow(
    controller: OnyxKeyboardController,
    label: String,
    previewFont: FontFamily?,
    selected: Boolean,
    font: FontEntity?,
) {
    val theme by controller.theme.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) theme.accent.copy(alpha = 0.20f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = if (selected) 1.2.dp else 0.dp,
                color = theme.accent.copy(alpha = if (selected) 0.7f else 0f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                controller.selectFontNow(font?.id ?: 0L)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(theme.accent.copy(alpha = if (selected) 0.5f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "أ",
                color = if (selected) Color.White else theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = previewFont
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = label,
                color = theme.keyText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = previewFont,
                maxLines = 1
            )
            Text(
                text = if (selected) stringResource(R.string.fonts_active)
                else stringResource(R.string.fonts_preview),
                color = theme.keyText.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontFamily = previewFont
            )
        }
        // Delete is only offered for imported faces — the default row can't
        // be removed, matching the themes panel's builtin protection.
        if (font != null) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = theme.keyText.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { controller.deleteFontNow(font) }
                    .padding(2.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.TextFields,
                contentDescription = null,
                tint = theme.keyText.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
