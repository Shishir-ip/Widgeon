package com.widgeon.app.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgeon.app.ai.AiClients
import com.widgeon.app.ai.AiException
import com.widgeon.app.data.Prefs
import com.widgeon.app.ui.AppViewModel
import com.widgeon.app.ui.components.GhostButton
import com.widgeon.app.ui.components.Haptics
import com.widgeon.app.ui.components.PrimaryButton
import com.widgeon.app.ui.components.SectionLabel
import com.widgeon.app.ui.components.WCard
import com.widgeon.app.ui.components.WChip
import com.widgeon.app.ui.components.WDialog
import com.widgeon.app.ui.components.WSwitch
import com.widgeon.app.ui.components.pressable
import com.widgeon.app.ui.components.rememberHaptics
import com.widgeon.app.widgeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val testState = MutableStateFlow<TestState>(TestState.Idle)

    sealed class TestState {
        data object Idle : TestState()
        data object Running : TestState()
        data class Done(val ok: Boolean, val message: String) : TestState()
    }

    fun testConnection() {
        if (testState.value is TestState.Running) return
        testState.value = TestState.Running
        viewModelScope.launch {
            val result = AiClients.test(getApplication())
            testState.value = when {
                result.isSuccess -> TestState.Done(true, "Provider replied: “${result.getOrNull()}”. You're ready to generate.")
                else -> {
                    val e = result.exceptionOrNull()
                    TestState.Done(false, friendly(e))
                }
            }
        }
    }

    companion object {
        fun friendly(t: Throwable?): String = when (t) {
            is AiException.Quota -> "Out of quota: ${t.message}"
            is AiException.Auth -> t.message ?: "Invalid API key."
            is AiException.Network -> t.message ?: "Network problem."
            else -> t?.message ?: "Unknown error."
        }
    }
}

@Composable
fun SettingsScreen(appVm: AppViewModel, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.widgeon
    val haptics = rememberHaptics()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val testState by vm.testState.collectAsState()

    var providerId by mutableStateOf(app.prefs.provider)
    var expanded by mutableStateOf(providerId)
    var showDeleteAll by mutableStateOf(false)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.onBackground,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )

        SectionLabel("AI PROVIDERS")
        Settings.providerInfos.forEach { p ->
            val selected = p.id == providerId
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pressable(
                        {
                            providerId = p.id
                            app.prefs.provider = p.id
                            expanded = p.id
                            haptics.tick()
                        },
                        haptics, 0.985f,
                    )
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                            .padding(4.dp)
                            .then(
                                Modifier
                            ),
                    )
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .padding(1.dp)
                        )
                    }
                }
                Text(
                    p.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AnimatedVisibility(
                    visible = expanded == p.id,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ProviderSettings(p, app.prefs, fieldColors, haptics)
                }
            }
        }

        // Test connection
        PrimaryButton(
            text = when (testState) {
                is SettingsViewModel.TestState.Running -> "Testing…"
                else -> "Test connection"
            },
            onClick = { vm.testConnection() },
            enabled = testState !is SettingsViewModel.TestState.Running,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        )
        GhostButton("How do I get a free API key?", onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
            }
        }, modifier = Modifier.padding(bottom = 18.dp))

        SectionLabel("APPEARANCE")
        val themeMode by appVm.themeMode.collectAsState()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            WChip("Auto", themeMode == Prefs.THEME_SYSTEM, onClick = { appVm.setThemeMode(Prefs.THEME_SYSTEM) })
            WChip("Light", themeMode == Prefs.THEME_LIGHT, onClick = { appVm.setThemeMode(Prefs.THEME_LIGHT) })
            WChip("Dark", themeMode == Prefs.THEME_DARK, onClick = { appVm.setThemeMode(Prefs.THEME_DARK) })
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("FEEDBACK")
        val hapticsEnabled by appVm.hapticsEnabled.collectAsState()
        val notifEnabled by appVm.notifEnabled.collectAsState()
        ToggleCard(
            icon = Icons.Rounded.Bolt,
            title = "Haptic feedback",
            subtitle = "Vibrations on taps, toggles and milestones",
            checked = hapticsEnabled,
        ) { appVm.setHapticsEnabled(it) }
        ToggleCard(
            icon = Icons.Rounded.Notifications,
            title = "Notifications",
            subtitle = "Progress + completion while you're away",
            checked = notifEnabled,
        ) { appVm.setNotifEnabled(it) }

        Spacer(Modifier.height(14.dp))
        SectionLabel("WEATHER")
        var city by mutableStateOf(app.prefs.weatherCity)
        OutlinedTextField(
            value = city,
            onValueChange = {
                city = it
                app.prefs.weatherCity = it.trim()
            },
            label = { Text("City for weather widgets") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        )

        SectionLabel("DATA")
        WCard(
            onClick = { showDeleteAll = true },
            corner = 20,
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Delete all widgets", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Removes every design from your collection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionLabel("ABOUT")
        WCard(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Widgeon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "v" + BuildVersionCompat.versionName(context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "AI-generated home-screen widgets, in black & white.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    // Test result dialog
    when (val t = testState) {
        is SettingsViewModel.TestState.Done -> {
            WDialog(
                onDismiss = { vm.testState.value = SettingsViewModel.TestState.Idle },
                icon = if (t.ok) Icons.Rounded.Science else Icons.Rounded.Key,
                title = if (t.ok) "Connection works" else "Couldn't connect",
                body = t.message,
                confirmText = "OK",
                dismissText = null,
                onConfirm = { vm.testState.value = SettingsViewModel.TestState.Idle },
            )
        }
        else -> Unit
    }

    if (showDeleteAll) {
        WDialog(
            onDismiss = { showDeleteAll = false },
            icon = Icons.Rounded.DeleteForever,
            title = "Delete all widgets?",
            body = "Every design in your collection will be removed permanently.",
            confirmText = "Delete all",
            onConfirm = {
                showDeleteAll = false
                scope.launch { context.widgeon.designRepo.deleteAll() }
            },
        )
    }
}

@Composable
private fun ToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    WCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WSwitch(checked = checked, onChange = onChange)
        }
    }
}

@Composable
private fun ProviderSettings(
    p: Settings.ProviderInfo,
    prefs: Prefs,
    fieldColors: androidx.compose.material3.TextFieldColors,
    haptics: Haptics,
) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 14.dp)) {
        if (p.models.isNotEmpty()) {
            Text("Model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                p.models.forEach { m ->
                    val current = prefsModel(prefs, p.id)
                    WChip(m, selected = current == m, onClick = {
                        setPrefsModel(prefs, p.id, m)
                        haptics.tick()
                    })
                }
            }
        }
        var model by mutableStateOf(prefsModel(prefs, p.id))
        OutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                setPrefsModel(prefs, p.id, it)
            },
            label = { Text(if (p.models.isEmpty()) "Model name" else "Custom model name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        if (p.id == Prefs.PROVIDER_CUSTOM) {
            var endpoint by mutableStateOf(prefs.customEndpoint)
            OutlinedTextField(
                value = endpoint,
                onValueChange = {
                    endpoint = it
                    prefs.customEndpoint = it
                },
                label = { Text("Endpoint base URL (…/v1)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        var key by mutableStateOf(prefs.apiKey(p.id))
        var show by mutableStateOf(false)
        OutlinedTextField(
            value = key,
            onValueChange = {
                key = it
                prefs.setApiKey(p.id, it)
            },
            label = { Text("API key") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        if (p.link.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .pressable({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.link))) } }, haptics, 0.97f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Rounded.OpenInNew, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    p.link.removePrefix("https://"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun prefsModel(prefs: Prefs, id: String): String = when (id) {
    Prefs.PROVIDER_GEMINI -> prefs.geminiModel
    Prefs.PROVIDER_GROQ -> prefs.groqModel
    Prefs.PROVIDER_OPENROUTER -> prefs.openRouterModel
    else -> prefs.customModel
}

private fun setPrefsModel(prefs: Prefs, id: String, value: String) {
    when (id) {
        Prefs.PROVIDER_GEMINI -> prefs.geminiModel = value
        Prefs.PROVIDER_GROQ -> prefs.groqModel = value
        Prefs.PROVIDER_OPENROUTER -> prefs.openRouterModel = value
        else -> prefs.customModel = value
    }
}

private object BuildVersionCompat {
    fun versionName(context: android.content.Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    }.getOrDefault("1.0.0")
}

private object Settings {
    data class ProviderInfo(
        val id: String,
        val name: String,
        val note: String,
        val link: String,
        val models: List<String>,
    )

    val providerInfos = listOf(
        ProviderInfo(
            Prefs.PROVIDER_GEMINI, "Google Gemini",
            "Default · gemini-3.5-flash-lite. Free tier available at aistudio.google.com",
            "https://aistudio.google.com/apikey",
            listOf("gemini-3.5-flash-lite", "gemini-3.6-flash"),
        ),
        ProviderInfo(
            Prefs.PROVIDER_GROQ, "Groq",
            "Default · openai/gpt-oss-120b. Free tier at console.groq.com",
            "https://console.groq.com/keys",
            listOf("openai/gpt-oss-120b", "qwen/qwen3.6-27b"),
        ),
        ProviderInfo(
            Prefs.PROVIDER_OPENROUTER, "OpenRouter",
            "Access hundreds of models with one key. Create a key at openrouter.ai/keys, browse models at openrouter.ai/models.",
            "https://openrouter.ai/keys",
            listOf("openai/gpt-oss-120b", "google/gemini-2.0-flash-lite-001", "deepseek/deepseek-chat-v3-0324"),
        ),
        ProviderInfo(
            Prefs.PROVIDER_CUSTOM, "Custom (OpenAI-compatible)",
            "Any /v1/chat/completions endpoint — LM Studio, Ollama, your own proxy.",
            "",
            emptyList(),
        ),
    )
}
