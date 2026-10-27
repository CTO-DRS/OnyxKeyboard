package com.onyx.keyboard.keyboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.onyx.keyboard.data.db.ThemeEntity
import kotlinx.coroutines.delay

/**
 * CUSTOM THEME EDITOR — lets the user build their own look from scratch.
 *
 * The panel replaces the whole keyboard page, so it ships its own char strip
 * (Arabic abjad + Latin + digits + space/backspace) that names the theme.
 * Every keystroke is captured by the controller's internal buffer (never
 * committed to the app — see [OnyxKeyboardController.internalCapture]).
 *
 * Seven color roles (two surfaces, key fill, key text, accent, glow,
 * particles) are edited with curated swatch strips, a live mini-keyboard
 * preview renders every change instantly, and the dice chip rolls a whole
 * coherent palette in one tap. Saving persists to Room as a builtin=false
 * row, applies it immediately, and lands back in the gallery.
 */
@Composable
fun ThemeEditorPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val draft by controller.editingTheme.collectAsState()
    val name by controller.internalCapture.collectAsState()
    val d = draft ?: return
    val editingExisting = d.id != 0L && !d.builtin

    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3000)
            confirmDelete = false
        }
    }

    // Slider's local position — keyed on the DRAFT identity (not the value,
    // so dragging never fights the recompositions it itself triggers).
    var radius by remember(d.id) {
        mutableFloatStateOf(d.keyRadius.coerceIn(0, 26).toFloat())
    }

    val title = stringResource(R.string.panel_theme_editor)
    val nameHint = stringResource(R.string.editor_name_hint)
    val saveLabel = stringResource(R.string.editor_save)
    val cancelLabel = stringResource(R.string.editor_cancel)
    val deleteLabel = stringResource(R.string.editor_delete)
    val deleteConfirmLabel = stringResource(R.string.editor_delete_confirm)
    val shuffleLabel = stringResource(R.string.editor_shuffle)
    val shareLabel = stringResource(R.string.editor_share)
    val bgLabel = stringResource(R.string.editor_bg_image)
    val pickLabel = stringResource(R.string.editor_pick_image)
    val removeLabel = stringResource(R.string.editor_remove_image)
    val radiusLabel = stringResource(R.string.editor_key_radius)
    val spaceStyleLabel = stringResource(R.string.editor_space_style)
    val iconStyle = stringResource(R.string.space_style_icon)
    val textStyle = stringResource(R.string.space_style_text)
    val hiddenStyle = stringResource(R.string.space_style_hidden)
    val roles = listOf(
        EditorColorRole(
            stringResource(R.string.editor_role_surface),
            { it.surfaceTop }, { t, c -> t.copy(surfaceTop = c) }
        ),
        EditorColorRole(
            stringResource(R.string.editor_role_surface_bottom),
            { it.surfaceBottom }, { t, c -> t.copy(surfaceBottom = c) }
        ),
        EditorColorRole(
            stringResource(R.string.editor_role_keys),
            { it.keyFill }, { t, c -> t.copy(keyFill = c) }
        ),
        EditorColorRole(
            stringResource(R.string.editor_role_text),
            { it.keyText }, { t, c -> t.copy(keyText = c) }
        ),
        EditorColorRole(
            stringResource(R.string.editor_role_accent),
            { it.accent }, { t, c -> t.copy(accent = c) }
        ),
        EditorColorRole(
            stringResource(R.string.editor_role_glow),
            { it.glow }, { t, c -> t.copy(glow = c) }
        ),
        EditorColorRole(
            stringResource(R.string.editor_role_particles),
            { it.particle }, { t, c -> t.copy(particle = c) }
        ),
    )

    Column(Modifier.fillMaxWidth().height(286.dp)) {
        // ---------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = cancelLabel,
                tint = theme.keyText.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { controller.closeThemeEditor() }
                    .padding(4.dp)
                    .size(18.dp)
            )
            Text(
                text = title,
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            )
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = shareLabel,
                tint = theme.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { controller.exportEditingTheme() }
                    .padding(4.dp)
                    .size(17.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.accent)
                    .clickable { controller.saveEditingTheme() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = saveLabel,
                    tint = Color.Black,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = saveLabel,
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
        }

        // ------------------------------------------------- scrollable body
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            // Name — typed with the char strip below, echoed here live.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = nameHint,
                    color = theme.keyText.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name.orEmpty(),
                    color = theme.keyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                BlinkingCursor(theme.accent)
            }

            Spacer(Modifier.height(6.dp))

            // Live mini-keyboard preview in the draft's own palette.
            EditorPreview(d = d)

            Spacer(Modifier.height(6.dp))

            roles.forEach { role ->
                RoleRow(
                    theme = theme,
                    label = role.label,
                    current = role.get(d),
                    onPick = { c -> controller.updateEditingTheme { role.set(it, c) } }
                )
                Spacer(Modifier.height(4.dp))
            }

            // ------------------------------------------------- shape section
            // Corner radius — slides from sharp squares to full pills and
            // reshapes the live preview (and, after saving, every key).
            ShapeSliderRow(
                theme = theme,
                label = radiusLabel,
                value = radius,
                onValueChange = { v ->
                    radius = v
                    controller.updateEditingTheme {
                        it.copy(keyRadius = v.toInt().coerceIn(0, 26))
                    }
                }
            )
            Spacer(Modifier.height(2.dp))

            // Space-bar face — cycles icon → the word → silent blank bar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spaceStyleLabel,
                    color = theme.keyText.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.width(78.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable {
                            controller.updateEditingTheme {
                                it.copy(spaceLabel = (it.spaceLabel + 1) % 3)
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SpaceBar,
                        contentDescription = spaceStyleLabel,
                        tint = theme.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = when (d.spaceLabel) {
                            1 -> textStyle
                            2 -> hiddenStyle
                            else -> iconStyle
                        },
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Optional background photo — picked through the transparent
            // trampoline; the file never leaves the device on export.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bgLabel,
                    color = theme.keyText.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.width(78.dp)
                )
                Text(
                    text = pickLabel,
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { controller.requestBackgroundImage() }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
                if (d.bgUri.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable { controller.removeBackgroundImage() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = removeLabel,
                            tint = theme.glow,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = removeLabel,
                            color = theme.glow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        // ------------------------------------------------------ char strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "bs") {
                StripChip(onClick = { controller.onBackspace() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = theme.keyText,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            items(EDITOR_ARABIC, key = { "ar_$it" }) { c -> CharChip(controller, theme.accent, c) }
            items(EDITOR_LATIN, key = { "en_$it" }) { c -> CharChip(controller, theme.accent, c) }
            items(EDITOR_DIGITS, key = { "d_$it" }) { c -> CharChip(controller, theme.accent, c) }
            item(key = "space") {
                StripChip(onClick = { controller.onSpace() }) {
                    Icon(
                        imageVector = Icons.Filled.SpaceBar,
                        contentDescription = "Space",
                        tint = theme.keyText,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // ------------------------------------------------------ action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorAction(
                icon = Icons.Filled.Casino,
                label = shuffleLabel,
                color = theme.keyText.copy(alpha = 0.85f),
                onClick = { confirmDelete = false; controller.shuffleEditingTheme() }
            )
            Spacer(Modifier.width(8.dp))
            EditorAction(
                icon = Icons.Filled.Close,
                label = cancelLabel,
                color = theme.keyText.copy(alpha = 0.85f),
                onClick = { controller.closeThemeEditor() }
            )
            if (editingExisting) {
                Spacer(Modifier.weight(1f))
                if (confirmDelete) {
                    Text(
                        text = deleteConfirmLabel,
                        color = theme.glow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                confirmDelete = false
                                controller.deleteEditingThemeNow()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                } else {
                    EditorAction(
                        icon = Icons.Filled.Delete,
                        label = deleteLabel,
                        color = theme.glow,
                        onClick = { confirmDelete = true }
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------- bits

/** One editable color role — pure accessors over [ThemeEntity]. */
internal data class EditorColorRole(
    val label: String,
    val get: (ThemeEntity) -> Long,
    val set: (ThemeEntity, Long) -> ThemeEntity,
)

/**
 * The key-corner-radius slider row. Continuous 0..26dp: 0 squares, 14 the
 * classic neon glass, 26 full pills — the live preview below tracks it.
 */
@Composable
private fun ShapeSliderRow(
    theme: OnyxColors,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = theme.keyText.copy(alpha = 0.7f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(78.dp)
        )
        Slider(
            value = value.coerceIn(0f, 26f),
            onValueChange = onValueChange,
            valueRange = 0f..26f,
            colors = SliderDefaults.colors(
                thumbColor = theme.accent,
                activeTrackColor = theme.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f),
            ),
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        )
        Text(
            text = value.toInt().coerceIn(0, 26).toString(),
            color = theme.keyText.copy(alpha = 0.6f),
            fontSize = 9.sp,
            modifier = Modifier.width(20.dp)
        )
    }
}

/** Curated swatch pool: neutrals for surfaces/text + vivid picks for accents. */
internal val EDITOR_SWATCHES: List<Long> = listOf(
    0xFFFFFFFFL, 0xFFF2EDFFL, 0xFF9AA0A6L, 0xFF5A6068L,
    0xFF2A2D34L, 0xFF121317L, 0xFF000000L, 0xFFB37BFFL,
    0xFF3EE6FFL, 0xFF3DFFA8L, 0xFFFF5CA8L, 0xFFFFB300L,
    0xFFFF6B4AL, 0xFF4A7DFFL,
)

private val EDITOR_ARABIC = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي".map { it.toString() }
private val EDITOR_LATIN = ('A'..'Z').map { it.toString() }
private val EDITOR_DIGITS = ('0'..'9').map { it.toString() }

@Composable
private fun RoleRow(
    theme: OnyxColors,
    label: String,
    current: Long,
    onPick: (Long) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = theme.keyText.copy(alpha = 0.7f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(78.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(EDITOR_SWATCHES, key = { it }) { c ->
                val selected = c == current
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (selected) 2.dp else 0.8.dp,
                            color = if (selected) theme.accent
                            else theme.keyText.copy(alpha = 0.22f),
                            shape = CircleShape
                        )
                        .clickable { onPick(c) }
                )
            }
        }
    }
}

@Composable
private fun EditorPreview(d: ThemeEntity) {
    // Proportional radius: the preview keys are ~14dp tall vs the real 50dp,
    // so the shape reads faithfully (0 squares → 26 full pills).
    val miniRadius = (d.keyRadius.coerceIn(0, 26) * 0.3f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(d.surfaceTop), Color(d.surfaceBottom))
                )
            )
            .border(
                width = 0.8.dp,
                color = Color(d.keyText).copy(alpha = 0.16f),
                shape = RoundedCornerShape(11.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) {
                PreviewKey(
                    fill = Color(d.keyFill).copy(alpha = 0.85f),
                    radius = miniRadius,
                    modifier = Modifier.weight(1f).height(14.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        PreviewKey(
            fill = Color(d.accent).copy(alpha = 0.9f),
            radius = miniRadius,
            modifier = Modifier.fillMaxWidth().height(7.dp)
        )
    }
}

@Composable
private fun PreviewKey(fill: Color, radius: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(fill)
    )
}

/** A letter/digit chip of the editor's own typing strip. */
@Composable
private fun CharChip(controller: OnyxKeyboardController, accent: Color, c: String) {
    Box(
        modifier = Modifier
            .size(width = 27.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { controller.onChar(c) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = c,
            color = accent.copy(alpha = 0.95f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Icon-only chip (backspace / space) of the typing strip. */
@Composable
private fun StripChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun EditorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** A caret that blinks forever — signals "the strip is typing the name". */
@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "caretAlpha"
    )
    Box(
        Modifier
            .size(width = 2.dp, height = 15.dp)
            .background(color.copy(alpha = alpha))
    )
}
