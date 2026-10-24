package com.onyx.keyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.keyboard.R
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.ui.components.SectionTitle
import com.onyx.keyboard.ui.themes.ThemesViewModel

@Composable
fun ThemesScreen(onBack: () -> Unit, vm: ThemesViewModel = viewModel()) {
    val themes by vm.themes.collectAsState()
    val activeThemeId by vm.activeThemeId.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        ScreenHeader(
            title = stringResource(R.string.themes_title),
            onBack = onBack
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.apply_theme),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(themes, key = { it.id }) { theme ->
                ThemeCard(
                    theme = theme,
                    isActive = theme.id == activeThemeId,
                    onClick = { vm.apply(theme.id) }
                )
            }
        }
    }
}

/** Mini keyboard preview rendered with the theme's own palette. */
@Composable
private fun ThemeCard(theme: ThemeEntity, isActive: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(theme.surfaceTop), Color(theme.surfaceBottom))
                )
            )
            .border(
                width = if (isActive) 1.8.dp else 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(theme.accent).copy(alpha = if (isActive) 1f else 0.35f),
                        Color(theme.glow).copy(alpha = if (isActive) 0.8f else 0.2f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = theme.name,
                color = Color(theme.keyText),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isActive) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.current_theme),
                    tint = Color(theme.accent),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3 mini key rows
        val rows = listOf(5, 5, 4)
        rows.forEachIndexed { i, count ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(count) { j ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Color(theme.keyFill).copy(alpha = 0.95f)
                            )
                            .border(
                                0.5.dp,
                                Color(theme.accent).copy(alpha = if (i + j == 2) 0.9f else 0.25f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(4.dp))
        Row {
            Box(
                Modifier
                    .weight(1f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(theme.accent).copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Color(theme.particle).copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(5.dp)
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
