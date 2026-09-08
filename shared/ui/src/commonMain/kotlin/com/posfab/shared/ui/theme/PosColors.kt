package com.posfab.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Primary: Amber-Gold (money, warmth, trust) ─────────────────────────────
val PosAmberPrimary = Color(0xFFC4882A)
val PosAmberOnPrimary = Color(0xFF1A0F00)
val PosAmberPrimaryContainer = Color(0xFF3D2A0A)
val PosAmberOnPrimaryContainer = Color(0xFFFFD8A8)

// ─── Secondary: Muted sienna ─────────────────────────────────────────────────
val PosSienna = Color(0xFFE8C88A)
val PosSiennaOn = Color(0xFF1A0F00)
val PosSiennaContainer = Color(0xFF2E2010)
val PosSiennaOnContainer = Color(0xFFE8C88A)

// ─── Tertiary: Forest green (success, active) ────────────────────────────────
val PosGreen = Color(0xFFB0D4A8)
val PosGreenOn = Color(0xFF00180A)
val PosGreenContainer = Color(0xFF1A3A1A)
val PosGreenOnContainer = Color(0xFFB0D4A8)

// ─── Error: Warm rose-red (distinct from primary amber) ──────────────────────
val PosError = Color(0xFFCF6679)
val PosErrorOn = Color(0xFF1A0008)
val PosErrorContainer = Color(0xFF3D0015)
val PosErrorOnContainer = Color(0xFFFFB3C0)

// ─── Neutrals: Warm dark tones with adequate step separation ─────────────────
val PosNeutral950 = Color(0xFF0F0D0C)    // background
val PosNeutral900 = Color(0xFF1C1917)    // surface (cards, panels)
val PosNeutral850 = Color(0xFF262220)    // surfaceVariant, surfaceContainer
val PosNeutral800 = Color(0xFF312C29)    // surfaceContainerHigh
val PosNeutral750 = Color(0xFF3D3733)    // surfaceContainerHighest

// ─── Text ────────────────────────────────────────────────────────────────────
val PosTextPrimary = Color(0xFFF0E8DF)   // warm off-white, no halation
val PosTextSecondary = Color(0xFFC4B8AE) // secondary text

// ─── Outlines: WCAG 1.4.11 compliant (3:1+ against surface) ─────────────────
val PosOutline = Color(0xFF6B6560)       // input borders, dividers
val PosOutlineVariant = Color(0xFF4D4845) // subtle dividers

val PosDarkColorScheme = darkColorScheme(
    // Primary — amber-gold
    primary = PosAmberPrimary,
    onPrimary = PosAmberOnPrimary,
    primaryContainer = PosAmberPrimaryContainer,
    onPrimaryContainer = PosAmberOnPrimaryContainer,

    // Secondary
    secondary = PosSienna,
    onSecondary = PosSiennaOn,
    secondaryContainer = PosSiennaContainer,
    onSecondaryContainer = PosSiennaOnContainer,

    // Tertiary
    tertiary = PosGreen,
    onTertiary = PosGreenOn,
    tertiaryContainer = PosGreenContainer,
    onTertiaryContainer = PosGreenOnContainer,

    // Error
    error = PosError,
    onError = PosErrorOn,
    errorContainer = PosErrorContainer,
    onErrorContainer = PosErrorOnContainer,

    // Background & Surface hierarchy
    background = PosNeutral950,
    onBackground = PosTextPrimary,
    surface = PosNeutral900,
    onSurface = PosTextPrimary,
    surfaceVariant = PosNeutral850,
    onSurfaceVariant = PosTextSecondary,
    surfaceTint = PosAmberPrimary,

    // Surface container levels (prevents M3 purple defaults)
    surfaceDim = PosNeutral950,
    surfaceBright = Color(0xFF3A3531),
    surfaceContainerLowest = Color(0xFF0A0807),
    surfaceContainerLow = PosNeutral900,
    surfaceContainer = PosNeutral850,
    surfaceContainerHigh = PosNeutral800,
    surfaceContainerHighest = PosNeutral750,

    // Outlines — visible input borders
    outline = PosOutline,
    outlineVariant = PosOutlineVariant,

    // Inverse (snackbars, tooltips)
    inverseSurface = PosTextPrimary,
    inverseOnSurface = PosNeutral900,
    inversePrimary = Color(0xFF8C5A10),

    scrim = Color.Black,
)

val PosLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C5A10),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDFA0),
    onPrimaryContainer = Color(0xFF3D2A0A),
    secondary = Color(0xFF6B5330),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF8E1),
    onSecondaryContainer = Color(0xFF2E2010),
    tertiary = Color(0xFF2E7D32),
    onTertiary = Color.White,
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF1A0A07),
    surface = Color.White,
    onSurface = Color(0xFF1A0A07),
    surfaceVariant = Color(0xFFF5EDEA),
    onSurfaceVariant = Color(0xFF534340),
)
