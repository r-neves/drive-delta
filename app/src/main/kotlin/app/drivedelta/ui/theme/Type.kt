package app.drivedelta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.drivedelta.R

/**
 * DriveDelta typography — Geist (UI) + Geist Mono (numerics), per design/tokens.md §3.
 * Every mono style sets fontFeatureSettings = "tnum" so digits are tabular and don't jitter as
 * live values tick.
 */

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
)

private const val TNUM = "tnum"

/**
 * Material 3 type scale mapped to the design roles. HUD-specific numeric styles that don't map to
 * a Material slot live in [DdTypeStyles] below.
 */
val DriveDeltaTypography = Typography(
    // HUD speed readout — Geist Mono 88 / 600, ls -3, line-height 0.82
    displayLarge = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 88.sp,
        lineHeight = 72.sp,
        letterSpacing = (-3).sp,
        fontFeatureSettings = TNUM,
    ),
    // HUD segment time — Geist Mono 34 / 600, ls -1
    displayMedium = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TNUM,
    ),
    // Screen titles / greeting — Geist 24 / 600
    headlineMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    // App-bar titles — Geist 18 / 600
    titleLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
    ),
    // Card titles, road names — Geist 15 / 500
    titleMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // Body / field values — Geist 16 / 400
    bodyLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    // Secondary text — Geist 14 / 400
    bodyMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Buttons — Geist 15 / 600
    labelLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // Uppercase eyebrows / section labels — Geist 11 / 600, ls +1.5
    labelSmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp,
    ),
)

/**
 * DriveDelta-specific numeric styles that have no Material 3 equivalent. Access via
 * [app.drivedelta.ui.theme.LocalDdType] (wired in Theme.kt) or reference [DdType] directly.
 */
data class DdTypeStyles(
    // Split times / deltas — Geist Mono 16 / 500
    val numericMono: TextStyle = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TNUM,
    ),
    // HUD delta value — Geist Mono 21 / 600, ls -0.5
    val deltaValue: TextStyle = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TNUM,
    ),
    // HUD elapsed / distance — Geist Mono 24 / 500
    val statValue: TextStyle = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TNUM,
    ),
)

val DdType = DdTypeStyles()
