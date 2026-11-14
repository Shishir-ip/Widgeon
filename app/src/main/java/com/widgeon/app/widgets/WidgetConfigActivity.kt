package com.widgeon.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.ui.theme.WidgeonTheme
import com.widgeon.app.widgeon
import kotlinx.coroutines.launch

/**
 * Opened by the launcher when a Widgeon widget is dropped on the home screen.
 * Lets the user pick which saved design this widget instance should show.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancel by default so backing out removes the widget (standard flow)
        setResult(RESULT_CANCELED)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            WidgeonTheme(themeMode = widgeon.prefs.themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigContent(
                        appWidgetId = appWidgetId,
                        onPick = { design -> bindAndFinish(appWidgetId, design) },
                        onOpenApp = {
                            startActivity(
                                Intent(this, com.widgeon.app.MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun bindAndFinish(appWidgetId: Int, design: WidgetDesign) {
        widgeon.prefs.bindWidget(appWidgetId, design.id)
        WidgetHost.updateWidget(this, appWidgetId)
        WidgetUpdater.refresh(this)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}

@Composable
private fun ConfigContent(
    appWidgetId: Int,
    onPick: (WidgetDesign) -> Unit,
    onOpenApp: () -> Unit,
) {
    var designs by remember { mutableStateOf<List<WidgetDesign>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        designs = context.widgeon.designRepo.list()
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Text(
            "Attach a Widgeon design",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "This home-screen widget will display the design you pick. You can swap it any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )

        if (!loading && designs.isEmpty()) {
            EmptyDesigns(onOpenApp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(designs, key = { it.id }) { design ->
                    DesignPickCard(design = design, onClick = { onPick(design) })
                }
            }
        }
    }
}

@Composable
private fun DesignPickCard(design: WidgetDesign, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(design.id, design.version) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(design.id, design.version) {
        launch {
            bitmap = WidgetHost.renderDesign(
                context, design,
                (design.cols * 70 - 30).coerceAtLeast(48),
                (design.rows * 70 - 30).coerceAtLeast(48),
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = design.name,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            design.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            design.sizeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDesigns(onOpenApp: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text("No designs yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Create your first AI widget inside Widgeon, then come back and attach it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(
            onClick = onOpenApp,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Open Widgeon")
        }
    }
}

/** Small local click modifier to avoid importing the shared components here. */
