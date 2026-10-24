package com.onyx.keyboard.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.onyx.keyboard.ui.components.ParticleField
import com.onyx.keyboard.ui.screens.AboutScreen
import com.onyx.keyboard.ui.screens.PrivacyScreen
import com.onyx.keyboard.ui.screens.ProfilesScreen
import com.onyx.keyboard.ui.screens.ThemesScreen
import com.onyx.keyboard.ui.dashboard.DashboardScreen

@Composable
fun OnyxRoot() {
    val navController = rememberNavController()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0B12), Color(0xFF171224))
                )
            )
    ) {
        // Ambient particles persist across all screens.
        ParticleField(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFB37BFF),
            count = 36,
            maxAlpha = 0.4f
        )

        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(tween(340)) { it / 3 } + fadeIn(tween(340))
            },
            exitTransition = {
                slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(260))
            },
            popEnterTransition = {
                slideInHorizontally(tween(340)) { -it / 3 } + fadeIn(tween(340))
            },
            popExitTransition = {
                slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(260))
            }
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onOpenThemes = { navController.navigate("themes") },
                    onOpenProfiles = { navController.navigate("profiles") },
                    onOpenPrivacy = { navController.navigate("privacy") },
                    onOpenAbout = { navController.navigate("about") }
                )
            }
            composable("themes") {
                ThemesScreen(onBack = { navController.popBackStack() })
            }
            composable("profiles") {
                ProfilesScreen(onBack = { navController.popBackStack() })
            }
            composable("privacy") {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
