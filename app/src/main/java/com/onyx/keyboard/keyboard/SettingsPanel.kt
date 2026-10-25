package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.onyx.keyboard.data.repository.OnyxRepository

/** Quick settings: toggles + live theme carousel + profile switcher. */
@Composable
fun SettingsPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val themes by controller.themes.collectAsState()
    val profiles by controller.profile.collectAsState()
    val allProfiles = controller.profiles.collectAsState().value

    Column(
        Modifier
            .fillMaxWidth()
            .height(286.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        PanelHeader(controller, stringResource(R.string.panel_settings))

        ToggleRow(controller, stringResource(R.string.settings_haptics), controller.haptics) {
            controller.setSetting(OnyxRepository.KEY_HAPTICS, it)
        }
        ToggleRow(controller, stringResource(R.string.settings_sound), controller.sound) {
            controller.setSetting(OnyxRepository.KEY_SOUND, it)
        }
        ToggleRow(controller, stringResource(R.string.settings_particles), controller.particles) {
            controller.setSetting(OnyxRepository.KEY_PARTICLES, it)
        }
        ToggleRow(controller, stringResource(R.string.settings_number_row), controller.numberRow) {
            controller.setSetting(OnyxRepository.KEY_NUMBER_ROW, it)
        }
        ToggleRow(controller, stringResource(R.string.settings_autocorrect), controller.autocorrect) {
            controller.setSetting(OnyxRepository.KEY_AUTOCORRECT, it)
        }
        ToggleRow(controller, stringResource(R.string.settings_learning), controller.learning) {
            controller.setSetting(OnyxRepository.KEY_LEARNING, it)
        }
        ToggleRow(controller, stringResource(R.string.settings_gifnet), controller.gifNet) {
            controller.setSetting(OnyxRepository.KEY_GIF_NET, it)
        }

        Text(
            text = stringResource(R.string.settings_theme),
            color = theme.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(themes, key = { it.id }) { t ->
                val selected = t.id == theme.id
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) Color(t.accent).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .clickable { controller.switchTheme(t.id) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(t.accent))
                        )
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(t.glow))
                        )
                    }
                    Text(
                        text = t.name,
                        color = theme.keyText.copy(alpha = if (selected) 1f else 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_profile),
            color = theme.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allProfiles, key = { it.id }) { p ->
                val selected = p.id == profiles?.id
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) theme.accent.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .clickable { controller.switchProfile(p.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = p.avatar,
                        color = theme.keyText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " " + p.name,
                        color = theme.keyText.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    controller: OnyxKeyboardController,
    label: String,
    state: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onChange: (Boolean) -> Unit,
) {
    val theme by controller.theme.collectAsState()
    val checked by state.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = theme.keyText.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.accent,
                uncheckedThumbColor = theme.keyText.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.height(26.dp)
        )
    }
}
