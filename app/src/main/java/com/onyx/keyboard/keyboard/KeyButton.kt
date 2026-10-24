package com.onyx.keyboard.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single neon-glass key. Pressed state triggers: scale-down, glow bloom,
 * gradient border intensification — plus the controller's haptic/sound path.
 */
@Composable
fun RowScope.KeyButton(
    label: String,
    accent: Color,
    glow: Color,
    keyFill: Color,
    keyText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    height: Dp = 52.dp,
    fontSize: Int = 18,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "keyScale")
    val glowAlpha by animateFloatAsState(if (pressed) 1f else 0.35f, label = "keyGlow")

    Box(
        modifier
            .weight(weight)
            .padding(horizontal = 2.5.dp, vertical = 3.dp)
            .height(height)
            .drawBehind {
                // Outer neon bloom, stronger while pressed.
                val a = glowAlpha * (if (pressed) 0.55f else 0.18f)
                if (a > 0.01f) {
                    drawRoundRect(
                        color = glow.copy(alpha = a),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 16.dp.toPx() * 0.9f)
                    )
                }
            }
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        keyFill.copy(alpha = if (pressed) 0.98f else 0.88f),
                        keyFill.copy(alpha = 0.72f)
                    )
                )
            )
            .border(
                width = if (pressed) 1.6.dp else 0.9.dp,
                brush = Brush.linearGradient(
                    listOf(accent.copy(alpha = glowAlpha), glow.copy(alpha = glowAlpha * 0.6f))
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = keyText,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            maxLines = 1
        )
    }
}

/** Icon variant of the neon key (shift, backspace, enter, mode keys…). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.KeyIcon(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    glow: Color,
    keyFill: Color,
    keyText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    height: Dp = 52.dp,
    iconSize: Dp = 20.dp,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "iconScale")
    val glowAlpha by animateFloatAsState(if (pressed) 1f else 0.35f, label = "iconGlow")

    Box(
        modifier
            .weight(weight)
            .padding(horizontal = 2.5.dp, vertical = 3.dp)
            .height(height)
            .drawBehind {
                val a = glowAlpha * (if (pressed) 0.55f else 0.18f)
                if (a > 0.01f) {
                    drawRoundRect(
                        color = glow.copy(alpha = a),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 16.dp.toPx() * 0.9f)
                    )
                }
            }
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        keyFill.copy(alpha = if (pressed) 0.98f else 0.88f),
                        keyFill.copy(alpha = 0.72f)
                    )
                )
            )
            .border(
                width = if (pressed) 1.6.dp else 0.9.dp,
                brush = Brush.linearGradient(
                    listOf(accent.copy(alpha = glowAlpha), glow.copy(alpha = glowAlpha * 0.6f))
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = keyText,
            modifier = Modifier
                .fillMaxSize()
                .padding(iconSize / 2)
        )
    }
}
