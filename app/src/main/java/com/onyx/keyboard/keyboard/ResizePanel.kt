package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R

/**
 * مساحة اللوحة — the RESIZE panel behind the toolbar's area button.
 *
 * The slider drags the global key-height scale (80%..130%) with a LIVE
 * preview: the inert mini keyboard at the bottom renders through the very
 * same [LocalOnyxKeyScale] the real keys consume, so what the user sees
 * while dragging is exactly what every page will look like when they
 * return to typing. The value snaps to a 5% grid, persists through
 * [OnyxKeyboardController.commitKeyboardScale] when the finger lifts, and
 * the preset chips (صغير/عادي/كبير/ضخم) land one-tap values.
 */
@Composable
fun ResizePanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val scale by controller.keyboardScale.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        PanelHeader(controller, stringResource(R.string.resize_title))

        Text(
            text = stringResource(R.string.resize_percent, KeyboardScale.percent(scale)),
            color = theme.accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Slider(
            value = scale,
            onValueChange = { controller.previewKeyboardScale(it) },
            onValueChangeFinished = { controller.commitKeyboardScale() },
            valueRange = KeyboardScale.MIN..KeyboardScale.MAX,
            colors = SliderDefaults.colors(
                thumbColor = theme.accent,
                activeTrackColor = theme.accent,
                inactiveTrackColor = theme.keyFill.copy(alpha = 0.65f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                R.string.resize_small to 0.85f,
                R.string.resize_normal to KeyboardScale.DEFAULT,
                R.string.resize_large to 1.15f,
                R.string.resize_huge to KeyboardScale.MAX,
            ).forEach { (label, value) ->
                val selected = KeyboardScale.percent(scale) == KeyboardScale.percent(value)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) theme.accent.copy(alpha = 0.4f)
                            else theme.keyFill.copy(alpha = 0.6f)
                        )
                        .clickable { controller.setKeyboardScale(value) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(label),
                        color = if (selected) Color.White else theme.keyText.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.resize_hint),
            color = theme.keyText.copy(alpha = 0.55f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
        )

        Text(
            text = stringResource(R.string.resize_preview),
            color = theme.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        // LIVE preview — inert keys rendering through the real scale.
        Column(Modifier.fillMaxWidth()) {
            listOf(
                listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه"),
                listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن"),
            ).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { ch ->
                        KeyButton(
                            label = ch,
                            accent = theme.accent,
                            glow = theme.glow,
                            keyFill = theme.keyFill,
                            keyText = theme.keyText,
                            onClick = {},
                            height = 34.dp,
                            fontSize = 14
                        )
                    }
                }
            }
        }
    }
}
