package com.widgeon.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.WidgeonApp
import com.widgeon.app.ui.components.Haptics
import com.widgeon.app.ui.components.ShimmerPlaceholder
import com.widgeon.app.ui.components.WCard
import com.widgeon.app.ui.components.WDialog
import com.widgeon.app.ui.components.WidgetThumb
import com.widgeon.app.ui.components.rememberHaptics
import com.widgeon.app.ui.components.staggerEntrance
import com.widgeon.app.widgeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CollectionViewModel(app: Application) : AndroidViewModel(app) {
    val designs = MutableStateFlow<List<WidgetDesign>>(emptyList())
    val loading = MutableStateFlow(true)
    var query by mutableStateOf("")
    var pendingDelete by mutableStateOf<WidgetDesign?>(null)

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            designs.value = getApplication<WidgeonApp>().designRepo.list()
            loading.value = false
        }
    }

    fun confirmDelete() {
        val d = pendingDelete ?: return
        pendingDelete = null
        viewModelScope.launch {
            getApplication<WidgeonApp>().designRepo.delete(d.id)
            refresh()
        }
    }
}

@Composable
fun CollectionScreen(
    onOpenEditor: (String) -> Unit,
    onCreate: () -> Unit,
    vm: CollectionViewModel = viewModel(),
) {
    val haptics = rememberHaptics()
    val designs by vm.designs.collectAsState()
    val loading by vm.loading.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }

    val filtered = designs.filter {
        vm.query.isBlank() || it.name.contains(vm.query, ignoreCase = true) || it.prompt.contains(vm.query, ignoreCase = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            "Collection",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
        )
        Text(
            "${designs.size} widget${if (designs.size == 1) "" else "s"} made with AI",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp),
        )

        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Search widgets…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )

        when {
            loading -> LazyGridSkeleton()
            filtered.isEmpty() && designs.isNotEmpty() -> EmptySearch()
            designs.isEmpty() -> EmptyCollection(onCreate)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(filtered, key = { _, d -> d.id }) { i, design ->
                    WidgetCard(
                        design = design,
                        index = i,
                        haptics = haptics,
                        onClick = { onOpenEditor(design.id) },
                        onLongPress = { haptics.error(); vm.pendingDelete = design },
                        modifier = Modifier.staggerEntrance(i, key = designs.size),
                    )
                }
            }
        }
    }

    vm.pendingDelete?.let { d ->
        WDialog(
            onDismiss = { vm.pendingDelete = null },
            icon = Icons.Rounded.Delete,
            title = "Delete “${d.name}”?",
            body = "This can't be undone. Home-screen widgets using it will show the placeholder.",
            confirmText = "Delete",
            onConfirm = { vm.confirmDelete() },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WidgetCard(
    design: WidgetDesign,
    index: Int,
    haptics: Haptics,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = { haptics.tick(); onClick() },
                onLongClick = onLongPress,
            )
            .padding(12.dp),
    ) {
        WidgetThumb(
            design,
            Modifier
                .fillMaxWidth()
                .height(if (design.rows >= 3) 200.dp else 150.dp),
            corner = 16,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            design.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                design.sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(design.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LazyGridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(List(4) { it }) { _, _ ->
            ShimmerPlaceholder(Modifier.fillMaxWidth().height(190.dp), corner = 24)
        }
    }
}

@Composable
private fun EmptySearch() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nothing matches", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Try a different search term.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyCollection(onCreate: () -> Unit) {
    val haptics = rememberHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LogoMark(Modifier.width(110.dp).height(110.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Your gallery awaits",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Every widget you generate lives here, ready to edit or pin to your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        WCard(
            onClick = { onCreate() },
            corner = 50,
        ) {
            Text(
                "Create your first widget",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
