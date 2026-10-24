package com.onyx.keyboard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.keyboard.R
import com.onyx.keyboard.ui.components.GlassCard
import com.onyx.keyboard.ui.components.OnyxLogo
import com.onyx.keyboard.ui.components.SectionTitle
import com.onyx.keyboard.ui.privacy.PrivacyViewModel
import com.onyx.keyboard.ui.theme.OnyxBlue
import com.onyx.keyboard.ui.theme.OnyxPurple

@Composable
fun PrivacyScreen(onBack: () -> Unit, vm: PrivacyViewModel = viewModel()) {
    val context = LocalContext.current
    var confirmWipe by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by system settings; UI re-checks live */ }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        ScreenHeader(title = stringResource(R.string.privacy_title), onBack = onBack)
        Spacer(Modifier.height(16.dp))

        PrivacyCard(
            icon = Icons.Filled.Shield,
            title = stringResource(R.string.privacy_local_title),
            body = stringResource(R.string.privacy_local_desc)
        )
        Spacer(Modifier.height(12.dp))
        PrivacyCard(
            icon = Icons.Filled.EnhancedEncryption,
            title = stringResource(R.string.privacy_encrypt_title),
            body = stringResource(R.string.privacy_encrypt_desc)
        )
        Spacer(Modifier.height(12.dp))
        PrivacyCard(
            icon = Icons.Filled.Shield,
            title = stringResource(R.string.privacy_permissions_title),
            body = null
        ) {
            PermissionLine(stringResource(R.string.privacy_perm_vibrate))
            PermissionLine(stringResource(R.string.privacy_perm_mic))
            PermissionLine(stringResource(R.string.privacy_perm_net))
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.privacy_voice_note),
                color = OnyxBlue.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(OnyxPurple.copy(alpha = 0.15f))
                        .clickable {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = OnyxPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        " " + stringResource(R.string.mic_permission),
                        color = OnyxPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.privacy_title))
        Spacer(Modifier.height(10.dp))

        WipeRow(stringResource(R.string.wipe_learned)) { confirmWipe = "learned" }
        Spacer(Modifier.height(8.dp))
        WipeRow(stringResource(R.string.wipe_clips)) { confirmWipe = "clips" }

        Spacer(Modifier.height(30.dp))
    }

    confirmWipe?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmWipe = null },
            title = {
                Text(stringResource(R.string.confirm_wipe), color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.confirm_wipe_msg), color = Color.White.copy(alpha = 0.7f)) },
            containerColor = Color(0xFF171224),
            confirmButton = {
                TextButton(onClick = {
                    if (kind == "learned") vm.wipeLearned() else vm.clearClips()
                    confirmWipe = null
                }) {
                    Text(stringResource(R.string.confirm), color = Color(0xFFFF5CA8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = null }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
private fun PrivacyCard(
    icon: ImageVector,
    title: String,
    body: String?,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = OnyxBlue, modifier = Modifier.size(22.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        if (body != null) {
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        extra()
    }
}

@Composable
private fun PermissionLine(text: String) {
    Text(
        text = "•  $text",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun WipeRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFF5CA8).copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = label,
            tint = Color(0xFFFF5CA8),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color(0xFFFF5CA8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
