package com.widgeon.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.widgeon.app.data.Prefs
import com.widgeon.app.ui.AppViewModel
import com.widgeon.app.ui.components.Haptics
import com.widgeon.app.ui.components.PrimaryButton
import com.widgeon.app.ui.components.WCard
import com.widgeon.app.ui.components.WChip
import com.widgeon.app.ui.components.WSwitch
import com.widgeon.app.ui.components.pressable
import com.widgeon.app.ui.components.rememberHaptics
import com.widgeon.app.widgeon
import kotlinx.coroutines.launch

private data class ProviderInfo(
    val id: String,
    val name: String,
    val note: String,
    val keyHint: String,
    val link: String,
    val models: List<String>,
)

private val PROVIDERS = listOf(
    ProviderInfo(
        Prefs.PROVIDER_GEMINI, "Google Gemini",
        "Free tier available at aistudio.google.com",
        "AIza…", "https://aistudio.google.com/apikey",
        listOf("gemini-3.5-flash-lite", "gemini-3.6-flash"),
    ),
    ProviderInfo(
        Prefs.PROVIDER_GROQ, "Groq",
        "Free tier at console.groq.com",
        "gsk_…", "https://console.groq.com/keys",
        listOf("openai/gpt-oss-120b", "qwen/qwen3.6-27b"),
    ),
    ProviderInfo(
        Prefs.PROVIDER_OPENROUTER, "OpenRouter",
        "One key, hundreds of models · openrouter.ai/keys",
        "sk-or-…", "https://openrouter.ai/keys",
        listOf("openai/gpt-oss-120b", "google/gemini-2.0-flash-lite-001"),
    ),
    ProviderInfo(
        Prefs.PROVIDER_CUSTOM, "Custom (OpenAI-compatible)",
        "Any /v1/chat/completions endpoint your model supports",
        "Optional key", "",
        emptyList(),
    ),
)

@Composable
fun OnboardingScreen(appVm: AppViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val app = context.widgeon

    val pagerState = rememberPagerState(pageCount = { 3 })
    var providerId by rememberSaveable { mutableStateOf(app.prefs.provider) }
    var apiKey by rememberSaveable { mutableStateOf(app.prefs.apiKey(app.prefs.provider)) }
    var customModel by rememberSaveable { mutableStateOf(app.prefs.customModel) }
    var customEndpoint by rememberSaveable { mutableStateOf(app.prefs.customEndpoint) }
    var model by rememberSaveable { mutableStateOf(app.prefs.geminiModel) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    val provider = PROVIDERS.first { it.id == providerId }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        appVm.setNotifEnabled(granted || app.prefs.notifEnabled)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Progress dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                repeat(3) { i ->
                    val active = pagerState.currentPage >= i
                    val w by animateFloatAsState(if (pagerState.currentPage == i) 26f else 8f, tween(280), label = "dot")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = w.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.outline
                            ),
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ProviderPage(
                        providerId = providerId,
                        onProvider = {
                            providerId = it
                            apiKey = app.prefs.apiKey(it)
                            model = when (it) {
                                Prefs.PROVIDER_GEMINI -> app.prefs.geminiModel
                                Prefs.PROVIDER_GROQ -> app.prefs.groqModel
                                Prefs.PROVIDER_OPENROUTER -> app.prefs.openRouterModel
                                else -> app.prefs.customModel
                            }
                            haptics.tick()
                        },
                        model = model,
                        onModel = { model = it },
                        customModel = customModel,
                        onCustomModel = { customModel = it },
                        customEndpoint = customEndpoint,
                        onCustomEndpoint = { customEndpoint = it },
                        apiKey = apiKey,
                        onApiKey = { apiKey = it },
                        showKey = showKey,
                        onShowKey = { showKey = !showKey },
                    )
                    else -> FinishPage(
                        appVm = appVm,
                        onRequestNotifications = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }

            // Bottom controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    WChip("Back", selected = false, onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    })
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = if (pagerState.currentPage == 2) "Start creating" else "Continue",
                    onClick = {
                        if (pagerState.currentPage == 1) {
                            // Persist the AI setup as we leave the page
                            app.prefs.provider = providerId
                            app.prefs.setApiKey(providerId, apiKey)
                            when (providerId) {
                                Prefs.PROVIDER_GEMINI -> app.prefs.geminiModel = model
                                Prefs.PROVIDER_GROQ -> app.prefs.groqModel = model
                                Prefs.PROVIDER_OPENROUTER -> app.prefs.openRouterModel = model
                                else -> {
                                    app.prefs.customModel = customModel
                                    app.prefs.customEndpoint = customEndpoint
                                }
                            }
                        }
                        if (pagerState.currentPage == 2) {
                            app.prefs.onboardingDone = true
                            onDone()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = pagerState.currentPage != 1 || model.isNotBlank(),
                    modifier = Modifier.width(170.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ pages

@Composable
private fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        LogoMark(Modifier.size(120.dp))
        Spacer(Modifier.height(34.dp))
        Text(
            "Widgeon",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Describe a home-screen widget in plain words. AI designs it. It lands on your home screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Monogram with a slow breathing pulse. */
@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "logo")
    val t by transition.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2400),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "logoPulse",
    )
    val ink = MaterialTheme.colorScheme.onBackground
    val scale by animateFloatAsState(1f + 0.03f * t, label = "logoScale")
    Canvas(modifier.scale(scale)) {
        val s = size.minDimension * 0.26f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - s, cy - s * 0.82f)
            lineTo(cx - s * 0.42f, cy + s * 0.82f)
            lineTo(cx, cy - s * 0.36f)
            lineTo(cx + s * 0.42f, cy + s * 0.82f)
            lineTo(cx + s, cy - s * 0.82f)
        }
        drawCircle(ink.copy(alpha = 0.07f), radius = s * 1.9f)
        drawCircle(ink.copy(alpha = 0.05f), radius = s * 1.35f + 6f * t)
        drawPath(
            p, ink,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.minDimension * 0.055f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun ProviderPage(
    providerId: String,
    onProvider: (String) -> Unit,
    model: String,
    onModel: (String) -> Unit,
    customModel: String,
    onCustomModel: (String) -> Unit,
    customEndpoint: String,
    onCustomEndpoint: (String) -> Unit,
    apiKey: String,
    onApiKey: (String) -> Unit,
    showKey: Boolean,
    onShowKey: () -> Unit,
) {
    val context = LocalContext.current
    val provider = PROVIDERS.first { it.id == providerId }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onBackground,
        cursorColor = MaterialTheme.colorScheme.onBackground,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text("Choose your AI", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Pick a provider and paste a key. Free tiers are available for all of them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )

        PROVIDERS.forEach { p ->
            val selected = p.id == providerId
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(18.dp),
                    )
                    .pressable({ onProvider(p.id) }, rememberHaptics(), 0.98f)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.onPrimary else Color.Transparent)
                            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
                Text(
                    p.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Model
        Text("Model", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        if (provider.models.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                provider.models.forEach { m ->
                    WChip(m, selected = model == m, onClick = { onModel(m) })
                }
            }
            if (model !in provider.models) {
                Spacer(Modifier.height(8.dp))
            }
        }
        OutlinedTextField(
            value = model,
            onValueChange = onModel,
            label = { Text(if (provider.models.isEmpty()) "Model name" else "Custom model name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        if (providerId == Prefs.PROVIDER_CUSTOM) {
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = onCustomEndpoint,
                label = { Text("Endpoint base URL (…/v1)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        // API key
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKey,
            label = { Text("API key (${provider.keyHint})") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            trailingIcon = {
                IconButton(onClick = onShowKey) {
                    Icon(
                        if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showKey) "Hide key" else "Show key",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (provider.link.isNotBlank()) {
            TextButtonCompat(
                text = "Get a free key · ${provider.link.removePrefix("https://")}",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.link)))
                    }
                },
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TextButtonCompat(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(50))
            .pressable(onClick, rememberHaptics(), 0.97f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Rounded.OpenInNew, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinishPage(appVm: AppViewModel, onRequestNotifications: () -> Unit) {
    val haptics = rememberHaptics()
    val notifEnabled by appVm.notifEnabled.collectAsState()
    val hapticsEnabled by appVm.hapticsEnabled.collectAsState()
    val themeMode by appVm.themeMode.collectAsState()

    // Pop the system permission dialog automatically when this page appears
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            kotlinx.coroutines.delay(700)
            if (!notifEnabled) onRequestNotifications()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text("Finishing touches", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Widgeon only asks for what it truly needs. You can change all of this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )

        WCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Progress notifications", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "See generation progress and completion even when you leave the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WSwitch(checked = notifEnabled, onChange = { on ->
                    if (on) onRequestNotifications()
                    appVm.setNotifEnabled(on)
                })
            }
        }

        WCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Haptic feedback", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Subtle vibrations on taps, toggles and generation milestones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WSwitch(checked = hapticsEnabled, onChange = { appVm.setHapticsEnabled(it) })
            }
        }

        Text("THEME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(Prefs.THEME_SYSTEM, "Auto", Icons.Rounded.Palette),
                Triple(Prefs.THEME_LIGHT, "Light", Icons.Rounded.Palette),
                Triple(Prefs.THEME_DARK, "Dark", Icons.Rounded.Palette),
            ).forEach { (mode, label, _) ->
                WChip(label, selected = themeMode == mode, onClick = { appVm.setThemeMode(mode) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
