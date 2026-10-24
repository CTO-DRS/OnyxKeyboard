package com.onyx.keyboard.ui.dashboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.keyboard.R
import com.onyx.keyboard.ui.components.GlassCard
import com.onyx.keyboard.ui.components.OnyxLogo
import com.onyx.keyboard.ui.components.SectionTitle
import com.onyx.keyboard.ui.theme.OnyxBlue
import com.onyx.keyboard.ui.theme.OnyxPurple

@Composable
fun DashboardScreen(
    onOpenThemes: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val stats by vm.todayStats.collectAsState()
    val totals by vm.totals.collectAsState()
    val topWords by vm.topWords.collectAsState()

    // The step users most often miss: Onyx is ENABLED in system settings but
    // not yet CHOSEN as the active keyboard. Whenever the user comes back to
    // the app in that state, open the system input-method picker for them.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                status == ImeStatus.ENABLED_NOT_SELECTED
            ) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showInputMethodPicker()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(34.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnyxLogo(size = 58.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = "ONYX",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                )
                Text(
                    text = stringResource(R.string.tagline),
                    color = OnyxBlue.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- Activation status card
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = status == ImeStatus.ACTIVE)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = stringResource(
                            when (status) {
                                ImeStatus.ACTIVE -> R.string.status_active
                                ImeStatus.ENABLED_NOT_SELECTED -> R.string.status_enabled_not_selected
                                ImeStatus.DISABLED -> R.string.status_disabled
                            }
                        ),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            when (status) {
                                ImeStatus.ACTIVE -> R.string.status_hint_active
                                ImeStatus.ENABLED_NOT_SELECTED -> R.string.status_hint_enabled
                                ImeStatus.DISABLED -> R.string.status_hint_disabled
                            }
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(
                    label = stringResource(R.string.btn_enable_settings),
                    fillWeight = false,
                    modifier = Modifier.weight(1f)
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                NeonButton(
                    label = stringResource(R.string.btn_choose_onyx),
                    fillWeight = true,
                    modifier = Modifier.weight(1f)
                ) {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showInputMethodPicker()
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- Usage statistics
        SectionTitle(stringResource(R.string.stats_title))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.stat_keystrokes), stats?.keystrokes ?: 0, Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_words), stats?.words ?: 0, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.stat_emoji), stats?.emojis ?: 0, Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_voice), stats?.voice ?: 0, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.stats_total,
                totals.keystrokes.toString(),
                totals.words.toString()
            ),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(22.dp))

        // ---- Learned words
        SectionTitle(stringResource(R.string.learned_title))
        Spacer(Modifier.height(10.dp))
        GlassCard {
            if (topWords.isEmpty()) {
                Text(
                    text = stringResource(R.string.learned_empty),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topWords.take(4).forEach { w ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(OnyxPurple.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = w.word,
                                color = OnyxPurple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- Explore grid
        SectionTitle(stringResource(R.string.explore_title))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExploreCard(stringResource(R.string.themes_title), stringResource(R.string.themes_desc),
                Icons.Filled.Palette, Modifier.weight(1f), onOpenThemes)
            ExploreCard(stringResource(R.string.profiles_title), stringResource(R.string.profiles_desc),
                Icons.Filled.Group, Modifier.weight(1f), onOpenProfiles)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExploreCard(stringResource(R.string.privacy_title), stringResource(R.string.privacy_desc),
                Icons.Filled.Lock, Modifier.weight(1f), onOpenPrivacy)
            ExploreCard(stringResource(R.string.about_title), stringResource(R.string.about_desc),
                Icons.Filled.Info, Modifier.weight(1f), onOpenAbout)
        }

        Spacer(Modifier.height(18.dp))

        // Privacy banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OnyxBlue.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = OnyxBlue,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.privacy_banner),
                color = OnyxBlue.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun StatusDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "statusDot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    val color = if (active) Color(0xFF3DFFA8) else Color(0xFFFFB347)
    Box(
        Modifier
            .size(14.dp)
            .alpha(if (active) alpha else 0.75f)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun NeonButton(
    label: String,
    fillWeight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (fillWeight) {
                    Brush.horizontalGradient(listOf(OnyxPurple, Color(0xFF8A5CFF)))
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (fillWeight) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatCard(label: String, value: Long, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(
            text = value.toString(),
            color = OnyxBlue,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ExploreCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.02f))
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = OnyxPurple,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
