@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ---------------- تم و شخصی‌سازی ----------------

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var cs = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
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
            primaryContainer = androidx.compose.ui.graphics.lerp(a, cs.surface, 0.8f), onPrimaryContainer = a,
            secondaryContainer = androidx.compose.ui.graphics.lerp(a, cs.surface, 0.85f))
    }
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
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, d.fontScale * Prefs.fontScale),
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(colorScheme = cs, typography = ty) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

// ---------------- آیکون‌های ترسیمی ----------------

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

@Composable
fun CallTypeBadge(type: Int, dim: Dp = 42.dp) {
    val color = when (type) {
        1 -> Color(0xFF1E88E5); 2 -> Color(0xFF2E9F5B); 3 -> Color(0xFFE53935)
        5 -> Color(0xFFFB8C00); 6 -> Color(0xFFD32F2F); else -> Color(0xFF78909C)
    }
    Canvas(Modifier.size(dim).clip(CircleShape).background(color.copy(alpha = 0.15f))) {
        val w = this.size.width
        val sw = w * 0.075f
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
}

@Composable
fun Avatar(name: String, dim: Dp = 44.dp) {
    val h = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
    Box(Modifier.size(dim).clip(CircleShape).background(
        Brush.linearGradient(listOf(Color.hsv(h, 0.55f, 0.88f), Color.hsv((h + 40f) % 360f, 0.65f, 0.68f)))),
        contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).ifBlank { "#" }, color = Color.White, fontSize = (dim.value * 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

/** دکمهٔ گرد پاسخ (سبز) یا رد/قطع (قرمز) */
@Composable
fun CallCircleButton(answer: Boolean, label: String, pulse: Boolean = false, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val sc by t.animateFloat(1f, if (pulse) 1.12f else 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "sc")
    val cols = if (answer) listOf(Color(0xFF52E58A), Color(0xFF1B9E4B)) else listOf(Color(0xFFFF7A7A), Color(0xFFC62828))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.size(82.dp)
            .shadow(14.dp, CircleShape, ambientColor = cols[1], spotColor = cols[1])
            .clip(CircleShape).background(Brush.verticalGradient(cols)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            Handset(Color.White, 36.dp, if (answer) 0f else 135f)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp)
    }
}

// ---------------- اجزای مشترک ----------------

@Composable
fun CallPill(modifier: Modifier, title: String, sub: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.height(56.dp), enabled = enabled, shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E9F5B)), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Handset(Color.White, 20.dp); Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (sub != null) Text(sub, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** دکمه(های) تماس؛ اگر دو سیم‌کارت باشد برای هر کدام یک دکمه (پیش‌فرض با ★) */
@Composable
fun SimCallRow(number: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ctx = LocalContext.current
    val sims = Repo.sims
    val on = enabled && number.isNotBlank()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sims.size < 2) CallPill(Modifier.weight(1f), "تماس", null, on) { placeCall(ctx, number, sims.firstOrNull()?.handle) }
        else sims.forEach { s ->
            CallPill(Modifier.weight(1f), "SIM ${Jalali.fa(s.index)}" + if (s.isDefault) " ★" else "", s.label, on) {
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

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun Keypad(modifier: Modifier = Modifier, keyHeight: Dp = 58.dp, onKey: (Char) -> Unit, onLongZero: (() -> Unit)? = null) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier) {
            listOf("123", "456", "789", "*0#").forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { ch ->
                        Box(Modifier.weight(1f).padding(4.dp).height(keyHeight).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .combinedClickable(onClick = { onKey(ch) }, onLongClick = if (ch == '0') onLongZero else null),
                            contentAlignment = Alignment.Center) {
                            Text(Jalali.fa(ch.toString()), fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Empty(t: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(t, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}


// ---------------- ظاهر مدرن، حرکات، منو ----------------

@Composable
fun AppHeader(title: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
        .background(Brush.horizontalGradient(listOf(lerp(cs.surface, cs.primary, 0.28f), lerp(cs.surface, cs.primary, 0.06f))))
        .statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MenuItem(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowLeft, null, tint = cs.onSurfaceVariant)
        }
    }
}

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
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(18.dp))
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
