package com.widgeon.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgeon.app.ui.AppViewModel
import com.widgeon.app.ui.PendingNav
import com.widgeon.app.ui.nav.AppRoot
import com.widgeon.app.ui.theme.WidgeonTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val appVm: AppViewModel = viewModel()
            val theme by appVm.themeMode.collectAsState()
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
}
