package app.drivedelta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Non-Material design tokens (spacing, shape, elevation) from design/tokens.md §4–6.
 * Material owns colour + type; these cover the rest. Reach them via [LocalDdTokens].
 */
data class DdTokens(
    // Spacing (§4)
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,
    val screenPadding: Dp = 20.dp,
    // Shape (§5)
    val radiusSm: Dp = 10.dp,
    val radiusInput: Dp = 14.dp,
    val radiusMd: Dp = 16.dp,
    val radiusCard: Dp = 22.dp,
    val radiusLg: Dp = 28.dp,
    val radiusHudPanel: Dp = 30.dp,
    // Elevation (§6) — design uses hairline borders + soft shadows, not tonal elevation
    val elevationCard: Dp = 0.dp,
    val elevationSheet: Dp = 16.dp,
)

val LocalDdTokens = staticCompositionLocalOf { DdTokens() }
val LocalDdType = staticCompositionLocalOf { DdType }

/**
 * The single dark colour scheme. No light scheme is wired — the POC is dark-only (see
 * design/tokens.md §9). surfaceTint is transparent so Compose does not overlay a tonal-elevation
 * tint on surfaces; the design expresses depth with borders and shadows instead.
 */
private val DriveDeltaDarkColors = darkColorScheme(
    primary = DdPrimary,
    onPrimary = DdOnPrimary,
    primaryContainer = DdPrimary,
    onPrimaryContainer = DdOnPrimary,
    secondary = DdSecondary,
    onSecondary = DdOnPrimary,
    background = DdBackground,
    onBackground = DdOnSurface,
    surface = DdSurface,
    onSurface = DdOnSurface,
    surfaceVariant = DdSurfaceVariant,
    onSurfaceVariant = DdOnSurfaceVariant,
    surfaceContainer = DdSurfaceVariant,
    surfaceContainerHigh = DdSurfaceElevated,
    surfaceContainerLow = DdSurfaceSheet,
    outline = DdOutline,
    outlineVariant = DdOutline,
    error = DdError,
    onError = DdOnPrimary,
    surfaceTint = Color.Transparent,
)

@Composable
fun DriveDeltaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DriveDeltaDarkColors,
        typography = DriveDeltaTypography,
        content = content,
    )
}
