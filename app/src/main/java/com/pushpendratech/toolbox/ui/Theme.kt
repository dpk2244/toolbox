package com.pushpendratech.toolbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White, secondary = Color(0xFF7C3AED),
    background = Color(0xFFF8F8FC), surface = Color.White, onSurface = Color(0xFF191927), onBackground = Color(0xFF191927)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC), onPrimary = Color(0xFF1E1B4B), secondary = Color(0xFFC4B5FD),
    background = Color(0xFF12121A), surface = Color(0xFF1C1C28), onSurface = Color(0xFFF1F1F6), onBackground = Color(0xFFF1F1F6)
)

@Composable
fun ToolBoxTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
