package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryNeonViolet,
    secondary = SecondaryOrchid,
    tertiary = TertiaryCyan,
    background = BackgroundDeepSpace,
    surface = SurfaceObsidian,
    onPrimary = TextPrimaryWhite,
    onSecondary = TextPrimaryWhite,
    onTertiary = TextPrimaryWhite,
    onBackground = TextPrimaryWhite,
    onSurface = TextPrimaryWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryNeonViolet,
    secondary = SecondaryOrchid,
    tertiary = TertiaryCyan,
    background = TextPrimaryWhite,
    surface = TextPrimaryWhite,
    onPrimary = TextPrimaryWhite,
    onSecondary = TextPrimaryWhite,
    onTertiary = TextPrimaryWhite,
    onBackground = BackgroundDeepSpace,
    onSurface = BackgroundDeepSpace
  )

private val HighContrastColorScheme =
  darkColorScheme(
    primary = Color(0xFFFFFF00), // High Contrast Yellow
    secondary = Color(0xFF00FFFF), // Cyan
    tertiary = Color(0xFFFF00FF), // Magenta
    background = Color(0xFF000000), // Pure Black
    surface = Color(0xFF000000), // Pure Black
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF), // Pure White
    onSurface = Color(0xFFFFFFFF)
  )

@Composable
fun MyApplicationTheme(
  themeMode: String = "dark",
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      themeMode == "high_contrast" -> HighContrastColorScheme
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeMode != "high_contrast" -> {
        val context = LocalContext.current
        if (themeMode == "dark") dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      themeMode == "dark" -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
