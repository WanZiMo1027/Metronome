package com.yuntian.metronome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val InstrumentColorScheme = darkColorScheme(
    primary = InstrumentAmber,
    onPrimary = InstrumentBackground,
    primaryContainer = InstrumentAmberMuted,
    onPrimaryContainer = InstrumentIvory,
    secondary = InstrumentMuted,
    onSecondary = InstrumentBackground,
    secondaryContainer = InstrumentSurfaceHigh,
    onSecondaryContainer = InstrumentIvory,
    tertiary = InstrumentAmber,
    onTertiary = InstrumentBackground,
    tertiaryContainer = InstrumentAmberMuted,
    onTertiaryContainer = InstrumentIvory,
    background = InstrumentBackground,
    onBackground = InstrumentIvory,
    surface = InstrumentSurface,
    onSurface = InstrumentIvory,
    surfaceVariant = InstrumentSurfaceHigh,
    onSurfaceVariant = InstrumentMuted,
    surfaceContainerLowest = InstrumentBackground,
    surfaceContainerLow = InstrumentSurface,
    surfaceContainer = InstrumentSurface,
    surfaceContainerHigh = InstrumentSurfaceHigh,
    surfaceContainerHighest = InstrumentSurfaceHigh,
    outline = InstrumentOutline,
    error = InstrumentError,
    onError = InstrumentBackground,
)

@Composable
fun MetronomeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = InstrumentColorScheme,
        typography = Typography,
        content = content
    )
}
