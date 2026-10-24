package com.onyx.keyboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R
import com.onyx.keyboard.ui.components.GlassCard
import com.onyx.keyboard.ui.components.OnyxLogo
import com.onyx.keyboard.ui.theme.OnyxBlue
import com.onyx.keyboard.ui.theme.OnyxPurple

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        ScreenHeader(
            title = stringResource(R.string.about_title),
            onBack = onBack
        )
        Spacer(Modifier.height(28.dp))

        OnyxLogo(size = 96.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "ONYX",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 8.sp
        )
        Text(
            text = stringResource(R.string.about_version),
            color = OnyxBlue,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(24.dp))

        GlassCard {
            Text(
                text = stringResource(R.string.about_build),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text(
                text = stringResource(R.string.about_stack),
                color = OnyxPurple,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.about_privacy_line),
            color = OnyxBlue.copy(alpha = 0.8f),
            fontSize = 12.sp
        )

        Spacer(Modifier.height(30.dp))
    }
}
