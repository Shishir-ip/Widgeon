package com.widgeon.app.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgeon.app.ai.CreateRequest
import com.widgeon.app.ai.PromptBuilder
import com.widgeon.app.WidgeonApp
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.ui.components.IconPill
import com.widgeon.app.ui.components.Haptics
import com.widgeon.app.ui.components.PrimaryButton
import com.widgeon.app.ui.components.SectionLabel
import com.widgeon.app.ui.components.WCard
import com.widgeon.app.ui.components.WChip
import com.widgeon.app.ui.components.WSwitch
import com.widgeon.app.ui.components.WidgetThumb
import com.widgeon.app.ui.components.pressable
import com.widgeon.app.ui.components.rememberHaptics
import com.widgeon.app.ui.components.staggerEntrance
import com.widgeon.app.widgeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    var prompt by mutableStateOf("")
    var advancedOpen by mutableStateOf(false)
    var cols by mutableStateOf(2)
    var rows by mutableStateOf(2)
    var customSize by mutableStateOf(false)
    var style by mutableStateOf("Minimal")
    var accent by mutableStateOf("")
    var corner by mutableStateOf(24f)
    var wantClock by mutableStateOf(true)
    var wantDate by mutableStateOf(true)
    var wantBattery by mutableStateOf(false)
    var wantWeather by mutableStateOf(false)
    var city by mutableStateOf(getApplication<WidgeonApp>().prefs.weatherCity)
    var notes by mutableStateOf("")

    val recent = MutableStateFlow<List<WidgetDesign>>(emptyList())

    init {
        refreshRecent()
    }

    fun refreshRecent() {
        viewModelScope.launch {
            recent.value = getApplication<WidgeonApp>().designRepo.list().take(8)
        }
    }

    fun generate(haptics: Haptics): Boolean {
        val text = prompt.trim()
        if (text.isBlank()) return false
        val app = getApplication<WidgeonApp>()
        app.prefs.weatherCity = city.trim()
        haptics.click()
        app.engine.create(
            CreateRequest(
                prompt = text,
                cols = cols,
                rows = rows,
                style = style,
                accent = accent,
                cornerRadius = corner.toInt(),
                wantClock = wantClock,
                wantDate = wantDate,
                wantBattery = wantBattery,
                wantWeather = wantWeather,
                notes = notes.trim(),
            )
        )
        return true
    }
}

@Composable
fun HomeScreen(
    onOpenEditor: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val haptics = rememberHaptics()
    val recent by vm.recent.collectAsState()
    val context = LocalContext.current

    // Refresh the recent strip whenever a generation finishes (new widget added)
    val engineState by context.widgeon.engine.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(engineState) {
        if (engineState !is com.widgeon.app.ai.GenState.Running) vm.refreshRecent()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.staggerEntrance(0)) {
                Text(
                    "Create",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Describe it. AI builds it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Prompt input
        item {
            WCard(
                modifier = Modifier.fillMaxWidth().staggerEntrance(1),
                corner = 26,
            ) {
                OutlinedTextField(
                    value = vm.prompt,
                    onValueChange = { vm.prompt = it },
                    placeholder = {
                        Text(
                            "A minimal clock with the date, dark glass style…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                // Idea chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PromptBuilder.ideaChips) { (label, full) ->
                        WChip(label, selected = false, onClick = {
                            vm.prompt = full
                            haptics.tick()
                        })
                    }
                }
            }
        }

        // Advanced options
        item {
            val arrow by animateFloatAsState(if (vm.advancedOpen) 180f else 0f, tween(260), label = "arrow")
            WCard(
                modifier = Modifier.fillMaxWidth().staggerEntrance(2),
                onClick = { vm.advancedOpen = !vm.advancedOpen },
                corner = 22,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Advanced options",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(arrow),
                    )
                }

                AnimatedVisibility(
                    visible = vm.advancedOpen,
                    enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
                ) {
                    Column(Modifier.padding(top = 16.dp)) {
                        AdvancedContent(vm, haptics)
                    }
                }
            }
        }

        // Generate button
        item {
            PrimaryButton(
                text = "Generate widget",
                onClick = { vm.generate(haptics) },
                enabled = vm.prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().staggerEntrance(3),
            )
        }

        // Recent
        if (recent.isNotEmpty()) {
            item { SectionLabel("RECENT", Modifier.padding(top = 6.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.id }) { design ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(130.dp)
                                .pressable({ onOpenEditor(design.id) }, haptics, 0.96f),
                        ) {
                            WidgetThumb(design, Modifier.fillMaxWidth().height(designThumbHeight(design)), corner = 18)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                design.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(130.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                                .pressable({ onOpenEditor("") }, haptics, 0.96f),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "All widgets", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Collection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun designThumbHeight(design: WidgetDesign) =
    (when {
        design.rows >= 4 -> 190.dp
        design.rows == 3 -> 160.dp
        else -> 130.dp
    })

// ------------------------------------------------------------- advanced

@Composable
private fun AdvancedContent(vm: HomeViewModel, haptics: Haptics) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.onBackground,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    // --- Size
    SectionLabel("WIDGET SIZE")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to 1, 2 to 2, 4 to 2, 2 to 4, 4 to 4).forEach { (c, r) ->
            val selected = !vm.customSize && vm.cols == c && vm.rows == r
            WChip("${c}×$r", selected = selected, onClick = {
                vm.customSize = false; vm.cols = c; vm.rows = r
            })
        }
        WChip("Custom", selected = vm.customSize, onClick = { vm.customSize = true })
    }
    AnimatedVisibility(vm.customSize) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Stepper("Width", vm.cols, onPlus = { if (vm.cols < 5) vm.cols++ }, onMinus = { if (vm.cols > 1) vm.cols-- })
            Spacer(Modifier.width(18.dp))
            Stepper("Height", vm.rows, onPlus = { if (vm.rows < 5) vm.rows++ }, onMinus = { if (vm.rows > 1) vm.rows-- })
        }
    }

    Spacer(Modifier.height(16.dp))

    // --- Style
    SectionLabel("STYLE")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PromptBuilder.styleNames.take(3).forEach { s ->
            WChip(s, selected = vm.style == s, onClick = { vm.style = s })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        PromptBuilder.styleNames.drop(3).forEach { s ->
            WChip(s, selected = vm.style == s, onClick = { vm.style = s })
        }
    }

    Spacer(Modifier.height(16.dp))

    // --- Accent
    SectionLabel("ACCENT COLOR")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val swatches = listOf(
            "" to "None",
            "#FFFFFF" to "White",
            "#111111" to "Black",
            "#E53935" to "Red",
            "#FB8C00" to "Orange",
            "#43A047" to "Green",
            "#1E88E5" to "Blue",
            "#8E24AA" to "Purple",
        )
        swatches.forEach { (hex, _) ->
            val selected = vm.accent == hex
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (hex.isBlank()) Color.Transparent else parseColorCompat(hex))
                    .border(
                        if (selected) 2.5.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .pressable({
                        vm.accent = hex
                        haptics.tick()
                    }, haptics, 0.85f),
            )
        }
    }
    OutlinedTextField(
        value = vm.accent,
        onValueChange = { vm.accent = it.take(9) },
        label = { Text("Custom hex (#RRGGBB)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors,
        keyboardOptions = KeyboardOptions(autoCorrect = false),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )

    Spacer(Modifier.height(16.dp))

    // --- Corner radius
    SectionLabel("CORNER RADIUS · ${vm.corner.toInt()}dp")
    Slider(
        value = vm.corner,
        onValueChange = { vm.corner = it },
        valueRange = 0f..44f,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onBackground,
            activeTrackColor = MaterialTheme.colorScheme.onBackground,
            inactiveTrackColor = MaterialTheme.colorScheme.outline,
        ),
    )

    Spacer(Modifier.height(16.dp))

    // --- Live data
    SectionLabel("LIVE DATA")
    ToggleRow("Clock", vm.wantClock) { vm.wantClock = it }
    ToggleRow("Date", vm.wantDate) { vm.wantDate = it }
    ToggleRow("Battery", vm.wantBattery) { vm.wantBattery = it }
    ToggleRow("Weather", vm.wantWeather) { vm.wantWeather = it }
    AnimatedVisibility(vm.wantWeather) {
        OutlinedTextField(
            value = vm.city,
            onValueChange = { vm.city = it },
            label = { Text("City for weather (e.g. Dhaka)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    Spacer(Modifier.height(16.dp))

    // --- Notes
    SectionLabel("EXTRA NOTES FOR THE AI")
    OutlinedTextField(
        value = vm.notes,
        onValueChange = { vm.notes = it },
        placeholder = { Text("e.g. use emoji ⚡, show seconds, purple accent…") },
        minLines = 2,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Stepper(label: String, value: Int, onPlus: () -> Unit, onMinus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        IconPill(Icons.Rounded.Remove, "Decrease $label", onMinus, modifier = Modifier.size(34.dp))
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        IconPill(Icons.Rounded.Add, "Increase $label", onPlus, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        WSwitch(checked = checked, onChange = onChange)
    }
}

private fun parseColorCompat(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Transparent
}
