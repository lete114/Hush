package cn.imlete.apps.hush.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Design tokens —— UI_SPEC §1 palette and type scale (frozen)
// ---------------------------------------------------------------------------

val Ink = Color(0xFF12101A)
val Surface = Color(0xFF1A1725)
val SurfaceHigh = Color(0xFF232030)
val Amber = Color(0xFFFFB74D)
val AmberDeep = Color(0xFFE08A2E)
val AmberSoft = Color(0x33FFB74D)
val AmberGlow = Color(0x1AFFB74D)
val Muted = Color(0xFF6B6880)
val Track = Color(0xFF2A2735)
val OnInk = Color(0xFFE8E6F0)

val NightTop = Color(0xFF221C3A)
val NightMid = Color(0xFF151220)
val NightBot = Color(0xFF0A0810)

val Aurora = Color(0xFFAD9EFF)

// Typography —— Theme.kt:54-78. At fontScale=1, sp equals the prototype's px.
val DisplaySize = 56.sp
val HeadlineSize = 22.sp
val TitleSize = 16.sp
val LabelSize = 15.sp
val BodySize = 14.sp
val SmallSize = 12.sp

// ---------------------------------------------------------------------------
// Aurora background —— replicates Modifier.nightWash() from HomeScreen.kt:268-317
// Top bandW=w, center y=-0.1875h, radius w; bottom bandW=1.56w, center y=1.15625h, radius .78w
// ---------------------------------------------------------------------------

fun Modifier.nightWash(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(0f to NightTop, 0.45f to NightMid, 1f to NightBot))

    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Aurora.copy(alpha = 0.26f),
                1f to Aurora.copy(alpha = 0f),
            ),
            center = Offset(w / 2f, -0.1875f * h),
            radius = w,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Aurora.copy(alpha = 0.221f),
                1f to Aurora.copy(alpha = 0f),
            ),
            center = Offset(w / 2f, 1.15625f * h),
            radius = 0.78f * w,
        ),
    )
}

@Composable
fun HushTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Surface,
            onBackground = OnInk,
            onSurface = OnInk,
            surfaceVariant = SurfaceHigh,
            primary = Amber,
            onPrimary = Ink,
            secondary = Aurora,
            outline = Muted,
        ),
        content = content,
    )
}

@Suppress("unused")
val BodyWeight = FontWeight.Normal
