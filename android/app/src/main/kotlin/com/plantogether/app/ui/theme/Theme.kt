package com.plantogether.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 配色取「旅行手账」的方向：暖米色纸底 + 深海水青主色 + 珊瑚色只留给钱。
 *
 * 两条纪律：
 * 1. 珊瑚色（secondary / 价格）只用来标金额，别的地方不上色，否则「贵」这件事就不突出了。
 * 2. 页面底色不用纯白也不用灰，用暖米色；卡片才是白的，这样卡片自己会浮起来，不必靠阴影。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E6E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDF0EB),
    onPrimaryContainer = Color(0xFF00332F),

    secondary = Color(0xFFD2694C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE2D8),
    onSecondaryContainer = Color(0xFF4A1608),

    tertiary = Color(0xFF7C6A92),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE4F5),
    onTertiaryContainer = Color(0xFF2A1F38),

    background = Color(0xFFFFFBF3),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFF2EDE2),
    onSurfaceVariant = Color(0xFF6B6459),
    surfaceContainer = Color(0xFFF7F3EA),
    surfaceContainerHigh = Color(0xFFF2EDE2),
    surfaceContainerHighest = Color(0xFFECE6DA),
    surfaceContainerLow = Color(0xFFFCF9F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),

    outline = Color(0xFFB8B0A1),
    outlineVariant = Color(0xFFE5DED0),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3C9),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFCDF0EB),

    secondary = Color(0xFFFFB49B),
    onSecondary = Color(0xFF4A1608),
    secondaryContainer = Color(0xFF6E2C19),
    onSecondaryContainer = Color(0xFFFFE2D8),

    tertiary = Color(0xFFCFC0E0),
    onTertiary = Color(0xFF2A1F38),
    tertiaryContainer = Color(0xFF443555),
    onTertiaryContainer = Color(0xFFEDE4F5),

    background = Color(0xFF14140F),
    onBackground = Color(0xFFEDE7DC),
    surface = Color(0xFF1C1C16),
    onSurface = Color(0xFFEDE7DC),
    surfaceVariant = Color(0xFF2A2921),
    onSurfaceVariant = Color(0xFFB5AE9F),
    surfaceContainer = Color(0xFF21211A),
    surfaceContainerHigh = Color(0xFF2A2921),
    surfaceContainerHighest = Color(0xFF35342B),
    surfaceContainerLow = Color(0xFF1A1A14),
    surfaceContainerLowest = Color(0xFF0F0F0A),

    outline = Color(0xFF7A7467),
    outlineVariant = Color(0xFF3A382F),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 圆角统一放大：模板里几乎看不到小尖角，卡片都在 20dp 上下。 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 排版做了两件事：
 * 1. 标题加大加粗（模板里的标题都很重），让每屏有个「一句话标题」的落点；
 * 2. 正文行高放宽到 1.5 倍左右，中文密排会显得闷。
 */
private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        bodySmall = bodySmall.copy(lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.3.sp),
    )
}

/** 价格用的样式：比正文重，颜色由调用方给（一律 secondary）。 */
val PriceTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    )

@Composable
fun PlanTogetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
