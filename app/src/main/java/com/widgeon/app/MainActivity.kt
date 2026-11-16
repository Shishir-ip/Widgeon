package com.widgeon.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgeon.app.data.Prefs
import com.widgeon.app.ui.AppViewModel
import com.widgeon.app.ui.PendingNav
import com.widgeon.app.ui.nav.AppRoot
import com.widgeon.app.ui.theme.WidgeonTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        refreshWidgets()
        setContent {
            val appVm: AppViewModel = viewModel()
            val theme by appVm.themeMode.collectAsState()
            val systemDark =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (theme) {
                Prefs.THEME_DARK -> true
                Prefs.THEME_LIGHT -> false
                else -> systemDark
            }
            // System bars follow the APP theme, not just the system setting
            LaunchedEffect(dark) {
                val style = if (dark) {
                    androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            WidgeonTheme(themeMode = theme) {
                AppRoot(appVm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("open_design")?.let { PendingNav.designId = it }
    }

    /** Opening the app is a good moment to push fresh data into bound widgets. */
    private fun refreshWidgets() {
        val app = application as WidgeonApp
        app.appScope.launch {
            runCatching {
                if (app.prefs.weatherCity.isNotBlank()) {
                    com.widgeon.app.render.WeatherRepository.refresh(this@MainActivity)
                }
                com.widgeon.app.widgets.WidgetHost.updateAll(this@MainActivity)
            }
        }
    }
}
