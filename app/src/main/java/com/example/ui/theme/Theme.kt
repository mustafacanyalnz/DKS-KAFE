package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DksLightColorScheme = lightColorScheme(
    primary = RoastedCopper,
    onPrimary = Color.White,
    primaryContainer = OccupiedContainer,
    onPrimaryContainer = EspressoInk,
    secondary = AvailableSage,
    onSecondary = Color.White,
    secondaryContainer = AvailableContainer,
    onSecondaryContainer = EspressoInk,
    tertiary = StageGold,
    onTertiary = EspressoInk,
    background = CreamCanvas,
    onBackground = EspressoInk,
    surface = IvoryCard,
    onSurface = EspressoInk,
    surfaceVariant = LatteSurface,
    onSurfaceVariant = MutedCocoa,
    outline = WarmBorder,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert
)

private val DksDarkColorScheme = darkColorScheme(
    primary = NightCopperPrimary,
    onPrimary = NightStageBg,
    primaryContainer = NightOccupiedContainer,
    onPrimaryContainer = NightWarmInk,
    secondary = Color(0xFF52B788),
    onSecondary = NightStageBg,
    secondaryContainer = NightAvailableContainer,
    onSecondaryContainer = NightWarmInk,
    tertiary = StageGold,
    onTertiary = NightStageBg,
    background = NightStageBg,
    onBackground = NightWarmInk,
    surface = NightEspressoSurface,
    onSurface = NightWarmInk,
    surfaceVariant = NightEspressoCard,
    onSurfaceVariant = NightMutedInk,
    outline = Color(0xFF44372F),
    error = Color(0xFFF28B82),
    onError = NightStageBg,
    errorContainer = Color(0xFF5C1B16),
    onErrorContainer = Color(0xFFFCDAD7)
)

private val DksShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DksSahneKafeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DksDarkColorScheme else DksLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DksTypography,
        shapes = DksShapes,
        content = content
    )
}
