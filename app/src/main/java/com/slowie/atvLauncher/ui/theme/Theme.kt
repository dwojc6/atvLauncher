package com.slowie.atvLauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ATLauncherTheme(
    isInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tvColorScheme = if (isInDarkTheme) {
        tvDarkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80
        )
    } else {
        tvLightColorScheme(
            primary = Purple40,
            secondary = PurpleGrey40,
            tertiary = Pink40
        )
    }
    val materialColorScheme = if (isInDarkTheme) {
        androidx.compose.material3.darkColorScheme()
    } else {
        androidx.compose.material3.lightColorScheme()
    }

    androidx.compose.material3.MaterialTheme(colorScheme = materialColorScheme) {
        TvMaterialTheme(
            colorScheme = tvColorScheme,
            typography = Typography,
            content = content
        )
    }
}
