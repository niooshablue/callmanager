@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ======================= قالب ظاهری (iPhone 17 / Galaxy S26 / مدرن) =======================

/** اندازهٔ گوشه‌ها و رفتار فهرست‌ها در هر قالب */
class Look(val style: Int, val card: Dp, val row: Dp, val chip: Dp)

val LocalLook = staticCompositionLocalOf { Look(0, 24.dp, 20.dp, 20.dp) }
val LocalThemed = staticCompositionLocalOf { false }

fun lookFor(style: Int): Look = when (style) {
    1 -> Look(1, 16.dp, 12.dp, 16.dp)
    2 -> Look(2, 28.dp, 24.dp, 22.dp)
    else -> Look(0, 24.dp, 20.dp, 20.dp)
}

/** وضعیت ناوبری سراسری؛ -1 یعنی پیش‌نمایش بسته است، 0 تماس ورودی، 1 در حال مکالمه، 2 شماره‌گیری */
object Nav {
    var callPreview by mutableIntStateOf(-1)
}

private fun iosScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF0A84FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3A6E), onPrimaryContainer = Color(0xFFA9D2FF),
    secondary = Color(0xFF30D158), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFE5E5EA),
    tertiary = Color(0xFFFF9F0A), tertiaryContainer = Color(0xFF3A2A0A), onTertiaryContainer = Color(0xFFFFD79A),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF636366), outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A), errorContainer = Color(0xFF5C1713), onErrorContainer = Color(0xFFFFB4AE)
).copy(surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black, surfaceContainer = Color(0xFF111113),
    surfaceContainerHigh = Color(0xFF1C1C1E), surfaceContainerHighest = Color(0xFF2C2C2E))
else lightColorScheme(
    primary = Color(0xFF007AFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF), onPrimaryContainer = Color(0xFF00346B),
    secondary = Color(0xFF34C759), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFFFF9500), tertiaryContainer = Color(0xFFFFE9C7), onTertiaryContainer = Color(0xFF4A2C00),
    background = Color(0xFFF2F2F7), onBackground = Color.Black,
    surface = Color(0xFFF2F2F7), onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF8A8A8E),
    outline = Color(0xFFAEAEB2), outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF5C0A05)
).copy(surfaceContainerLowest = Color(0xFFF2F2F7), surfaceContainerLow = Color(0xFFF2F2F7), surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFE5E5EA))

private fun galaxyScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF5AA2FF), onPrimary = Color(0xFF00172E),
    primaryContainer = Color(0xFF1B3555), onPrimaryContainer = Color(0xFFCFE3FF),
    secondary = Color(0xFF4CD08A), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF26303D), onSecondaryContainer = Color(0xFFDDE6F2),
    tertiary = Color(0xFFFFB454), tertiaryContainer = Color(0xFF3B2B10), onTertiaryContainer = Color(0xFFFFDDA8),
    background = Color.Black, onBackground = Color(0xFFF2F2F2),
    surface = Color.Black, onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1F1F1F), onSurfaceVariant = Color(0xFFA6A6A6),
    outline = Color(0xFF6E6E6E), outlineVariant = Color(0xFF333333),
    error = Color(0xFFFF6B6B), errorContainer = Color(0xFF5A1F1F), onErrorContainer = Color(0xFFFFC9C9)
).copy(surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A), surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1F1F1F), surfaceContainerHighest = Color(0xFF2B2B2B))
else lightColorScheme(
    primary = Color(0xFF2F82F5), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFE), onPrimaryContainer = Color(0xFF0A2E5C),
    secondary = Color(0xFF1FAE6B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EDF5), onSecondaryContainer = Color(0xFF1B2430),
    tertiary = Color(0xFFF08A00), tertiaryContainer = Color(0xFFFFE8C6), onTertiaryContainer = Color(0xFF462800),
    background = Color(0xFFF4F4F4), onBackground = Color(0xFF101010),
    surface = Color(0xFFF4F4F4), onSurface = Color(0xFF101010),
    surfaceVariant = Color(0xFFE9E9E9), onSurfaceVariant = Color(0xFF6A6A6A),
    outline = Color(0xFFA0A0A0), outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFE5484D), errorContainer = Color(0xFFFFDAD9), onErrorContainer = Color(0xFF5A0D10)
).copy(surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F4F4), surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEAEAEA))

private fun modernScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF8FA6FF), onPrimary = Color(0xFF0F1B5C),
    primaryContainer = Color(0xFF26358F), onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF4FD6AE), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF1F3A36), onSecondaryContainer = Color(0xFFC9F2E6),
    tertiary = Color(0xFFFFB27A), tertiaryContainer = Color(0xFF4A2A12), onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0D1016), onBackground = Color(0xFFE8EBF2),
    surface = Color(0xFF0D1016), onSurface = Color(0xFFE8EBF2),
    surfaceVariant = Color(0xFF222735), onSurfaceVariant = Color(0xFFA0A8B8),
    outline = Color(0xFF6C7587), outlineVariant = Color(0xFF2C3240),
    error = Color(0xFFFF8A8A), errorContainer = Color(0xFF5A1F1F), onErrorContainer = Color(0xFFFFD6D6)
).copy(surfaceContainerLowest = Color(0xFF090B10), surfaceContainerLow = Color(0xFF11141B), surfaceContainer = Color(0xFF151922),
    surfaceContainerHigh = Color(0xFF1A1F29), surfaceContainerHighest = Color(0xFF232937))
else lightColorScheme(
    primary = Color(0xFF3B5BDB), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E7FF), onPrimaryContainer = Color(0xFF14226E),
    secondary = Color(0xFF0CA678), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF5EC), onSecondaryContainer = Color(0xFF053D2E),
    tertiary = Color(0xFFE8590C), tertiaryContainer = Color(0xFFFFE6D5), onTertiaryContainer = Color(0xFF4A1C00),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF14181F),
    surface = Color(0xFFF5F7FB), onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE6EAF3), onSurfaceVariant = Color(0xFF5B6472),
    outline = Color(0xFF9AA3B2), outlineVariant = Color(0xFFDDE2EC),
    error = Color(0xFFD92D3A), errorContainer = Color(0xFFFFDAD9), onErrorContainer = Color(0xFF5A0D10)
).copy(surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FD), surfaceContainer = Color(0xFFF1F4FA),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFE9EDF6))

/** رنگ‌بندی نهایی: قالب پایه + شخصی‌سازی رنگ‌های کاربر */
fun appColorScheme(ctx: Context, dark: Boolean): ColorScheme {
    var cs = when (Prefs.uiStyle) {
        1 -> iosScheme(dark)
        2 -> galaxyScheme(dark)
        else -> if (Prefs.on("dynamicColor") && Build.VERSION.SDK_INT >= 31)
            (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else modernScheme(dark)
    }
    if (Prefs.bg != 0) {
        val b = Color(Prefs.bg)
        cs = cs.copy(background = b, surface = b, surfaceContainerLowest = b, surfaceContainerLow = b, surfaceContainer = b)
    }
    if (Prefs.card != 0) {
        val k = Color(Prefs.card)
        cs = cs.copy(surfaceContainerHigh = k, surfaceContainerHighest = k, surfaceVariant = k)
    }
    val tx: Color? = when {
        Prefs.text != 0 -> Color(Prefs.text)
        Prefs.bg != 0 -> if (Color(Prefs.bg).luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF2F2F2)
        else -> null
    }
    if (tx != null) cs = cs.copy(onBackground = tx, onSurface = tx, onSurfaceVariant = tx.copy(alpha = 0.72f))
    if (Prefs.accent != 0) {
        val a = Color(Prefs.accent)
        cs = cs.copy(primary = a, onPrimary = if (a.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = lerp(a, cs.surface, 0.8f), onPrimaryContainer = a,
            secondaryContainer = lerp(a, cs.surface, 0.85f))
    }
    return cs
}

@Composable
fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
fun AppTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = forceDark ?: when (Prefs.darkMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val cs = appColorScheme(ctx, dark)
    val look = lookFor(Prefs.uiStyle)
    val f = Prefs.family()
    val t = Typography()
    val ty = Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f))
    val shapes = when (Prefs.uiStyle) {
        1 -> Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(22.dp))
        2 -> Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp))
        else -> Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp))
    }
    val d = LocalDensity.current
    val already = LocalThemed.current
    SystemBars(dark)
    CompositionLocalProvider(
        LocalDensity provides (if (already) d else Density(d.density, d.fontScale * Prefs.fontScale)),
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalLook provides look,
        LocalThemed provides true
    ) {
        MaterialTheme(colorScheme = cs, typography = ty, shapes = shapes) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

// ======================= آیکون‌های ترسیمی =======================

private const val HANDSET = "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z"

@Composable
fun Handset(color: Color, dim: Dp, rotation: Float = 0f) {
    val path = remember { PathParser().parsePathString(HANDSET).toPath() }
    Canvas(Modifier.size(dim)) {
        rotate(rotation, pivot = center) {
            scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawPath(path, color) }
        }
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color, w: Float) {
    drawLine(color, from, to, w, StrokeCap.Round)
    val ang = atan2(to.y - from.y, to.x - from.x)
    val hl = w * 3.4f
    for (s in listOf(-1f, 1f)) {
        val a = ang + Math.PI.toFloat() + s * 0.62f
        drawLine(color, to, Offset(to.x + hl * cos(a), to.y + hl * sin(a)), w, StrokeCap.Round)
    }
}

private fun typeColor(type: Int) = when (type) {
    1 -> Color(0xFF1E88E5); 2 -> Color(0xFF2E9F5B); 3 -> Color(0xFFE53935)
    5 -> Color(0xFFFB8C00); 6 -> Color(0xFFD32F2F); else -> Color(0xFF78909C)
}

private fun DrawScope.drawType(type: Int, color: Color, w: Float, sw: Float) {
    when (type) {
        1, 3 -> arrow(Offset(w * 0.69f, w * 0.31f), Offset(w * 0.31f, w * 0.69f), color, sw)
        2 -> arrow(Offset(w * 0.31f, w * 0.69f), Offset(w * 0.69f, w * 0.31f), color, sw)
        5, 6 -> {
            drawCircle(color, radius = w * 0.2f, style = Stroke(sw))
            drawLine(color, Offset(w * 0.36f, w * 0.64f), Offset(w * 0.64f, w * 0.36f), sw, StrokeCap.Round)
        }
        else -> drawCircle(color, radius = w * 0.1f)
    }
    if (type == 3) drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.72f, w * 0.72f))
}

@Composable
fun CallTypeBadge(type: Int, dim: Dp = 42.dp) {
    val color = typeColor(type)
    Canvas(Modifier.size(dim).clip(CircleShape).background(color.copy(alpha = 0.15f))) {
        drawType(type, color, this.size.width, this.size.width * 0.075f)
    }
}

/** آیکون کوچک نوع تماس (بدون پس‌زمینه) برای کنار زمان در فهرست */
@Composable
fun TypeGlyph(type: Int, dim: Dp = 16.dp) {
    val color = typeColor(type)
    Canvas(Modifier.size(dim)) { drawType(type, color, this.size.width, this.size.width * 0.12f) }
}

@Composable
fun Avatar(name: String, dim: Dp = 44.dp) {
    val look = LocalLook.current
    val h = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
    val letter = name.trim().firstOrNull { it.isLetter() }
    val brush = when (look.style) {
        1 -> Brush.verticalGradient(listOf(Color(0xFFA7ACB8), Color(0xFF7F8593)))
        2 -> Brush.linearGradient(listOf(Color.hsv(h, 0.38f, 0.93f), Color.hsv(h, 0.50f, 0.78f)))
        else -> Brush.linearGradient(listOf(Color.hsv(h, 0.55f, 0.90f), Color.hsv((h + 40f) % 360f, 0.70f, 0.68f)))
    }
    Box(Modifier.size(dim).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        if (letter != null) Text(letter.toString().uppercase(), color = Color.White, fontSize = (dim.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(dim * 0.58f))
    }
}

private val AnswerGreen = Color(0xFF2EB85C)
private val DeclineRed = Color(0xFFE5392F)

/** دکمهٔ گرد پاسخ (سبز) یا رد/قطع (قرمز) */
@Composable
fun CallCircleButton(answer: Boolean, label: String, pulse: Boolean = false, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val sc by t.animateFloat(1f, if (pulse) 1.12f else 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "sc")
    val cols = if (answer) listOf(Color(0xFF52E58A), Color(0xFF1B9E4B)) else listOf(Color(0xFFFF7A7A), Color(0xFFC62828))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.size(78.dp)
            .shadow(12.dp, CircleShape, ambientColor = cols[1], spotColor = cols[1])
            .clip(CircleShape).background(Brush.verticalGradient(cols)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            Handset(Color.White, 34.dp, if (answer) 0f else 135f)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp)
    }
}

// ======================= دکمه‌های تماس =======================

@Composable
fun CallPill(modifier: Modifier, title: String, sub: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val look = LocalLook.current
    Button(onClick, modifier.height(58.dp), enabled = enabled, shape = RoundedCornerShape(if (look.style == 2) 24.dp else 29.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AnswerGreen), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Handset(Color.White, 20.dp); Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (sub != null) Text(sub, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** دکمه(های) تماس؛ اگر دو سیم‌کارت باشد برای هر کدام یک دکمه (پیش‌فرض با ★). ظاهر آن از قالب برنامه پیروی می‌کند */
@Composable
fun SimCallRow(number: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ctx = LocalContext.current
    val look = LocalLook.current
    val sims = Repo.sims
    val preferredId = remember(number, sims, Repo.calls) {
        if (number.isBlank()) null else try {
            Db.get(ctx).simChoice(number) ?: Repo.calls.asSequence()
                .filter { norm(it.number) == norm(number) && it.simId.isNotBlank() }
                .maxByOrNull { it.date }?.simId ?: sims.firstOrNull { it.isDefault }?.handle?.id
        } catch (_: Exception) { sims.firstOrNull { it.isDefault }?.handle?.id }
    }
    val on = enabled && number.isNotBlank()
    if (look.style == 1) {
        // iPhone: دکمهٔ گردِ بزرگ
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
            val list = if (sims.size < 2) listOf<Sim?>(sims.firstOrNull()) else sims.sortedByDescending { it.handle.id == preferredId }
            list.forEach { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(70.dp).clip(CircleShape).background(AnswerGreen.copy(alpha = if (on) 1f else 0.35f))
                        .clickable(enabled = on) { placeCall(ctx, number, s?.handle) }, contentAlignment = Alignment.Center) {
                        Handset(Color.White, 30.dp)
                    }
                    if (sims.size > 1 && s != null) Text("SIM " + Jalali.fa(s.index) + if (s.handle.id == preferredId) " ★" else "",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sims.size < 2) CallPill(Modifier.weight(1f), "تماس", null, on) { placeCall(ctx, number, sims.firstOrNull()?.handle) }
        else sims.sortedByDescending { it.handle.id == preferredId }.forEach { s ->
            val preferred = s.handle.id == preferredId
            CallPill(Modifier.weight(1f), "SIM ${Jalali.fa(s.index)}" + if (preferred) " ★" else "",
                if (preferred) "پیشنهادی · ${s.label}" else s.label, on) {
                placeCall(ctx, number, s.handle)
            }
        }
    }
}

@Composable
fun SimChooser() {
    val n = Repo.pickNumber ?: return
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = { Repo.pickNumber = null }, title = { Text("تماس با کدام سیم‌کارت؟") },
        text = {
            Column {
                Repo.sims.forEach { s ->
                    Button({ placeCall(ctx, n, s.handle); Repo.pickNumber = null }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("SIM ${Jalali.fa(s.index)} · ${s.label}")
                    }
                }
            }
        }, confirmButton = {})
}

// ======================= اجزای مشترک =======================

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(look.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Composable
fun OptionChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, n -> FilterChip(i == selected, { onSelect(i) }, label = { Text(n) }) }
    }
}

@Composable
fun Empty(t: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(t, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MenuItemShell(title: String, subtitle: String, onClick: () -> Unit, lead: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(look.row)).clickable(onClick = onClick),
        shape = RoundedCornerShape(look.row), color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            lead()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowLeft, null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, title: String, subtitle: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    MenuItemShell(title, subtitle, onClick) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(if (LocalLook.current.style == 1) 11.dp else 14.dp)).background(tint),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
fun MenuItem(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    MenuItemShell(title, subtitle, onClick) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
    }
}

// ======================= سربرگ صفحه‌ها =======================

@Composable
fun AppHeader(title: String, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val showDate = Prefs.on("headerDate") && !compact
    val date = if (showDate) Jalali.dateOnly(System.currentTimeMillis()) else null
    when (Prefs.header()) {
        1 -> Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = if (compact) 6.dp else 14.dp, bottom = 6.dp)) {
            Text(title, fontSize = if (compact) 22.sp else 34.sp, fontWeight = FontWeight.Bold)
            if (date != null) Text(date, fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        2 -> Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 26.dp).padding(top = if (compact) 10.dp else 30.dp, bottom = 10.dp)) {
            Text(title, fontSize = if (compact) 22.sp else 31.sp, fontWeight = FontWeight.SemiBold)
            if (date != null) Text(date, fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        3 -> Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, Modifier.padding(vertical = 12.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant)
        }
        else -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(lerp(cs.surface, cs.primary, 0.36f), lerp(cs.surface, cs.primary, 0.07f))))
            .drawBehind {
                drawCircle(cs.primary.copy(alpha = 0.10f), radius = size.minDimension * 1.1f, center = Offset(size.width * 0.9f, -size.height * 0.25f))
                drawCircle(cs.secondary.copy(alpha = 0.10f), radius = size.minDimension * 0.7f, center = Offset(size.width * 0.05f, size.height * 1.05f))
            }
            .statusBarsPadding().padding(horizontal = 22.dp, vertical = if (compact) 10.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = if (compact) 20.sp else 27.sp, fontWeight = FontWeight.ExtraBold)
                    if (date != null) Text(date, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Box(Modifier.size(if (compact) 36.dp else 44.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Handset(cs.primary, if (compact) 18.dp else 22.dp)
                }
            }
        }
    }
}

// ======================= منوی پایین =======================

class NavTab(val label: String, val icon: ImageVector)

@Composable
fun AppNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    when (Prefs.nav()) {
        0 -> Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerHigh, shadowElevation = 10.dp, tonalElevation = 2.dp) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { i, t ->
                        val sel = i == selected
                        val bg by animateColorAsState(if (sel) cs.primary else Color.Transparent, label = "nbg")
                        val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, label = "nfg")
                        Row(Modifier.animateContentSize().clip(RoundedCornerShape(26.dp)).background(bg).clickable { onSelect(i) }
                            .padding(horizontal = if (sel) 18.dp else 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(t.icon, t.label, tint = fg, modifier = Modifier.size(22.dp))
                            if (sel) { Spacer(Modifier.width(7.dp)); Text(t.label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        }
                    }
                }
            }
        }
        1 -> Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(34.dp), color = cs.surfaceContainerHigh.copy(alpha = 0.92f),
                shadowElevation = 14.dp, border = BorderStroke(0.6.dp, cs.outlineVariant.copy(alpha = 0.6f))) {
                Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { i, t ->
                        val sel = i == selected
                        val bg by animateColorAsState(if (sel) cs.primary.copy(alpha = 0.14f) else Color.Transparent, label = "ibg")
                        val fg by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, label = "ifg")
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(30.dp)).background(bg).clickable { onSelect(i) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(t.icon, t.label, tint = fg, modifier = Modifier.size(24.dp))
                            Text(t.label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
        2 -> Surface(Modifier.fillMaxWidth(), color = cs.surface) {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
                    tabs.forEachIndexed { i, t ->
                        val sel = i == selected
                        val bg by animateColorAsState(if (sel) cs.primaryContainer else Color.Transparent, label = "gbg")
                        val fg by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, label = "gfg")
                        Column(Modifier.weight(1f).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(62.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
                                Icon(t.icon, t.label, tint = fg, modifier = Modifier.size(23.dp))
                            }
                            Text(t.label, fontSize = 11.sp, color = fg, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                                modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        else -> NavigationBar {
            tabs.forEachIndexed { i, t ->
                NavigationBarItem(selected = i == selected, onClick = { onSelect(i) },
                    icon = { Icon(t.icon, t.label) }, label = { Text(t.label, fontSize = 11.sp) })
            }
        }
    }
}

// ======================= کادر جستجو، فیلتر و ردیف فهرست =======================

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
                autoFocus: Boolean = false, onClose: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) try { fr.requestFocus() } catch (_: Exception) { } }
    TextField(value, onChange, modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(fr), singleLine = true,
        placeholder = { Text(hint, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty() || onClose != null) IconButton({ if (value.isNotEmpty()) onChange("") else onClose?.invoke() }) {
                Icon(Icons.Default.Close, "پاک‌کردن")
            }
        },
        shape = RoundedCornerShape(look.chip + 8.dp),
        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh))
}

@Composable
fun FilterPill(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val isDefault = options.isEmpty() || options.first().second == selected
    val text = if (isDefault) label else options.firstOrNull { it.second == selected }?.first ?: label
    Box {
        Surface(onClick = { expanded = true }, shape = RoundedCornerShape(look.chip),
            color = if (isDefault) cs.surfaceContainerHigh else cs.primaryContainer) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    color = if (isDefault) cs.onSurface else cs.onPrimaryContainer)
                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp), tint = if (isDefault) cs.onSurfaceVariant else cs.onPrimaryContainer)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (name, v) ->
                DropdownMenuItem(text = { Text(name, fontWeight = if (v == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(v); expanded = false })
            }
        }
    }
}

/** پوستهٔ یک ردیف فهرست؛ در iPhone تخت با خط جداکننده، در Galaxy بدون کادر با گوشهٔ گرد، در مدرن کارت */
@Composable
fun ListRowShell(selected: Boolean = false, showDivider: Boolean = true, onClick: () -> Unit, onLong: () -> Unit = {},
                 content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    when (look.style) {
        1 -> Column(Modifier.fillMaxWidth().background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)) {
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, content = content)
            if (showDivider) HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 0.5.dp, color = cs.outlineVariant)
        }
        2 -> Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(RoundedCornerShape(look.row))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, content = content)
        else -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(look.row))
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

// ======================= صفحه‌کلید شماره‌گیر =======================

private val LETTERS = mapOf('1' to "", '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+", '*' to "", '#' to "")

/** style: 0 کلاسیک، 1 دایره‌ای، 2 مینیمال، 3 iPhone، 4 Galaxy */
@Composable
fun Keypad(modifier: Modifier = Modifier, keyHeight: Dp = 58.dp, style: Int = Prefs.dialerStyle,
           onKey: (Char) -> Unit, onLongZero: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val hapticOn = Prefs.on("haptics")
    val letters = Prefs.on("dialLetters")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier) {
            listOf("123", "456", "789", "*0#").forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { ch ->
                        val longAction: (() -> Unit)? = if (ch == '0' && onLongZero != null) ({
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongZero()
                        }) else null
                        val click = Modifier.combinedClickable(
                            onClick = { if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(ch) },
                            onLongClick = longAction)
                        val label = Jalali.fa(ch.toString())
                        val sub = if (letters) LETTERS[ch] ?: "" else ""
                        when (style) {
                            1 -> Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(keyHeight + 8.dp).clip(CircleShape).background(cs.surfaceContainerHigh).then(click),
                                    contentAlignment = Alignment.Center) { Text(label, fontSize = 24.sp) }
                            }
                            2 -> Box(Modifier.weight(1f).height(keyHeight).clip(CircleShape).then(click), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 30.sp, fontWeight = FontWeight.Light)
                            }
                            3 -> Box(Modifier.weight(1f).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(keyHeight + 14.dp).clip(CircleShape).background(cs.surfaceContainerHighest).then(click),
                                    contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(label, fontSize = 31.sp, fontWeight = FontWeight.Normal, lineHeight = 34.sp)
                                        if (sub.isNotEmpty()) Text(sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                                            color = cs.onSurfaceVariant, lineHeight = 10.sp)
                                    }
                                }
                            }
                            4 -> Box(Modifier.weight(1f).height(keyHeight + 6.dp).padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(26.dp)).then(click), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, fontSize = 28.sp, fontWeight = FontWeight.Normal)
                                    if (sub.isNotEmpty()) { Spacer(Modifier.width(5.dp)); Text(sub, fontSize = 10.sp, color = cs.onSurfaceVariant) }
                                }
                            }
                            else -> Box(Modifier.weight(1f).padding(4.dp).height(keyHeight).clip(RoundedCornerShape(30.dp))
                                .background(cs.surfaceContainerHigh).then(click), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= قالب‌های پاسخ به تماس =======================

@Composable
fun SlideToAnswer(label: String, onAnswer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val curAnswer by rememberUpdatedState(onAnswer)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(35.dp)).background(cs.surfaceContainerHigh)) {
            val travel = with(density) { (maxWidth - 70.dp).toPx() }
            var dx by remember { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf(false) }
            val shown by animateFloatAsState(dx, if (dragging) snap<Float>() else spring<Float>(), label = "slide")
            Text(label, Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - (shown / travel).coerceIn(0f, 1f) },
                fontSize = 15.sp, color = cs.onSurfaceVariant)
            Box(Modifier.padding(5.dp).offset { IntOffset(shown.toInt(), 0) }.size(60.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF52E58A), Color(0xFF1B9E4B))))
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false; if (dx >= travel * 0.85f) curAnswer(); dx = 0f },
                        onDragCancel = { dragging = false; dx = 0f }
                    ) { _, amount -> dx = (dx + amount).coerceIn(0f, travel) }
                }, contentAlignment = Alignment.Center) { Handset(Color.White, 28.dp) }
        }
    }
}

/** کشیدن به راست = پاسخ، کشیدن به چپ = رد تماس */
@Composable
fun SlideBoth(onAnswer: () -> Unit, onReject: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val curA by rememberUpdatedState(onAnswer)
    val curR by rememberUpdatedState(onReject)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp)).background(cs.surfaceContainerHigh)) {
            val half = with(density) { ((maxWidth - 62.dp) / 2).toPx() }
            var dx by remember { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf(false) }
            val shown by animateFloatAsState(dx, if (dragging) snap<Float>() else spring<Float>(), label = "both")
            val frac = (shown / half).coerceIn(-1f, 1f)
            Box(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) { Handset(DeclineRed.copy(alpha = 0.8f), 26.dp, 135f) }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)) { Handset(AnswerGreen.copy(alpha = 0.9f), 26.dp) }
            Box(Modifier.align(Alignment.Center).offset { IntOffset(shown.toInt(), 0) }.size(60.dp).clip(CircleShape)
                .background(if (frac < 0f) lerp(Color(0xFF3B82F6), DeclineRed, -frac) else lerp(Color(0xFF3B82F6), AnswerGreen, frac))
                .pointerInput(half) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            if (dx >= half * 0.85f) curA() else if (dx <= -half * 0.85f) curR()
                            dx = 0f
                        },
                        onDragCancel = { dragging = false; dx = 0f }
                    ) { _, amount -> dx = (dx + amount).coerceIn(-half, half) }
                }, contentAlignment = Alignment.Center) {
                Handset(Color.White, 28.dp, if (frac < 0f) 135f * (-frac) else 0f)
            }
        }
    }
}

@Composable
fun SwipeUpDown(onAnswer: () -> Unit, onReject: () -> Unit) {
    val travel = with(LocalDensity.current) { 70.dp.toPx() }
    val curA by rememberUpdatedState(onAnswer)
    val curR by rememberUpdatedState(onReject)
    var dy by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(dy, if (dragging) snap<Float>() else spring<Float>(), label = "ud")
    val frac = (shown / travel).coerceIn(-1f, 1f)
    Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        Text("▲ برای پاسخ بالا بکشید", Modifier.align(Alignment.TopCenter), fontSize = 13.sp, color = Color(0xFF52E58A))
        Text("▼ برای رد پایین بکشید", Modifier.align(Alignment.BottomCenter), fontSize = 13.sp, color = Color(0xFFFF7A7A))
        Box(Modifier.offset { IntOffset(0, shown.toInt()) }.size(84.dp).clip(CircleShape)
            .background(if (frac < 0f) lerp(Color(0xFF3B82F6), Color(0xFF1B9E4B), -frac) else lerp(Color(0xFF3B82F6), Color(0xFFC62828), frac))
            .pointerInput(travel) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        if (dy <= -travel * 0.8f) curA() else if (dy >= travel * 0.8f) curR()
                        dy = 0f
                    },
                    onDragCancel = { dragging = false; dy = 0f }
                ) { _, amount -> dy = (dy + amount).coerceIn(-travel, travel) }
            }, contentAlignment = Alignment.Center) { Handset(Color.White, 36.dp, if (frac > 0f) 135f * frac else 0f) }
    }
}

@Composable
private fun DragCircle(color: Color, label: String, rotation: Float, onTrigger: () -> Unit) {
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    val cur by rememberUpdatedState(onTrigger)
    var dy by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(dy, if (dragging) snap<Float>() else spring<Float>(), label = "dc")
    val inf = rememberInfiniteTransition(label = "ring")
    val ring by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "r")
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).graphicsLayer { val s = 1f + ring * 0.42f; scaleX = s; scaleY = s; alpha = (1f - ring) * 0.45f }
                .clip(CircleShape).background(color))
            Box(Modifier.offset { IntOffset(0, shown.toInt()) }.size(76.dp).clip(CircleShape).background(color)
                .pointerInput(threshold) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false; if (dy <= -threshold) cur(); dy = 0f },
                        onDragCancel = { dragging = false; dy = 0f }
                    ) { _, amount -> dy = (dy + amount).coerceIn(-threshold * 1.15f, 0f) }
                }, contentAlignment = Alignment.Center) { Handset(Color.White, 33.dp, rotation) }
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwipeCircles(onAnswer: () -> Unit, onReject: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            DragCircle(DeclineRed, "رد تماس ↑", 135f, onReject)
            DragCircle(AnswerGreen, "پاسخ ↑", 0f, onAnswer)
        }
    }
}

@Composable
fun CapsuleButtons(onAnswer: () -> Unit, onReject: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Button(onReject, Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeclineRed)) {
            Handset(Color.White, 22.dp, 135f); Spacer(Modifier.width(8.dp)); Text("رد", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Button(onAnswer, Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AnswerGreen)) {
            Handset(Color.White, 22.dp); Spacer(Modifier.width(8.dp)); Text("پاسخ", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

val answerStyleNames = listOf("دکمه‌های گرد", "کشویی (iPhone)", "کپسولی", "کشیدن بالا/پایین", "کشیدن دایره‌ها (Galaxy)", "کشویی دوطرفه")

/** کنترل‌های پاسخ به تماس ورودی؛ style: 0 دکمه‌های گرد، 1 کشویی (iOS)، 2 کپسولی، 3 کشیدن بالا/پایین، 4 کشیدن دایره‌ها (Galaxy)، 5 کشویی دوطرفه */
@Composable
fun AnswerControls(style: Int, onAnswer: () -> Unit, onReject: () -> Unit) {
    when (style) {
        1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SlideToAnswer("برای پاسخ بکشید  ›››", onAnswer)
            Spacer(Modifier.height(14.dp))
            CallCircleButton(false, "رد تماس", onClick = onReject)
        }
        2 -> CapsuleButtons(onAnswer, onReject)
        3 -> SwipeUpDown(onAnswer, onReject)
        4 -> SwipeCircles(onAnswer, onReject)
        5 -> SlideBoth(onAnswer, onReject)
        else -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            CallCircleButton(false, "رد تماس", onClick = onReject)
            CallCircleButton(true, "پاسخ", pulse = true, onClick = onAnswer)
        }
    }
}

// ======================= حرکات و حذف =======================

fun actionLabel(a: Int) = when (a) {
    1 -> "باز کردن جزئیات"; 2 -> "تماس"; 3 -> "ارسال پیامک"; 4 -> "حذف از تاریخچه"
    5 -> "انتخاب چندتایی"; 6 -> "رد خودکار (فعال/غیرفعال)"; else -> "هیچ‌کاری"
}

fun actionEmoji(a: Int) = when (a) { 1 -> "📄"; 2 -> "📞"; 3 -> "✉️"; 4 -> "🗑"; 5 -> "☑️"; 6 -> "🚫"; else -> "" }

private fun actionColor(a: Int) = when (a) {
    2 -> Color(0xFF2E9F5B); 3 -> Color(0xFF1E88E5); 4 -> Color(0xFFD32F2F)
    5 -> Color(0xFF607D8B); 6 -> Color(0xFFFB8C00); else -> Color(0xFF3F51B5)
}

@Composable
fun ChoiceRow(title: String, value: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Text(actionLabel(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(o); open = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(o == value, { onSelect(o); open = false }); Text(actionLabel(o))
                    }
                }
            }
        }, confirmButton = {})
}

/** کشیدن به چپ/راست؛ left = عمل کشیدن به سمت چپ، right = عمل کشیدن به سمت راست (چیدمان راست‌به‌چپ) */
@Composable
fun SwipeRow(left: Int, right: Int, onAction: (Int) -> Unit, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val curL by rememberUpdatedState(left)
    val curR by rememberUpdatedState(right)
    val cur by rememberUpdatedState(onAction)
    val st = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.StartToEnd) cur(curL) else if (v == SwipeToDismissBoxValue.EndToStart) cur(curR)
        false
    })
    SwipeToDismissBox(st, backgroundContent = {
        val dir = st.dismissDirection
        val a = if (dir == SwipeToDismissBoxValue.StartToEnd) left else if (dir == SwipeToDismissBoxValue.EndToStart) right else 0
        val flat = look.style == 1
        Box(Modifier.fillMaxSize().padding(horizontal = if (flat) 0.dp else 12.dp, vertical = if (flat) 0.dp else 3.dp)
            .clip(if (flat) RectangleShape else RoundedCornerShape(look.row))
            .background(if (a == 0) Color.Transparent else actionColor(a)),
            contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
            if (a != 0) Text(actionEmoji(a) + " " + actionLabel(a), Modifier.padding(horizontal = 20.dp), color = Color.White, fontSize = 13.sp)
        }
    }, enableDismissFromStartToEnd = left != 0, enableDismissFromEndToStart = right != 0) { content() }
}

/** حذف رکوردهای تاریخچه؛ در صورت نیاز مجوز WRITE_CALL_LOG را می‌پرسد */
@Composable
fun rememberCallDeleter(): (List<Long>) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<List<Long>?>(null) }
    fun run(ids: List<Long>) {
        val ok = deleteCalls(ctx, ids)
        Toast.makeText(ctx, if (ok) "حذف شد" else "حذف ممکن نشد (برنامه باید شماره‌گیر پیش‌فرض باشد)", Toast.LENGTH_LONG).show()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        val p = pending; pending = null
        if (g && p != null) run(p) else Toast.makeText(ctx, "برای حذف سابقه، مجوز لازم است", Toast.LENGTH_SHORT).show()
    }
    return { ids ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) run(ids)
        else { pending = ids; launcher.launch(Manifest.permission.WRITE_CALL_LOG) }
    }
}
