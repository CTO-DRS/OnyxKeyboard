package com.onyx.keyboard.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R
import com.onyx.keyboard.intelligence.ArabicLexicon
import com.onyx.keyboard.intelligence.EnglishLexicon
import com.onyx.keyboard.ui.components.ParticleField
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The full Onyx IME surface: particle background, smart toolbar,
 * and animated page switching between letters / symbols / panels.
 */
@Composable
fun OnyxKeyboardScreen(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(theme.surfaceTop, theme.surfaceBottom))
            )
    ) {
        // Every neon key inherits the active theme's corner radius, so the
        // editor's shape slider reshapes the whole keyboard live. The scale
        // provider carries the resize panel's global key-height factor
        // (مساحة اللوحة) the same way — and the font provider carries the
        // imported TTF/OTF face (F10) so every typed glyph renders in it.
        CompositionLocalProvider(
            LocalOnyxKeyRadius provides theme.keyRadius,
            LocalOnyxKeyScale provides controller.keyboardScale.collectAsState().value,
            LocalOnyxFont provides controller.activeFont.collectAsState().value,
        ) {
        val oneHand by controller.oneHand.collectAsState()
        // Custom background photo (user themes) — decoded once per path on a
        // background dispatcher, downscaled to a sane pixel budget. A gradient
        // scrim on top keeps the palette (and every key label) readable — the
        // photo is atmosphere, never contrast.
        val bgPath = theme.bgUri
        if (bgPath.isNotEmpty()) {
            val bgBitmap by produceState<android.graphics.Bitmap?>(null, bgPath) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(bgPath, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
                        BitmapFactory.decodeFile(
                            bgPath,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                    }.getOrNull()
                }
            }
            bgBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    theme.surfaceTop.copy(alpha = 0.72f),
                                    theme.surfaceBottom.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
            }
        }
        if (controller.particles.collectAsState().value &&
            !controller.powerSave.collectAsState().value
        ) {
            // Battery saver pauses the particle field (continuous repaints are
            // the single most expensive thing on this surface).
            ParticleField(
                // CRITICAL FIX (keyboard floating at top of screen):
                // fillMaxSize() made this Canvas fill the entire IME window's
                // max constraints, so the Box (and therefore the IME window)
                // measured to FULL SCREEN height — keys rendered at the top
                // with a huge empty particle area below. matchParentSize()
                // sizes the overlay to the keyboard content WITHOUT being
                // measured by the Box, so the window wraps the keys and the
                // IME docks at the bottom as expected.
                modifier = Modifier.matchParentSize(),
                color = theme.particle,
                count = 22
            )
        }

        if (oneHand == OneHand.OFF) {
            KeyboardBody(controller, shrink = 1f)
        } else {
            // ONE-HANDED: the content column shrinks to a fraction of the
            // width and docks to the chosen edge; the free side stays part of
            // the themed surface. A slim grip rides the inner edge with two
            // controls: flip dock side, back to full width.
            // CRITICAL FIX (keyboard stretched to full screen height, part 3):
            // OneHandGrip uses fillMaxHeight() — inside a plain wrap-content
            // Row that resolves to the window's MAX height (the whole screen),
            // inflating the IME window exactly like the cursor-cluster bug.
            // IntrinsicSize.Min binds the Row (and therefore the grip) to the
            // keyboard's own natural height, whatever page is open.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (oneHand == OneHand.RIGHT) {
                    Box(Modifier.weight(1f))
                    KeyboardBody(controller, shrink = ONE_HAND_FRACTION)
                } else {
                    KeyboardBody(controller, shrink = ONE_HAND_FRACTION)
                }
                OneHandGrip(controller, side = oneHand)
            }
        }
        }
    }
}

/** Width fraction of the keyboard content in one-handed mode. */
internal const val ONE_HAND_FRACTION = 0.84f

/**
 * The keyboard content (toolbar strip + animated pages). [shrink] caps the
 * width fraction — 1f in normal mode, ONE_HAND_FRACTION in one-handed mode.
 */
@Composable
private fun KeyboardBody(controller: OnyxKeyboardController, shrink: Float) {
    val altReq by controller.altPopup.collectAsState()
    val glideCands by controller.glideCandidates.collectAsState()
    val glideChain by controller.glideChain.collectAsState()
    val suggestions by controller.suggestions.collectAsState()
    val buffer by controller.buffer.collectAsState()
    val page by controller.page.collectAsState()
    val selecting by controller.selectionMode.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(shrink),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (altReq != null) {
            // Long-press alternatives replace the toolbar IN-PLACE (same
            // height) so the keyboard never resizes mid-gesture.
            AlternativesStrip(
                req = altReq!!,
                controller = controller,
                onPick = { controller.onAltChar(it) },
                onDismiss = { controller.closeAltPopup() }
            )
        } else if (glideChain.isNotEmpty()) {
            // MID-GLIDE letter chain — the live glowing trace of the keys
            // the finger has crossed, replacing the toolbar in place so
            // the keyboard never resizes during the gesture.
            GlideChainStrip(chain = glideChain, controller = controller)
        } else if (glideCands.isNotEmpty()) {
            // Post-glide word alternatives — first entry (the committed
            // word) is highlighted; tapping another swaps it in place.
            GlideStrip(candidates = glideCands, controller = controller)
        } else if (selecting) {
            // TEXT SELECTION MODE — select-all / copy / cut / paste / extend
            // arrows replace the toolbar in place (same 46dp chrome).
            SelectionStrip(controller)
        } else if (suggestions.isNotEmpty() && buffer.isNotEmpty()) {
            // Actively composing a word with suggestions available — the
            // full-width suggestion strip takes the toolbar's place.
            SuggestionBar(controller)
        } else {
            // Idle / predicting mode: language chip, compact prediction
            // chips (next-word) and the tool dock share the row.
            SmartToolbar(controller)
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)))
                    .togetherWith(slideOutVertically(tween(180)) { it / 8 } + fadeOut(tween(120)))
            },
            label = "kbPage"
        ) { p ->
            when (p) {
                KeyboardPage.LETTERS -> LettersPage(controller)
                KeyboardPage.SYMBOLS -> SymbolsPage(controller)
                KeyboardPage.NUMPAD -> NumpadPage(controller)
                KeyboardPage.EMOJI -> EmojiPanel(controller)
                KeyboardPage.CLIPBOARD -> ClipboardPanel(controller)
                KeyboardPage.TRANSLATE -> TranslatePanel(controller)
                KeyboardPage.GIF -> GifPanel(controller)
                KeyboardPage.VOICE -> VoicePanel(controller)
                KeyboardPage.SETTINGS -> SettingsPanel(controller)
                KeyboardPage.THEMES -> ThemesPanel(controller)
                KeyboardPage.THEME_EDITOR -> ThemeEditorPanel(controller)
                KeyboardPage.THEME_ARCHIVE -> CommunityArchivePanel(controller)
                KeyboardPage.RESIZE -> ResizePanel(controller)
                KeyboardPage.FONTS -> FontsPanel(controller)
            }
        }
    }
}

/**
 * The one-handed grip: a slim control rail on the keyboard's inner edge.
 * Flip = swap dock side (left ⇄ right), Expand = back to full width.
 */
@Composable
private fun OneHandGrip(controller: OnyxKeyboardController, side: OneHand) {
    val theme by controller.theme.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(30.dp)
            .padding(start = 2.dp, end = 2.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GripButton(controller, Icons.Filled.SwapHoriz,
            contentDescription = if (side == OneHand.LEFT) "Move keyboard to right"
                                 else "Move keyboard to left"
        ) { controller.setOneHand(side.flipped) }
        Spacer(Modifier.height(8.dp))
        GripButton(controller, Icons.Filled.OpenInFull,
            contentDescription = "Back to full width"
        ) { controller.setOneHand(OneHand.OFF) }
    }
}

@Composable
private fun GripButton(
    controller: OnyxKeyboardController,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val theme by controller.theme.collectAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.keyFill.copy(alpha = 0.65f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = theme.accent.copy(alpha = 0.9f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ------------------------------------------------------------------ pages

/**
 * Letters page — also hosts the GLIDE (swipe) TYPING gesture layer:
 *
 *  - Every letter key reports its window-space bounds via [KeyButton]’s
 *    onGeometry into [keyBounds] (digits / shift / controls are excluded).
 *  - A pointerInput on the page container tracks the first finger: until
 *    touch-slop it does nothing (taps land on the key normally); beyond
 *    slop it switches to glide mode, consumes subsequent moves (cancelling
 *    the pressed key’s own tap), and records which letters the finger
 *    crosses into a path string, drawing a translucent trail on top.
 *  - On release the path is matched through [GlideMatcher] against the
 *    current language lexicon; the best word is committed and the rest
 *    appear in the candidate strip (GlideStrip).
 *
 * [compact] hides the number row — used when the page renders UNDER the
 * emoji search panel, where every dp of vertical budget is precious.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LettersPage(controller: OnyxKeyboardController, compact: Boolean = false) {
    val theme by controller.theme.collectAsState()
    val lang by controller.lang.collectAsState()
    val shift by controller.shift.collectAsState()
    val showNumbers by controller.numberRow.collectAsState()
    val rows = KeyboardLayouts.letterRows(lang, shift)

    // Glide-typing plumbing (identity-stable across recompositions).
    val keyBounds = remember { mutableMapOf<String, Rect>() }
    val boxCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val trace = remember { mutableStateListOf<Offset>() }

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { boxCoords.value = it }
            .pointerInput(controller) {
                val hitPad = 3.dp.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // While an internal capture owns the keystrokes (emoji
                    // search), a glide would "type" into the search query —
                    // do not engage.
                    if (controller.internalCapture.value != null) return@awaitEachGesture
                    val bc = boxCoords.value ?: return@awaitEachGesture

                    fun hitAt(boxLocal: Offset): String? {
                        val wp = bc.localToWindow(boxLocal)
                        return keyBounds.entries
                            .firstOrNull { it.value.inflate(hitPad).contains(wp) }
                            ?.key
                    }

                    // Only gestures STARTING on a registered letter key can glide.
                    val start = hitAt(down.position) ?: return@awaitEachGesture
                    val path = StringBuilder(start)
                    var lastKey: String = start
                    var last = down.position
                    var total = 0f
                    var gliding = false
                    trace.clear()
                    trace.add(down.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        // A long-press alt popup hands the gesture over — stop tracking.
                        if (controller.altPopup.value != null) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (gliding) change.consume()
                            break
                        }
                        val pos = change.position
                        total += (pos - last).getDistance()
                        last = pos
                        if (!gliding) {
                            if (total > slop) {
                                // The gesture just became a glide — light the
                                // chain strip with everything traced so far.
                                gliding = true
                                controller.pushGlideChain(path.toString())
                            } else continue
                        }
                        if (change.positionChanged()) change.consume()
                        val hit = hitAt(pos)
                        if (hit != null && hit != lastKey) {
                            path.append(hit)
                            lastKey = hit
                            controller.pushGlideChain(path.toString())
                        }
                        if (trace.size < 260) trace.add(pos)
                    }

                    if (gliding) {
                        val word = path.toString()
                        val cands = if (word.length >= 2) {
                            val lexicon = if (lang == Lang.AR) ArabicLexicon.words
                                          else EnglishLexicon.words
                            // LETTER-CHAIN matching: grid-aware distance so a
                            // finger drifting onto an adjacent key still lands
                            // on the intended word.
                            val neighbors = if (lang == Lang.AR) GlideMatcher.AR_NEIGHBORS
                                            else GlideMatcher.EN_NEIGHBORS
                            GlideMatcher.candidates(word, lexicon, neighbors = neighbors)
                        } else {
                            emptyList()
                        }
                        // ALWAYS settle the gesture (chain strip goes away,
                        // candidates show when there are any) — even when the
                        // sweep matched nothing.
                        controller.endGlide(cands)
                    }
                    trace.clear()
                }
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            if (showNumbers && !compact) {
                Row(Modifier.fillMaxWidth()) {
                    KeyboardLayouts.numberRow.forEach { digit ->
                        KeyButton(
                            label = digit,
                            accent = theme.accent,
                            glow = theme.glow,
                            keyFill = theme.keyFill.copy(alpha = 0.62f),
                            keyText = theme.keyText.copy(alpha = 0.9f),
                            onClick = { controller.onChar(digit) },
                            height = 38.dp,
                            fontSize = 15
                        )
                    }
                }
            }
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth()) {
                    if (index == 2) {
                        KeyIcon(
                            icon = when (shift) {
                                ShiftState.OFF -> Icons.Filled.ArrowUpward
                                ShiftState.ON -> Icons.Filled.ArrowUpward
                                ShiftState.CAPS -> Icons.Filled.Lock
                            },
                            contentDescription = "Shift",
                            accent = if (shift != ShiftState.OFF) theme.accent else theme.accent.copy(alpha = 0.6f),
                            glow = theme.glow,
                            keyFill = if (shift != ShiftState.OFF) theme.accent.copy(alpha = 0.35f) else theme.keyFill,
                            keyText = theme.keyText,
                            onClick = { controller.toggleShift() },
                            weight = 1.4f
                        )
                    }
                    row.forEach { key ->
                        // Long-press a letter to reveal accents / hamza variants.
                        // onGeometry feeds the glide hit-test registry.
                        KeyButton(
                            label = key,
                            accent = theme.accent,
                            glow = theme.glow,
                            keyFill = theme.keyFill,
                            keyText = theme.keyText,
                            onClick = { controller.onChar(key) },
                            onLongClick = { controller.openAltPopup(key) },
                            height = 50.dp,
                            onGeometry = { keyBounds[key] = it }
                        )
                    }
                    if (index == 2) {
                        KeyIcon(
                            icon = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            accent = theme.accent,
                            glow = theme.glow,
                            keyFill = theme.keyFill,
                            keyText = theme.keyText,
                            onClick = { controller.onBackspace() },
                            onLongClick = { controller.onBackspaceWord() },
                            holdRepeat = { controller.onBackspace() },
                            onSwipe = { controller.onBackspaceWord() },
                            weight = 1.4f
                        )
                    }
                }
            }
            BottomRow(controller)
        }

        // Translucent finger trail while gliding (draw-phase only invalidation).
        Canvas(Modifier.matchParentSize()) {
            if (trace.size > 1) {
                val w = 5.dp.toPx()
                val p = Path().apply {
                    moveTo(trace[0].x, trace[0].y)
                    for (i in 1 until trace.size) lineTo(trace[i].x, trace[i].y)
                }
                drawPath(
                    p,
                    color = theme.accent.copy(alpha = 0.30f),
                    style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawCircle(theme.accent.copy(alpha = 0.45f), radius = w, center = trace.last())
            }
        }
    }
}

@Composable
private fun SymbolsPage(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    var alt by remember { mutableStateOf(false) }
    val rows = if (alt) KeyboardLayouts.symbolRowsAlt else KeyboardLayouts.symbolRows

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.commitText(key) },
                        height = 50.dp,
                        fontSize = 16
                    )
                }
                if (index == 2) {
                    KeyIcon(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.onBackspace() },
                        onLongClick = { controller.onBackspaceWord() },
                        holdRepeat = { controller.onBackspace() },
                        onSwipe = { controller.onBackspaceWord() },
                        weight = 1.4f,
                        height = 50.dp
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton(
                label = if (alt) "1#" else "#+=",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.accent.copy(alpha = 0.25f),
                keyText = theme.keyText,
                onClick = { alt = !alt },
                weight = 1.5f,
                height = 50.dp,
                fontSize = 14
            )
            KeyButton(
                label = "ABC",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.keyFill,
                keyText = theme.accent,
                onClick = { controller.setPage(KeyboardPage.LETTERS) },
                weight = 1.5f,
                height = 50.dp,
                fontSize = 14
            )
            // Advanced numpad entry (calculator-style digits + math ops).
            KeyButton(
                label = "123+",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.keyFill,
                keyText = theme.accent,
                onClick = { controller.setPage(KeyboardPage.NUMPAD) },
                weight = 1.4f,
                height = 50.dp,
                fontSize = 13
            )
            SpaceWithCursorSlide(controller, weight = 3.4f)
            KeyIcon(
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = "Enter",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.accent.copy(alpha = 0.28f),
                keyText = theme.keyText,
                onClick = { controller.commitEnter() },
                weight = 1.7f,
                height = 50.dp
            )
        }
    }
}

@Composable
private fun BottomRow(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()

    Row(Modifier.fillMaxWidth()) {
        KeyButton(
            label = "?123",
            accent = theme.accent,
            glow = theme.glow,
            keyFill = theme.keyFill,
            keyText = theme.accent,
            onClick = { controller.setPage(KeyboardPage.SYMBOLS) },
            onLongClick = { controller.setPage(KeyboardPage.NUMPAD) },
            weight = 1.4f,
            height = 50.dp
        )
        // Cursor cluster — replaces the old bottom language key (the toolbar
        // language chip still switches AR/EN). Hold to keep scrolling. The
        // middle key arms TEXT SELECTION MODE (the strip swaps in above and
        // its own arrows extend the span).
        // CRITICAL FIX (keyboard stretched to full screen height, part 1):
        // this Row used fillMaxSize(). Inside a weighted Box (whose height
        // constraints are loose 0..screenHeight) fillMaxHeight resolves to the
        // FULL SCREEN, so the bottom row, the letters column and ultimately
        // the whole IME window measured to the display height — the keyboard
        // rendered pinned to the TOP with a huge empty surface below it.
        // fillMaxWidth() lets the Row wrap its 50.dp keys like every other
        // cell in the row.
        Box(modifier = Modifier.weight(1.9f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                KeyIcon(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Cursor left",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText,
                    onClick = { controller.moveCursor(-1) },
                    weight = 1f,
                    height = 50.dp,
                    iconSize = 20.dp,
                    holdRepeat = { controller.moveCursor(-1) }
                )
                val selecting by controller.selectionMode.collectAsState()
                KeyIcon(
                    icon = if (selecting) Icons.Filled.CheckCircle else Icons.Filled.SelectAll,
                    contentDescription = "Text selection",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = if (selecting) theme.accent.copy(alpha = 0.4f) else theme.keyFill,
                    keyText = theme.keyText,
                    onClick = { controller.toggleSelectionMode() },
                    weight = 1f,
                    height = 50.dp,
                    iconSize = 20.dp
                )
                KeyIcon(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Cursor right",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText,
                    onClick = { controller.moveCursor(1) },
                    weight = 1f,
                    height = 50.dp,
                    iconSize = 20.dp,
                    holdRepeat = { controller.moveCursor(1) }
                )
            }
        }
        SpaceWithCursorSlide(
            controller = controller,
            weight = 4.6f,
            onLongClick = { controller.openSpacePopup() }
        )
        KeyIcon(
            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
            contentDescription = "Enter",
            accent = theme.accent,
            glow = theme.glow,
            keyFill = theme.accent.copy(alpha = 0.28f),
            keyText = theme.keyText,
            onClick = { controller.commitEnter() },
            weight = 1.7f,
            height = 50.dp
        )
    }
}

/**
 * Long-press alternatives strip. Replaces the toolbar row at the same
 * height: tap an alternative, or slide horizontally onto one and release.
 */
@Composable
private fun AlternativesStrip(
    req: AltRequest,
    controller: OnyxKeyboardController,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme by controller.theme.collectAsState()
    var selected by remember(req) { mutableStateOf(-1) }

    // Subtle tick when the sliding finger lands on a new option.
    LaunchedEffect(selected) { if (selected >= 0) controller.haptic() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyFill.copy(alpha = 0.55f))
            .pointerInput(req) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (selected >= 0) onPick(req.options[selected]) else onDismiss()
                    },
                    onDragCancel = { onDismiss() }
                ) { change, _ ->
                    change.consume()
                    val w = size.width.toFloat() / req.options.size
                    selected = (change.position.x / w).toInt().coerceIn(0, req.options.lastIndex)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        req.options.forEachIndexed { i, opt ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (i == selected) theme.accent.copy(alpha = 0.55f)
                        else Color.Transparent
                    )
                    .clickable { onPick(opt) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = opt,
                    color = if (i == selected) Color.White else theme.keyText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * The space key with a hidden superpower: drag horizontally across it to
 * slide the text cursor character-by-character (the classic "space bar
 * cursor" gesture). A plain TAP still types a space, and [onLongClick]
 * (harakat strip) still works — the drag only kicks in after horizontal
 * touch slop, which also cancels the tap/long-press path.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.SpaceWithCursorSlide(
    controller: OnyxKeyboardController,
    weight: Float,
    onLongClick: (() -> Unit)? = null,
) {
    val theme by controller.theme.collectAsState()
    val density = LocalDensity.current
    val slider = remember(density) {
        CursorDrag(with(density) { CursorDrag.STEP_DP.dp.toPx() })
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { slider.reset() }
                ) { change, dragAmount ->
                    change.consume()
                    val steps = slider.onDelta(dragAmount)
                    val dir = if (steps > 0) 1 else -1
                    repeat(abs(steps)) { controller.moveCursor(dir) }
                }
            }
    ) {
        // CRITICAL FIX (keyboard stretched to full screen height, part 2):
        // the space key's wrapper Row used fillMaxSize() — same loose-height
        // explosion as the cursor cluster (this Box is a weighted child of
        // the bottom row, so fillMaxHeight resolved to the SCREEN height and
        // blew the IME window up to full screen). fillMaxWidth() wraps the
        // 50.dp space key instead.
        Row(modifier = Modifier.fillMaxWidth()) {
            // Theme shape setting: icon / the word / silent blank bar.
            when (theme.spaceLabel) {
                1 -> KeyButton(
                    label = stringResource(R.string.space_bar_word),
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText.copy(alpha = 0.75f),
                    onClick = { controller.onSpace() },
                    onLongClick = onLongClick,
                    weight = 1f,
                    height = 50.dp,
                    fontSize = 15
                )
                2 -> KeyButton(
                    label = "",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText.copy(alpha = 0.75f),
                    onClick = { controller.onSpace() },
                    onLongClick = onLongClick,
                    weight = 1f,
                    height = 50.dp
                )
                else -> KeyIcon(
                    icon = Icons.Filled.SpaceBar,
                    contentDescription = "Space",
                    accent = theme.accent,
                    glow = theme.glow,
                    keyFill = theme.keyFill,
                    keyText = theme.keyText.copy(alpha = 0.75f),
                    onClick = { controller.onSpace() },
                    onLongClick = onLongClick,
                    weight = 1f,
                    height = 50.dp
                )
            }
        }
    }
}

/**
 * The LIVE letter-chain strip, shown mid-glide in the toolbar's place (same
 * 46dp height so the keyboard never resizes during the gesture). Renders the
 * exact key sequence the finger has traced; every character glows softly and
 * the newest one burns at full accent — typing with your finger and watching
 * the trail write itself.
 */
@Composable
private fun GlideChainStrip(
    chain: String,
    controller: OnyxKeyboardController,
) {
    val theme by controller.theme.collectAsState()
    val annotated = buildAnnotatedString {
        chain.forEachIndexed { i, ch ->
            append(ch)
            val newest = i == chain.lastIndex
            addStyle(
                SpanStyle(
                    color = if (newest) theme.accent else theme.accent.copy(alpha = 0.45f),
                    fontWeight = if (newest) FontWeight.Black else FontWeight.Bold,
                ),
                i, i + 1
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyFill.copy(alpha = 0.35f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = annotated,
            fontSize = 19.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
    }
}

/**
 * Post-glide candidate strip, rendered in place of the toolbar (same 46dp
 * height so the keyboard never resizes). The first chip is the word that
 * was already committed by the swipe — tapping any other chip transparently
 * replaces it; any regular key press hides the strip.
 */
@Composable
private fun GlideStrip(
    candidates: List<String>,
    controller: OnyxKeyboardController,
) {
    val theme by controller.theme.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyFill.copy(alpha = 0.55f)),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        candidates.forEachIndexed { i, word ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (i == 0) theme.accent.copy(alpha = 0.45f) else Color.Transparent)
                    .clickable { controller.pickGlideCandidate(word) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    color = if (i == 0) Color.White else theme.keyText,
                    fontSize = 19.sp,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Advanced numpad — calculator-style layout with a slim function row
 * (clear/√/x²/π/%/parens), a digit grid with math operators, long-press
 * currency & symbol alternates, a wide zero, decimal point, space and a
 * smart enter. Exits via the ABC key.
 */
@Composable
private fun NumpadPage(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        // Slim function row.
        Row(Modifier.fillMaxWidth()) {
            KeyboardLayouts.numpadSlim.forEach { key ->
                if (key == "ABC") {
                    KeyButton(
                        label = key,
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.accent.copy(alpha = 0.25f),
                        keyText = theme.keyText,
                        onClick = { controller.setPage(KeyboardPage.LETTERS) },
                        height = 34.dp,
                        fontSize = 12,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    KeyButton(
                        label = key,
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.commitText(KeyboardLayouts.numpadCommit(key)) },
                        onLongClick = { controller.openAltPopup(key, numpad = true) },
                        height = 34.dp,
                        fontSize = 14
                    )
                }
            }
        }

        // Digit / operator grid (row 0 carries the backspace).
        KeyboardLayouts.numpadRows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.commitText(KeyboardLayouts.numpadCommit(key)) },
                        onLongClick = { controller.openAltPopup(key, numpad = true) },
                        height = 50.dp,
                        fontSize = 20,
                        fontWeight = FontWeight.Normal
                    )
                }
                if (index == 0) {
                    KeyIcon(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        accent = theme.accent,
                        glow = theme.glow,
                        keyFill = theme.keyFill,
                        keyText = theme.keyText,
                        onClick = { controller.onBackspace() },
                        onLongClick = { controller.onBackspaceWord() },
                        holdRepeat = { controller.onBackspace() },
                        onSwipe = { controller.onBackspaceWord() },
                        weight = 1f,
                        height = 50.dp
                    )
                }
            }
        }

        // Utility row: wide 0, decimal point, space (with cursor slide), enter.
        Row(Modifier.fillMaxWidth()) {
            KeyButton(
                label = "0",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.keyFill,
                keyText = theme.keyText,
                onClick = { controller.commitText("0") },
                onLongClick = { controller.openAltPopup("0", numpad = true) },
                weight = 2f,
                height = 50.dp,
                fontSize = 20,
                fontWeight = FontWeight.Normal
            )
            KeyButton(
                label = ".",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.keyFill,
                keyText = theme.keyText,
                onClick = { controller.commitText(".") },
                onLongClick = { controller.openAltPopup(".", numpad = true) },
                weight = 1f,
                height = 50.dp,
                fontSize = 20
            )
            SpaceWithCursorSlide(controller, weight = 1f)
            KeyIcon(
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = "Enter",
                accent = theme.accent,
                glow = theme.glow,
                keyFill = theme.accent.copy(alpha = 0.28f),
                keyText = theme.keyText,
                onClick = { controller.commitEnter() },
                weight = 1f,
                height = 50.dp
            )
        }
    }
}
