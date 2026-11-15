package com.widgeon.app.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.widgeon.app.ai.GenState
import com.widgeon.app.WidgeonApp
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.ui.components.GhostButton
import com.widgeon.app.ui.components.Haptics
import com.widgeon.app.ui.components.IconPill
import com.widgeon.app.ui.components.PrimaryButton
import com.widgeon.app.ui.components.ShimmerPlaceholder
import com.widgeon.app.ui.components.WChip
import com.widgeon.app.ui.components.WDialog
import com.widgeon.app.ui.components.rememberHaptics
import com.widgeon.app.widgeon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application, val designId: String) : AndroidViewModel(app) {

    var history by mutableStateOf<List<WidgetDesign>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set
    var loaded by mutableStateOf(false)
        private set
    var savedTick by mutableStateOf(0)
        private set
    val lastInstruction = MutableStateFlow("")

    val current: WidgetDesign? get() = history.getOrNull(index)
    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < history.lastIndex

    init {
        viewModelScope.launch {
            val d = getApplication<WidgeonApp>().designRepo.get(designId)
            if (d != null) {
                history = listOf(d)
                index = 0
            }
            loaded = true
        }
        // Apply incoming AI refinements for this design (from the editor chat)
        viewModelScope.launch {
            getApplication<WidgeonApp>().engine.state.collect { st ->
                if (st is GenState.Success && st.design.id == designId && current?.version != st.design.version) {
                    push(st.design)
                    persist()
                }
            }
        }
    }

    fun push(design: WidgetDesign) {
        val currentV = history.getOrNull(index)?.version ?: -1
        val bumped = if (design.version == currentV) design.copy(version = design.version + 1) else design
        history = history.take(index + 1) + bumped
        index = history.lastIndex
    }

    fun undo() {
        if (canUndo) index--
    }

    fun redo() {
        if (canRedo) index++
    }

    fun persist() {
        current?.let { d ->
            viewModelScope.launch {
                getApplication<WidgeonApp>().designRepo.upsert(d)
                savedTick++
                withContext(Dispatchers.Default) {
                    com.widgeon.app.widgets.WidgetHost.updateAll(getApplication())
                }
            }
        }
    }

    fun removeImage(name: String) {
        current?.let { d -> push(d.copy(images = d.images - name)) }
    }

    fun addImages(names: List<String>) {
        current?.let { d ->
            push(d.copy(images = (d.images + names).distinct().take(12)))
            persist()
        }
    }

    fun setSize(cols: Int, rows: Int) {
        val d = current ?: return
        if (d.cols != cols || d.rows != rows) {
            push(d.copy(cols = cols, rows = rows))
            persist()
        }
    }

    fun rename(name: String) {
        current?.let { push(it.copy(name = name.take(40))) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            getApplication<WidgeonApp>().designRepo.delete(designId)
            onDone()
        }
    }

    fun refine(instruction: String) {
        val c = current ?: return
        lastInstruction.value = instruction
        getApplication<WidgeonApp>().engine.refine(c, instruction)
    }
}

@Composable
fun EditorScreen(
    designId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = run {
        val app = LocalContext.current.widgeon
        viewModel(
            key = "editor_$designId",
            factory = viewModelFactory { initializer { EditorViewModel(app, designId) } },
        )
    },
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val engine = context.widgeon.engine
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }
    var showPinHint by remember { mutableStateOf(false) }
    var instruction by remember { mutableStateOf("") }
    var previewBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val design = vm.current
    val hasImageSlots = remember(design?.elements, design?.images) {
        design != null && (design.images.isNotEmpty() || hasImageElement(design))
    }

    val pickPhotos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val names = com.widgeon.app.data.ImageStore.saveAll(context, uris)
                if (names.isNotEmpty()) vm.addImages(names)
            }
        }
    }

    // Re-render preview whenever the version changes + refresh bound home-screen widgets
    LaunchedEffect(design?.id, design?.version) {
        val d = design ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            previewBmp = com.widgeon.app.widgets.WidgetHost.renderDesign(
                context, d,
                (d.cols * 70 - 30).coerceAtLeast(48),
                (d.rows * 70 - 30).coerceAtLeast(48),
            )
            com.widgeon.app.widgets.WidgetHost.updateAll(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ------- Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconPill(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { haptics.tick(); onBack() })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    design?.name ?: "Widget",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                if (design != null) {
                    Text(
                        "v${design.version} · ${design.sizeLabel}" + if (vm.canUndo) " · edited" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconPill(
                Icons.Rounded.Undo, "Undo", { haptics.undo(); vm.undo() },
                enabled = vm.canUndo,
            )
            Spacer(Modifier.width(8.dp))
            IconPill(
                Icons.Rounded.Redo, "Redo", { haptics.undo(); vm.redo() },
                enabled = vm.canRedo,
            )
            Spacer(Modifier.width(8.dp))
            IconPill(Icons.Rounded.Delete, "Delete", { showDelete = true })
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ------- Preview
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
                    .height(previewHeight(design)),
            ) {
                val bmp = previewBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Widget preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    ShimmerPlaceholder(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(180.dp),
                        corner = 22,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WChip("Refresh preview", selected = false, onClick = {
                    val d = design ?: return@WChip
                    scope.launch {
                        previewBmp = withContext(Dispatchers.Default) {
                            com.widgeon.app.widgets.WidgetHost.renderDesign(
                                context, d,
                                (d.cols * 70 - 30).coerceAtLeast(48),
                                (d.rows * 70 - 30).coerceAtLeast(48),
                            )
                        }
                    }
                })
                WChip("Add to Home", selected = false, onClick = {
                    val d = design ?: return@WChip
                    haptics.click()
                    val ok = com.widgeon.app.widgets.WidgetHost.requestPin(context, d)
                    if (!ok) showPinHint = true
                })
            }

            // ------- Grid size (content re-adapts on the home screen)
            Spacer(Modifier.height(16.dp))
            Text(
                "Size — content re-adapts when you resize the widget",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScrollable(),
            ) {
                listOf(1 to 1, 2 to 2, 4 to 2, 2 to 4, 4 to 4).forEach { (c, r) ->
                    WChip("${c}×$r", selected = design?.cols == c && design?.rows == r, onClick = { vm.setSize(c, r) })
                }
            }

            // ------- Photos (for image elements)
            if (hasImageSlots && design != null) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Photos",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(
                        if (design.images.isEmpty()) "Attach" else "Add more",
                        onClick = {
                            pickPhotos.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    )
                }
                if (design.images.isEmpty()) {
                    Text(
                        "This design has photo slots. Attach pictures to fill them — rotating slots cycle through all photos on the home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        items(design.images, key = { it }) { name ->
                            PhotoThumb(name = name, onRemove = { vm.removeImage(name) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ------- Refine input
            Text(
                "Ask the AI to change anything",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            if (vm.lastInstruction.value.isNotBlank()) {
                Text(
                    "Last change: “${vm.lastInstruction.value}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = { Text("e.g. make the clock bigger and add seconds") },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconPill(
                    Icons.Rounded.Send, "Send change",
                    onClick = {
                        val text = instruction.trim()
                        if (text.isNotBlank()) {
                            vm.refine(text)
                            instruction = ""
                        }
                    },
                    container = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    enabled = instruction.isNotBlank(),
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(22.dp))

            PrimaryButton(
                text = "Save changes",
                onClick = { vm.persist() },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    // ------- dialogs
    if (showDelete && design != null) {
        WDialog(
            onDismiss = { showDelete = false },
            icon = Icons.Rounded.Delete,
            title = "Delete “${design.name}”?",
            body = "The design is removed from your collection. Widgets already placed on your home screen will fall back to the Widgeon placeholder.",
            confirmText = "Delete",
            onConfirm = {
                showDelete = false
                vm.delete { onBack() }
            },
        )
    }
    if (showPinHint) {
        WDialog(
            onDismiss = { showPinHint = false },
            icon = Icons.Rounded.AddToHomeScreen,
            title = "Add it manually",
            body = "Your launcher doesn't support one-tap pinning. Long-press the home screen → Widgets → Widgeon, pick a size, and choose this design.",
            confirmText = "Got it",
            dismissText = null,
            onConfirm = { showPinHint = false },
        )
    }
}

private fun previewHeight(design: WidgetDesign?) = when {
    design == null -> 200.dp
    design.rows >= 4 && design.cols >= 3 -> 340.dp
    design.rows >= 4 -> 320.dp
    design.cols >= 3 -> 220.dp
    else -> 240.dp
}

private fun hasImageElement(design: WidgetDesign): Boolean =
    design.elements.any { it.type == "image" || it.elements.any { e -> e.type == "image" } }

@Composable
private fun Modifier.horizontalScrollable(): Modifier =
    this.horizontalScroll(rememberScrollState())

@Composable
private fun PhotoThumb(name: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    var bmp by remember(name) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(name) {
        withContext(Dispatchers.IO) { bmp = com.widgeon.app.data.ImageStore.load(context, name) }
    }
    Box {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        IconPill(
            Icons.Rounded.Close, "Remove photo", onRemove,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp),
            container = MaterialTheme.colorScheme.primary,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
