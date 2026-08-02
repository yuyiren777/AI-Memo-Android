package cn.aimemo.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF247B76),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEEB),
    onPrimaryContainer = Color(0xFF0B3C39),
    secondary = Color(0xFF53656D),
    secondaryContainer = Color(0xFFDDE5E8),
    tertiary = Color(0xFFC26845),
    error = Color(0xFFB33F43),
    background = Color(0xFFF6F8F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDEE),
    outline = Color(0xFF748487),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF74D2CA),
    onPrimary = Color(0xFF083B38),
    primaryContainer = Color(0xFF155D58),
    onPrimaryContainer = Color(0xFFD6F5F1),
    secondary = Color(0xFFBBC9CD),
    secondaryContainer = Color(0xFF34464B),
    tertiary = Color(0xFFFFB28D),
    error = Color(0xFFFFB3B5),
    background = Color(0xFF11191B),
    surface = Color(0xFF182225),
    surfaceVariant = Color(0xFF273336),
    outline = Color(0xFF819194),
)

val ImportantColor = Color(0xFFD69A20)
val UrgentColor = Color(0xFFD25050)
val NormalColor = Color(0xFF3BAE68)

@Composable
fun AiMemoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

