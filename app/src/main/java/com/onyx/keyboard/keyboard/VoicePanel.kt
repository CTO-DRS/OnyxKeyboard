package com.onyx.keyboard.keyboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import com.onyx.keyboard.R

/** Voice dictation panel — delegates to the system speech recognizer. */
@Composable
fun VoicePanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val active by controller.voiceActive.collectAsState()
    val partial by controller.voiceText.collectAsState()
    val error by controller.voiceError.collectAsState()

    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .height(286.dp)
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PanelHeader(controller, stringResource(R.string.panel_voice))

        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(84.dp)
                .scale(if (active) pulseScale else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(theme.accent.copy(alpha = 0.9f), theme.glow.copy(alpha = 0.4f))
                    )
                )
                .clickable {
                    if (active) controller.stopVoice() else controller.startVoice()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (active) "Stop" else "Start",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }

        Text(
            text = when {
                error != null -> stringResource(R.string.voice_error) + " — " + (error ?: "")
                active -> stringResource(R.string.voice_listening)
                partial.isNotBlank() -> partial
                else -> stringResource(R.string.voice_hint)
            },
            color = theme.keyText.copy(alpha = if (active) 1f else 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
