package es.miscanales

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** La paleta oscura de YouTube, con su rojo. */
object Colors {
    val bg = Color(0xFF0F0F0F)
    val bg2 = Color(0xFF272727)
    val bg3 = Color(0xFF3F3F3F)
    val text = Color(0xFFF1F1F1)
    val muted = Color(0xFFAAAAAA)
    val accent = Color(0xFFFF0033)
    val link = Color(0xFF3EA6FF)
}

object Type {
    val pageTitle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Colors.text)
    val title = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = Colors.text)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = Colors.text)
    val sub = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = Colors.muted)
    val badge = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
fun MisCanalesTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Colors.text,
        onPrimary = Colors.bg,
        background = Colors.bg,
        onBackground = Colors.text,
        surface = Colors.bg,
        onSurface = Colors.text,
        surfaceVariant = Colors.bg2,
        onSurfaceVariant = Colors.muted,
        inverseSurface = Colors.text,
        inverseOnSurface = Colors.bg,
        secondaryContainer = Colors.bg2,
        onSecondaryContainer = Colors.text,
        border = Colors.text,
    ),
    content = content,
)
