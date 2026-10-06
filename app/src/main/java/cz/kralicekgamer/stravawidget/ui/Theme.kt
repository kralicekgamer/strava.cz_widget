package cz.kralicekgamer.stravawidget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Vlastní vzhled aplikace v zelené z ikony. Widget na ploše barvy bere ze systému.
// Pozadí musí odpovídat barvě `canvas` v res/values(-night).
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EBE3),
    onPrimaryContainer = Color(0xFF0C3A2F),
    background = Color(0xFFF3F6F4),
    onBackground = Color(0xFF17201C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17201C),
    surfaceVariant = Color(0xFFE9EEEB),
    onSurfaceVariant = Color(0xFF56635D),
    outline = Color(0xFF8A9690),
    outlineVariant = Color(0xFFD9E0DC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE9E7),
    onErrorContainer = Color(0xFF5F1410),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD1B9),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF17493D),
    onPrimaryContainer = Color(0xFFBDEBDD),
    background = Color(0xFF101513),
    onBackground = Color(0xFFE3EAE6),
    surface = Color(0xFF18201D),
    onSurface = Color(0xFFE3EAE6),
    surfaceVariant = Color(0xFF222C28),
    onSurfaceVariant = Color(0xFF9FB0A8),
    outline = Color(0xFF6F7F78),
    outlineVariant = Color(0xFF2E3A35),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF3B1513),
    onErrorContainer = Color(0xFFF9DEDC),
)

// Tři řezy písma: 400 text, 500 popisky a tlačítka, 600 nadpis obrazovky.
private val AppTypography = Typography().run {
    copy(
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun StravaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
