package com.onyx.keyboard.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Per-theme key corner radius (dp) — provided by the IME surface from the
 * active ThemeEntity so the editor's radius slider reshapes the WHOLE
 * keyboard (letters, symbols, numpad, space bar) live. Every key falls
 * back to the classic 14dp neon-glass look when nothing provides it.
 */
val LocalOnyxKeyRadius = staticCompositionLocalOf { 14 }

/**
 * مساحة اللوحة — the global key-height scale (0.80..1.30) provided by the
 * IME surface from the resize panel. Every key multiplies its designed
 * height (and its label) by this factor, so the slider reshapes the WHOLE
 * keyboard live — letters, symbols, numpad, space bar — while the toolbar
 * and strips keep their fixed 46dp chrome.
 */
val LocalOnyxKeyScale = staticCompositionLocalOf { 1f }

/**
 * F10 — the user's imported TTF/OTF face, provided by the IME surface from
 * the fonts panel selection. Every text that types (key labels, suggestions,
 * space bar) renders in it; null = Onyx's default face.
 */
val LocalOnyxFont = staticCompositionLocalOf<android.graphics.Typeface?> { null }

/**
 * A single neon-glass key. Pressed state triggers: scale-down, glow bloom,
 * gradient border intensification — plus the controller's haptic/sound path.
 * Optional [onLongClick] powers long-press alternative characters.
 *
 * [onGeometry] reports the key's unscaled bounds (window space) on every
 * layout pass — the glide-typing gesture layer uses it to hit-test which
 * letter the finger is currently over. It is placed BEFORE the scale
 * modifier so the press animation never skews the reported geometry.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    radius: Dp = LocalOnyxKeyRadius.current.dp,
    onLongClick: (() -> Unit)? = null,
    onGeometry: ((Rect) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "keyScale")
    val glowAlpha by animateFloatAsState(if (pressed) 1f else 0.35f, label = "keyGlow")
    val keyScale = LocalOnyxKeyScale.current

    Box(
        modifier
            .weight(weight)
            .padding(horizontal = 2.5.dp, vertical = 3.dp)
            .height(height * keyScale)
            .onGloballyPositioned { coords -> onGeometry?.invoke(coords.boundsInWindow()) }
            .drawBehind {
                // Outer neon bloom, stronger while pressed.
                val a = glowAlpha * (if (pressed) 0.55f else 0.18f)
                if (a > 0.01f) {
                    drawRoundRect(
                        color = glow.copy(alpha = a),
                        cornerRadius = CornerRadius(
                            (radius + 2.dp).toPx(), (radius + 2.dp).toPx()
                        ),
                        style = Stroke(width = 16.dp.toPx() * 0.9f)
                    )
                }
            }
            .scale(scale)
            .clip(RoundedCornerShape(radius))
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
                shape = RoundedCornerShape(radius)
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = keyText,
            fontSize = (fontSize * keyScale).sp,
            fontWeight = fontWeight,
            fontFamily = LocalOnyxFont.current?.let { FontFamily(it) },
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
    radius: Dp = LocalOnyxKeyRadius.current.dp,
    onLongClick: (() -> Unit)? = null,
    /**
     * If set, this action auto-repeats while the key is held down
     * (after an initial delay so a normal tap fires exactly once).
     */
    holdRepeat: (() -> Unit)? = null,
    /**
     * If set, a HORIZONTAL swipe across the key (past a fixed threshold)
     * fires this action once — the backspace's «سحب = محو الكلمة كاملة»
     * gesture. The drag consumes the pointer stream, which also cancels the
     * press state — so hold-repeat can never double-fire behind the swipe.
     */
    onSwipe: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "iconScale")
    val glowAlpha by animateFloatAsState(if (pressed) 1f else 0.35f, label = "iconGlow")
    val keyScale = LocalOnyxKeyScale.current

    LaunchedEffect(pressed) {
        val repeat = holdRepeat ?: return@LaunchedEffect
        if (!pressed) return@LaunchedEffect
        delay(450) // longer than the long-press timeout to not double-fire word actions
        while (true) {
            repeat()
            delay(45)
        }
    }

    Box(
        modifier
            .weight(weight)
            .padding(horizontal = 2.5.dp, vertical = 3.dp)
            .height(height * keyScale)
            .then(
                if (onSwipe != null) {
                    Modifier.pointerInput(Unit) {
                        var total = 0f
                        val threshold = 64.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (SelectionMath.swipeDeletesWord(total, threshold)) {
                                    onSwipe?.invoke()
                                }
                            }
                        ) { change, amount ->
                            change.consume()
                            total += amount
                        }
                    }
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val a = glowAlpha * (if (pressed) 0.55f else 0.18f)
                if (a > 0.01f) {
                    drawRoundRect(
                        color = glow.copy(alpha = a),
                        cornerRadius = CornerRadius(
                            (radius + 2.dp).toPx(), (radius + 2.dp).toPx()
                        ),
                        style = Stroke(width = 16.dp.toPx() * 0.9f)
                    )
                }
            }
            .scale(scale)
            .clip(RoundedCornerShape(radius))
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
                shape = RoundedCornerShape(radius)
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
                .padding((iconSize * keyScale) / 2)
        )
    }
}
