package com.widgeon.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.widgeon.app.ai.AiException
import com.widgeon.app.ai.GenState
import com.widgeon.app.ui.AppViewModel
import com.widgeon.app.ui.PendingNav
import com.widgeon.app.ui.components.GeneratingOverlay
import com.widgeon.app.ui.components.Haptics
import com.widgeon.app.ui.components.SuccessBurst
import com.widgeon.app.ui.components.WDialog
import com.widgeon.app.ui.components.pressable
import com.widgeon.app.ui.components.rememberHaptics
import com.widgeon.app.ui.screens.CollectionScreen
import com.widgeon.app.ui.screens.EditorScreen
import com.widgeon.app.ui.screens.HomeScreen
import com.widgeon.app.ui.screens.OnboardingScreen
import com.widgeon.app.ui.screens.SettingsScreen
import com.widgeon.app.widgeon

object Routes {
    const val HOME = "home"
    const val COLLECTION = "collection"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{designId}"
    fun editor(id: String) = "editor/$id"
}

@Composable
fun AppRoot(appVm: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current
    val engine = context.widgeon.engine
    val engineState by engine.state.collectAsState()
    val haptics = rememberHaptics()
    val startRoute = androidx.compose.runtime.remember {
        if (context.widgeon.prefs.onboardingDone) Routes.HOME else "onboarding"
    }

    // Deep link from notifications
    LaunchedEffect(Unit) {
        PendingNav.designId?.let {
            navController.navigate(Routes.editor(it))
            PendingNav.designId = null
        }
    }

    val onMainTabs = route == Routes.HOME || route == Routes.COLLECTION || route == Routes.SETTINGS || route == null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                enterTransition = { fadeIn(tween(260)) + androidx.compose.animation.slideInHorizontally(tween(300)) { it / 22 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(260)) },
                popExitTransition = { fadeOut(tween(180)) + androidx.compose.animation.slideOutHorizontally(tween(240)) { it / 22 } },
            ) {
                composable("onboarding") {
                    OnboardingScreen(appVm, onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    })
                }
                composable(Routes.HOME) {
                    HomeScreen(onOpenEditor = { id -> if (id.isNotBlank()) navController.navigate(Routes.editor(id)) else navController.navigate(Routes.COLLECTION) })
                }
                composable(Routes.COLLECTION) {
                    CollectionScreen(
                        onOpenEditor = { id -> navController.navigate(Routes.editor(id)) },
                        onCreate = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(appVm) }
                composable(Routes.EDITOR) { entry ->
                    val id = entry.arguments?.getString("designId").orEmpty()
                    EditorScreen(designId = id, onBack = { navController.popBackStack() })
                }
            }

            AnimatedVisibility(
                visible = onMainTabs,
                enter = slideInVertically(tween(280)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
            ) {
                WBottomBar(current = route ?: Routes.HOME) { target ->
                    if (target != route) {
                        haptics.click()
                        navController.navigate(target) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }

        // ---- Global generation experience
        when (val st = engineState) {
            is GenState.Running -> GeneratingOverlay(
                stage = st.stage,
                progress = st.progress,
                onCancel = { haptics.tick(); engine.cancel() },
            )
            is GenState.Success -> {
                val targetId = st.design.id
                val alreadyThere = route == Routes.EDITOR && route?.let { r ->
                    backStack?.arguments?.getString("designId") == targetId
                } == true
                if (!alreadyThere) {
                    SuccessBurst(
                        name = st.design.name,
                        onDone = {
                            navController.navigate(Routes.editor(targetId)) { launchSingleTop = true }
                            engine.consume()
                        },
                    )
                }
            }
            is GenState.Failed -> GenerationErrorDialog(
                error = st.error,
                onDismiss = { engine.consume() },
                onOpenSettings = {
                    engine.consume()
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
            )
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------- bottom bar

private data class Tab(val route: String, val icon: ImageVector, val label: String)

@Composable
private fun WBottomBar(current: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Tab(Routes.HOME, Icons.Rounded.Widgets, "Create"),
        Tab(Routes.COLLECTION, Icons.Rounded.GridView, "Collection"),
        Tab(Routes.SETTINGS, Icons.Rounded.Settings, "Settings"),
    )
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(70.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == current
                val scale by animateFloatAsState(if (selected) 1.14f else 1f, tween(240), label = "tabScale")
                val alpha by animateFloatAsState(if (selected) 1f else 0.45f, tween(240), label = "tabAlpha")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pressable({ onSelect(tab.route) }, rememberHaptics(), pressed = 0.9f),
                ) {
                    Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp).scale(scale).alpha(alpha))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.alpha(alpha),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun GenerationErrorDialog(
    error: Throwable,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    LaunchedEffect(error) { haptics.error() }

    val isQuota = error is AiException.Quota
    val isAuth = error is AiException.Auth

    WDialog(
        onDismiss = onDismiss,
        icon = when {
            isQuota -> Icons.Rounded.WaterDrop
            isAuth -> Icons.Rounded.Info
            else -> Icons.Rounded.ErrorOutline
        },
        title = when {
            isQuota -> "Out of API credits"
            isAuth -> "API key problem"
            else -> "Generation failed"
        },
        body = error.message?.take(320) ?: "Something went wrong. Please try again.",
        confirmText = when {
            isQuota || isAuth -> "Open Settings"
            else -> "Close"
        },
        dismissText = when {
            isQuota || isAuth -> "Close"
            else -> null
        },
        onConfirm = { onOpenSettings() },
    )
}
