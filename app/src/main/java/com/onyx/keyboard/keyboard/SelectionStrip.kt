package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R

/**
 * وضع تحديد النص — the SELECTION strip, rendered in the toolbar's place
 * (same 46dp chrome, established in-place-replacement pattern) while the
 * mode is armed. Entering: the small select key in the bottom cursor
 * cluster; leaving: the ✕ chip, a character key, a field change, or
 * backspace (which also deletes the highlighted span whole).
 *
 * Chips: ✕ · تحديد الكل · نسخ · قص · لصق · then the two EXTEND arrows that
 * grow/shrink the span around the pinned anchor (SelectionMath).
 */
@Composable
fun SelectionStrip(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Exit — collapses the mode, text untouched.
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable { controller.exitSelectionMode() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.selection_exit),
                tint = theme.accent.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }

        SelectionChip(controller, stringResource(R.string.selection_all), weight = 1.6f) {
            controller.selectionSelectAll()
        }
        SelectionChip(controller, stringResource(R.string.selection_copy), weight = 1f) {
            controller.selectionCopy()
        }
        SelectionChip(controller, stringResource(R.string.selection_cut), weight = 1f) {
            controller.selectionCut()
        }
        SelectionChip(controller, stringResource(R.string.selection_paste), weight = 1f) {
            controller.selectionPaste()
        }

        Spacer(Modifier.weight(0.4f))

        // Extend arrows — move the focus edge around the pinned anchor.
        SelectionArrow(controller, Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
            controller.selectionExtend(-1)
        }
        SelectionArrow(controller, Icons.AutoMirrored.Filled.KeyboardArrowRight) {
            controller.selectionExtend(1)
        }
    }
}

@Composable
private fun RowScope.SelectionChip(
    controller: OnyxKeyboardController,
    label: String,
    weight: Float,
    onClick: () -> Unit,
) {
    val theme by controller.theme.collectAsState()
    Box(
        modifier = Modifier
            .weight(weight)
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = theme.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SelectionArrow(
    controller: OnyxKeyboardController,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val theme by controller.theme.collectAsState()
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.keyText.copy(alpha = 0.9f),
            modifier = Modifier.size(18.dp)
        )
    }
}
