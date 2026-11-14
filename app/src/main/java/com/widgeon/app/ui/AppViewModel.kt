package com.widgeon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.widgeon.app.WidgeonApp
import com.widgeon.app.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Global app state that must survive navigation: theme + preferences mirrors. */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: Prefs get() = (getApplication() as WidgeonApp).prefs

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<String> = _themeMode

    private val _hapticsEnabled = MutableStateFlow(prefs.hapticsEnabled)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled

    private val _notifEnabled = MutableStateFlow(prefs.notifEnabled)
    val notifEnabled: StateFlow<Boolean> = _notifEnabled

    private val _overlayClock = MutableStateFlow(prefs.overlayClock)
    val overlayClock: StateFlow<Boolean> = _overlayClock

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setOverlayClock(enabled: Boolean) {
        prefs.overlayClock = enabled
        _overlayClock.value = enabled
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.hapticsEnabled = enabled
        _hapticsEnabled.value = enabled
    }

    fun setNotifEnabled(enabled: Boolean) {
        prefs.notifEnabled = enabled
        _notifEnabled.value = enabled
    }
}

/** Tiny holder for "a notification tapped -> open this design". */
object PendingNav {
    @Volatile var designId: String? = null
}
