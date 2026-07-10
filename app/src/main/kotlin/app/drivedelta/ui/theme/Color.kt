package app.drivedelta.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DriveDelta colour tokens — dark theme only (the designed default; see design/tokens.md §2.1).
 * Values are taken verbatim from the Claude Design export. Light theme is out of scope for the POC.
 */

// --- Core roles ---
val DdPrimary = Color(0xFF5B8DEF)
val DdOnPrimary = Color(0xFFFFFFFF)
val DdSecondary = Color(0xFF82A8F4)
val DdBackground = Color(0xFF0A0B0D)
val DdSurface = Color(0xFF14161A)
val DdOnSurface = Color(0xFFF2F4F7)
val DdOnSurfaceVariant = Color(0xFF8A9099)
val DdOutline = Color(0x14FFFFFF) // white @ 8%
val DdError = Color(0xFFFF556A)
val DdSuccess = Color(0xFF37D67A)

// --- Delta / motorsport signal colours ---
val DdDeltaFaster = Color(0xFF37D67A) // green — ahead of best
val DdDeltaSlower = Color(0xFFFF556A) // red — behind best
val DdPurpleSector = Color(0xFFB388FF) // violet — new personal best

// --- Extended surfaces ---
val DdSurfaceSheet = Color(0xFF101216)
val DdSurfaceVariant = Color(0xFF16181C)
val DdSurfaceElevated = Color(0xFF1B1E24)
val DdNavBackground = Color(0xFF0C0E11)
val DdSegmentActive = Color(0xFF23262C)
val DdMapRoads = Color(0xFF15171B)
val DdMapBlocks = Color(0xFF0E1014)

// --- Text ramp (brightest → dimmest) ---
val DdTextBright = Color(0xFFE8EAED)
val DdTextSecondary = Color(0xFF8A9099)
val DdTextTertiary = Color(0xFF7E858F)
val DdTextDim = Color(0xFF6B7178)

// --- Purple-sector row (personal-best split) ---
val DdPurpleRowText = Color(0xFFC8B3FF)
val DdPurpleRowMuted = Color(0xFF8F83B0)
val DdPurpleRowBg = Color(0x17B388FF) // violet @ ~9%
val DdPurpleRowBorder = Color(0x40B388FF) // violet @ 25%

// --- Amber (map "steady" band, GPS searching) ---
val DdAmber = Color(0xFFF0B24E)

// --- Fuel-type badge colours (design/tokens.md §2.3) ---
// Note: Electric == success and Diesel == primary by design. Fuel badges must always show a text
// label and never signal by colour alone.
val DdFuelElectric = Color(0xFF37D67A)
val DdFuelDiesel = Color(0xFF5B8DEF)
val DdFuelPetrol = Color(0xFFF0913E)
val DdFuelHybrid = Color(0xFFF2C94C)
val DdFuelLpg = Color(0xFF8A9099)
