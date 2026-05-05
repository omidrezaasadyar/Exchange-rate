package ir.exchangerate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.exchangerate.app.data.RatesState
import ir.exchangerate.app.data.SourceRates
import ir.exchangerate.app.ui.RatesViewModel
import ir.exchangerate.app.ui.components.AboutDialog
import ir.exchangerate.app.ui.components.LiveBadge
import ir.exchangerate.app.ui.components.SourceSection

@Composable
fun RatesScreen(
    onOpenSettings: () -> Unit,
    viewModel: RatesViewModel = viewModel(factory = RatesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tick by viewModel.tick.collectAsStateWithLifecycle()
    val unit by viewModel.displayUnit.collectAsStateWithLifecycle()

    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.startPolling() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            val anyFetching = (state as? RatesState.Loaded)
                ?.sources?.any { it.isFetching } == true
            Header(
                isRefreshing = anyFetching,
                onRefresh = viewModel::manualRefresh,
                onSettings = onOpenSettings,
                onAbout = { showAbout = true },
            )

            when (val s = state) {
                RatesState.Loading -> LoadingBlock()
                is RatesState.Error -> ErrorBlock(message = s.cause.message ?: "خطا", onRetry = viewModel::manualRefresh)
                is RatesState.Loaded -> SectionsList(
                    sources = s.sources,
                    unit = unit,
                    nowMillis = tick.nowMillis,
                )
            }
        }

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }
    }
}

@Composable
private fun Header(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "قیمت لحظه‌ای ارز",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            LiveBadge(
                text = if (isRefreshing) "در حال دریافت لحظه‌ای" else "زنده — ۳ منبع",
                color = Color(0xFF16A34A),
                pulsing = true,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HeaderIconButton(
                icon = Icons.Filled.Info,
                description = "درباره برنامه",
                onClick = onAbout,
            )
            HeaderIconButton(
                icon = Icons.Filled.Refresh,
                description = "بروزرسانی",
                onClick = onRefresh,
            )
            HeaderIconButton(
                icon = Icons.Filled.Settings,
                description = "تنظیمات",
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "در حال دریافت قیمت‌های لحظه‌ای…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "هیچ منبعی پاسخ نداد",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) {
                    Text("تلاش مجدد")
                }
            }
        }
    }
}

@Composable
private fun SectionsList(
    sources: List<SourceRates>,
    unit: ir.exchangerate.app.data.DisplayUnit,
    nowMillis: Long,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(sources, key = { it.source.name }) { sectionData ->
            SourceSection(
                sourceRates = sectionData,
                unit = unit,
                nowMillis = nowMillis,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
