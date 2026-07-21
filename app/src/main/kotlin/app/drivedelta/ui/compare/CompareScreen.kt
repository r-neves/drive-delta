package app.drivedelta.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.domain.model.ComparisonWinner
import app.drivedelta.domain.model.SegmentComparison
import app.drivedelta.ui.theme.DdDeltaFaster
import app.drivedelta.ui.theme.DdError
import app.drivedelta.ui.theme.DdTextTertiary
import app.drivedelta.ui.theme.LocalDdTokens
import app.drivedelta.ui.theme.LocalDdType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalDdTokens.current

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(tokens.screenPadding)) {
            when {
                state.loading -> Text(stringResource(R.string.compare_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.candidates.isEmpty() -> Text(
                    stringResource(R.string.compare_no_matches),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        stringResource(R.string.compare_legend),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeltaBarChart(state.comparisons, Modifier.fillMaxWidth().height(160.dp).padding(vertical = tokens.spaceMd))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
                        items(state.comparisons) { ComparisonRow(it) }
                    }
                }
            }
        }
    }
}

/** Diverging bar chart: one bar per aligned segment; A−B delta, green below (A faster), red above. */
@Composable
private fun DeltaBarChart(comparisons: List<SegmentComparison>, modifier: Modifier) {
    val deltas = comparisons.map { it.deltaMs ?: 0L }
    val maxAbs = max(1L, deltas.maxOfOrNull { abs(it) } ?: 1L)
    Canvas(modifier) {
        if (deltas.isEmpty()) return@Canvas
        val midY = size.height / 2f
        val slot = size.width / deltas.size
        val barW = slot * 0.6f
        deltas.forEachIndexed { i, delta ->
            val h = (abs(delta).toFloat() / maxAbs) * (size.height / 2f - 4f)
            val x = i * slot + (slot - barW) / 2f
            val faster = delta < 0
            val top = if (faster) midY else midY - h
            drawRect(
                color = if (faster) DdDeltaFaster else DdError,
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barW, h),
            )
        }
        drawLine(
            color = Color(0x33FFFFFF),
            start = androidx.compose.ui.geometry.Offset(0f, midY),
            end = androidx.compose.ui.geometry.Offset(size.width, midY),
            strokeWidth = 2f,
        )
    }
}

@Composable
private fun ComparisonRow(c: SegmentComparison) {
    val tokens = LocalDdTokens.current
    val ddType = LocalDdType.current
    Row(Modifier.fillMaxWidth().padding(vertical = tokens.spaceSm), verticalAlignment = Alignment.CenterVertically) {
        Text(c.roadName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${formatTime(c.tripADurationMs)}  ·  ${formatTime(c.tripBDurationMs)}",
                style = ddType.numericMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val delta = c.deltaMs
            if (delta != null) {
                val faster = delta < 0
                Text(
                    (if (faster) "▾ −" else "▴ +") + formatTime(abs(delta)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (faster) DdDeltaFaster else DdError,
                )
            } else {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = DdTextTertiary)
            }
        }
    }
}

private fun formatTime(ms: Long?): String {
    if (ms == null) return "—"
    val totalSec = ms / 1000
    return String.format(Locale.US, "%d:%02d.%d", totalSec / 60, totalSec % 60, (ms % 1000) / 100)
}
