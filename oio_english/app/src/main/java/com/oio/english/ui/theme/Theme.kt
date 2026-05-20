package com.oio.english.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val OioColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = CardWhite,
    primaryContainer = CoralLight,
    secondary = Lavender,
    onSecondary = CardWhite,
    secondaryContainer = LavenderLight,
    tertiary = Mint,
    onTertiary = CardWhite,
    tertiaryContainer = MintLight,
    background = Cream,
    onBackground = WarmDark,
    surface = WarmWhite,
    onSurface = WarmDark,
    surfaceVariant = Cream,
    onSurfaceVariant = WarmGray,
    outline = WarmGrayLight,
    error = ErrorRed,
    onError = CardWhite
)

@Composable
fun OioEnglishTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = OioColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OioTypography,
        content = content
    )
}
