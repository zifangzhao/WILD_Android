package com.wild.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val WildColors = lightColorScheme(
    primary = Color(0xFF238CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F2FF),
    onPrimaryContainer = Color(0xFF0F4F92),
    secondary = Color(0xFF238CF6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF238CF6),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF8BC2FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6FAFF),
    onTertiaryContainer = Color(0xFF0F4F92),
    background = Color(0xFFF7FAFF),
    onBackground = Color(0xFF142033),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF142033),
    surfaceVariant = Color(0xFFF1F7FF),
    onSurfaceVariant = Color(0xFF4C6A8C),
    outline = Color(0xFFC9DAF0),
    error = Color(0xFFD64545),
)

@Composable
fun WildTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WildColors,
        typography = MaterialTheme.typography.copy(
            displaySmall = TextStyle(
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.8).sp,
            ),
            headlineSmall = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            titleMedium = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            bodyMedium = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            labelLarge = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
        ),
        content = content,
    )
}
