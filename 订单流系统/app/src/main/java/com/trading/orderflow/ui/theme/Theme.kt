package com.trading.orderflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00C853),      // 绿色 - 涨
    secondary = Color(0xFFFF1744),    // 红色 - 跌
    tertiary = Color(0xFFFFB300),     // 黄色 - 警告
    background = Color(0xFF121212),   // 深黑背景
    surface = Color(0xFF1E1E1E),      // 卡片背景
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFE0E0E0), // 主文字
    onSurface = Color(0xFFE0E0E0),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00C853),
    secondary = Color(0xFFFF1744),
    tertiary = Color(0xFFFFB300),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun OrderFlowTheme(
    darkTheme: Boolean = true, // 默认使用黑暗主题
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}