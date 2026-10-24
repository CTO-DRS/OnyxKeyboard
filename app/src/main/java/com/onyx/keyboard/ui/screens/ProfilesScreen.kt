package com.onyx.keyboard.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.keyboard.R
import com.onyx.keyboard.data.db.ProfileEntity
import com.onyx.keyboard.ui.components.GlassCard
import com.onyx.keyboard.ui.components.SectionTitle
import com.onyx.keyboard.ui.profiles.ProfilesViewModel
import com.onyx.keyboard.ui.theme.OnyxBlue
import com.onyx.keyboard.ui.theme.OnyxPurple

@Composable
fun ProfilesScreen(onBack: () -> Unit, vm: ProfilesViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsState()
    val active by vm.activeProfile.collectAsState()
    val customWords by vm.customWords.collectAsState()
    val shortcuts by vm.shortcuts.collectAsState()

    var showAddProfile by remember { mutableStateOf(false) }
    var showAddWord by remember { mutableStateOf(false) }
    var showAddShortcut by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        ScreenHeader(title = stringResource(R.string.profiles_title), onBack = onBack)
        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionTitle(stringResource(R.string.profiles_title)) }
            items(profiles, key = { it.id }) { p ->
                ProfileRow(
                    profile = p,
                    isActive = p.id == active?.id,
                    canDelete = profiles.size > 1,
                    onSwitch = { vm.switchTo(p.id) },
                    onDelete = { vm.delete(p.id) }
                )
            }
            item {
                AddRow(label = stringResource(R.string.add_profile)) { showAddProfile = true }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.dictionary_title))
            }
            items(customWords, key = { it.id }) { w ->
                WordRow(word = w.word, onDelete = { vm.deleteWord(w) })
            }
            if (customWords.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.empty_list),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
            item { AddRow(label = stringResource(R.string.add_word)) { showAddWord = true } }

            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.shortcuts_title))
            }
            items(shortcuts, key = { it.id }) { s ->
                ShortcutRow(
                    trigger = s.trigger,
                    expansion = s.expansion,
                    onDelete = { vm.deleteShortcut(s.id) }
                )
            }
            if (shortcuts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.empty_list),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
            item {
                AddRow(label = stringResource(R.string.add_shortcut)) { showAddShortcut = true }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showAddProfile) {
        InputDialog(
            title = stringResource(R.string.add_profile),
            fields = listOf(
                stringResource(R.string.profile_name),
                stringResource(R.string.profile_avatar)
            ),
            onDismiss = { showAddProfile = false }
        ) { values ->
            vm.addProfile(
                name = values[0],
                avatar = values.getOrElse(1) { "O" }.ifBlank { "O" }
            )
            showAddProfile = false
        }
    }
    if (showAddWord) {
        InputDialog(
            title = stringResource(R.string.add_word),
            fields = listOf(stringResource(R.string.word_hint)),
            onDismiss = { showAddWord = false }
        ) { values ->
            vm.addWord(values[0])
            showAddWord = false
        }
    }
    if (showAddShortcut) {
        InputDialog(
            title = stringResource(R.string.add_shortcut),
            fields = listOf(
                stringResource(R.string.trigger_hint),
                stringResource(R.string.expansion_hint)
            ),
            onDismiss = { showAddShortcut = false }
        ) { values ->
            vm.addShortcut(
                trigger = values[0],
                expansion = values.getOrElse(1) { "" }
            )
            showAddShortcut = false
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileEntity,
    isActive: Boolean,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OnyxPurple.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.avatar, color = OnyxPurple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(profile.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (isActive) {
                    Text(
                        stringResource(R.string.active_profile),
                        color = OnyxBlue,
                        fontSize = 11.sp
                    )
                }
            }
            if (!isActive) {
                Text(
                    stringResource(R.string.switch_to),
                    color = OnyxPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSwitch)
                        .padding(8.dp)
                )
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = OnyxBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (canDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                )
            }
        }
    }
}

@Composable
private fun WordRow(word: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(word, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.delete),
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
private fun ShortcutRow(trigger: String, expansion: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(trigger, color = OnyxPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(" → ", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
        Text(expansion, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.delete),
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OnyxPurple.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = OnyxPurple, modifier = Modifier.size(16.dp))
        Text(
            " $label",
            color = OnyxPurple,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Simple multi-field dialog; calls [onSubmit] once with all field values. */
@Composable
private fun InputDialog(
    title: String,
    fields: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (List<String>) -> Unit,
) {
    val values = remember(fields) { Array(fields.size) { mutableStateOf("") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        containerColor = Color(0xFF171224),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, hint ->
                    OutlinedTextField(
                        value = values[i].value,
                        onValueChange = { values[i].value = it },
                        placeholder = { Text(hint, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = OnyxPurple,
                            focusedBorderColor = OnyxPurple,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(values.map { it.value }) }) {
                Text(stringResource(R.string.confirm), color = OnyxPurple, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}
