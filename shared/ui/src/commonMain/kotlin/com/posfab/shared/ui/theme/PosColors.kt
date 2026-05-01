package com.posfab.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PosRed900 = Color(0xFF7B1A0F)
val PosRed700 = Color(0xFFB5290E)
val PosRed500 = Color(0xFFD94A2B)
val PosRed100 = Color(0xFFFFDAD6)

val PosAmber800 = Color(0xFF5C3D00)
val PosAmber600 = Color(0xFF8B5E00)
val PosAmber200 = Color(0xFFFFDFA0)
val PosAmber50 = Color(0xFFFFF8E1)

val PosGreen800 = Color(0xFF1B4D1F)
val PosGreen600 = Color(0xFF2E7D32)
val PosGreen200 = Color(0xFFA5D6A7)

val PosNeutral950 = Color(0xFF0F0D0C)
val PosNeutral900 = Color(0xFF1A1714)
val PosNeutral800 = Color(0xFF2A2522)
val PosNeutral700 = Color(0xFF3D3733)
val PosNeutral300 = Color(0xFFB0A89E)
val PosNeutral100 = Color(0xFFF0EBE6)

val PosError = Color(0xFFFF6B6B)

val PosDarkColorScheme = darkColorScheme(
    primary = PosRed500,
    onPrimary = Color.White,
    primaryContainer = PosRed900,
    onPrimaryContainer = PosRed100,
    secondary = PosAmber200,
    onSecondary = PosAmber800,
    secondaryContainer = PosAmber800,
    onSecondaryContainer = PosAmber200,
    tertiary = PosGreen200,
    onTertiary = PosGreen800,
    tertiaryContainer = PosGreen800,
    onTertiaryContainer = PosGreen200,
    error = PosError,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = PosNeutral950,
    onBackground = PosNeutral100,
    surface = PosNeutral900,
    onSurface = PosNeutral100,
    surfaceVariant = PosNeutral800,
    onSurfaceVariant = PosNeutral300,
    surfaceDim = PosNeutral950,
    surfaceBright = Color(0xFF3A3531),
    surfaceContainerLowest = Color(0xFF0A0807),
    surfaceContainerLow = PosNeutral900,
    surfaceContainer = Color(0xFF221E1B),
    surfaceContainerHigh = PosNeutral800,
    surfaceContainerHighest = Color(0xFF35302C),
    outline = PosNeutral700,
    outlineVariant = Color(0xFF524A46),
)

val PosLightColorScheme = lightColorScheme(
    primary = PosRed700,
    onPrimary = Color.White,
    primaryContainer = PosRed100,
    onPrimaryContainer = PosRed900,
    secondary = PosAmber600,
    onSecondary = Color.White,
    secondaryContainer = PosAmber50,
    onSecondaryContainer = PosAmber800,
    tertiary = PosGreen600,
    onTertiary = Color.White,
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF1A0A07),
    surface = Color.White,
    onSurface = Color(0xFF1A0A07),
    surfaceVariant = Color(0xFFF5EDEA),
    onSurfaceVariant = Color(0xFF534340),
)
