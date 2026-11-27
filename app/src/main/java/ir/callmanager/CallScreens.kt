package ir.callmanager

import android.telecom.Call
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** وضعیت قابل‌نمایش یک تماس؛ مستقل از شیء Call تا پیش‌نمایش در تنظیمات هم ممکن باشد */
class CallUi(
    val name: String?, val number: String, val state: Int, val status: String,
    val sim: String?, val groups: List<String>, val ref: String?, val note: String, val lastText: String?,
    val muted: Boolean, val recording: Boolean, val route: Int, val routeActive: Boolean,
    val held: Boolean, val showPad: Boolean, val typed: String, val canReply: Boolean
) {
    val ringing get() = state == Call.STATE_RINGING
    val dialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
    val connected get() = state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
    val title get() = name ?: Jalali.fa(number).ifBlank { "ناشناس" }
}

class CallActs(
    val answer: () -> Unit = {}, val reject: () -> Unit = {}, val hangup: () -> Unit = {},
    val mute: () -> Unit = {}, val pad: () -> Unit = {}, val closePad: () -> Unit = {}, val dtmf: (Char) -> Unit = {},
    val route: () -> Unit = {}, val record: () -> Unit = {}, val hold: () -> Unit = {},
    val note: () -> Unit = {}, val replies: () -> Unit = {}
)

private val EndRed = Color(0xFFE5392F)

/** آیا صفحهٔ تماس باید با رنگ‌بندی تیره نمایش داده شود؟ (قالب‌های iPhone و Galaxy ذاتاً تیره‌اند) */
fun callForceDark(): Boolean = Prefs.callDark || Prefs.callTheme == 2 || (Prefs.callLayout != 0 && Prefs.callTheme == 0)

@Composable
fun CallTheme(content: @Composable () -> Unit) {
    if (callForceDark()) AppTheme(forceDark = true, content = content) else content()
}

fun routeIcon(route: Int): ImageVector = when (route) {
    CallAudioState.ROUTE_SPEAKER -> Icons.Default.VolumeUp
    CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.Bluetooth
    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
    else -> Icons.Default.PhoneInTalk
}

private fun callBackground(layout: Int, cs: ColorScheme): Brush = when {
    Prefs.callTheme == 2 -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    Prefs.callTheme == 1 -> Brush.verticalGradient(listOf(cs.surface, cs.surface))
    layout == 1 -> Brush.verticalGradient(listOf(Color(0xFF4A5775), Color(0xFF242C40), Color(0xFF0C101A)))
    layout == 2 -> Brush.verticalGradient(listOf(Color(0xFF1E2B40), Color(0xFF0E131D), Color(0xFF05070B)))
    else -> Brush.verticalGradient(listOf(lerp(cs.surface, cs.primary, 0.38f), cs.surface))
}

private fun metaText(ui: CallUi): String {
    val parts = ArrayList<String>()
    if (Prefs.on("callSim") && !ui.sim.isNullOrBlank()) parts += ui.sim
    if (Prefs.on("groupsShow") && ui.groups.isNotEmpty()) parts += ui.groups.joinToString("، ")
    if (Prefs.on("referrer") && !ui.ref.isNullOrBlank()) parts += "معرف: " + ui.ref
    return parts.joinToString("  •  ")
}

private class Ctl(val icon: ImageVector, val label: String, val active: Boolean, val onClick: () -> Unit)

private fun buildControls(ui: CallUi, a: CallActs): List<Ctl> {
    val l = ArrayList<Ctl>()
    if (Prefs.on("ctrlMute")) l += Ctl(if (ui.muted) Icons.Default.MicOff else Icons.Default.Mic, "بی‌صدا", ui.muted, a.mute)
    if (ui.connected && Prefs.on("ctrlKeypad")) l += Ctl(Icons.Default.Dialpad, "صفحه‌کلید", false, a.pad)
    if (Prefs.on("ctrlRoute")) l += Ctl(routeIcon(ui.route), "خروجی صدا", ui.routeActive, a.route)
    if (ui.connected && Prefs.on("record")) l += Ctl(Icons.Default.FiberManualRecord, "ضبط", ui.recording, a.record)
    if (ui.connected && Prefs.on("ctrlHold")) l += Ctl(Icons.Default.Pause, "انتظار", ui.held, a.hold)
    if (Prefs.on("ctrlNote")) l += Ctl(Icons.Default.Edit, "یادداشت", false, a.note)
    return l
}

@Composable
private fun ControlGrid(ctls: List<Ctl>, size: Dp, shape: Shape, idleBg: Color, activeBg: Color, idleFg: Color, activeFg: Color,
                        labelColor: Color) {
    val perRow = 3
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ctls.chunked(perRow).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowItems.forEach { c ->
                    Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(size).clip(shape).background(if (c.active) activeBg else idleBg).clickable(onClick = c.onClick),
                            contentAlignment = Alignment.Center) {
                            Icon(c.icon, c.label, tint = if (c.active) activeFg else idleFg, modifier = Modifier.size(size * 0.4f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(c.label, fontSize = 11.sp, color = labelColor, maxLines = 1, fontWeight = FontWeight.Medium)
                    }
                }
                repeat(perRow - rowItems.size) { Spacer(Modifier.width(90.dp)) }
            }
        }
    }
}

@Composable
private fun InfoStrip(ui: CallUi, a: CallActs, container: Color, fg: Color) {
    val hasNote = Prefs.on("notes") && ui.note.isNotBlank()
    val hasLast = Prefs.on("lastCall") && !ui.lastText.isNullOrBlank()
    if (!hasNote && !hasLast) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(container).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (hasNote) Row(Modifier.fillMaxWidth().clickable(onClick = a.note), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Edit, null, Modifier.size(15.dp), tint = fg.copy(alpha = 0.7f))
            Spacer(Modifier.width(7.dp))
            Text(ui.note, fontSize = 13.sp, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (hasLast) Text("آخرین تماس: " + ui.lastText, fontSize = 11.sp, color = fg.copy(alpha = 0.78f), maxLines = 1)
    }
}

@Composable
private fun PadPanel(ui: CallUi, a: CallActs, textColor: Color, style: Int) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Jalali.fa(ui.typed.takeLast(24)), Modifier.padding(4.dp).heightIn(min = 32.dp), fontSize = 24.sp, color = textColor)
        Keypad(Modifier.widthIn(max = 330.dp), keyHeight = 46.dp, style = style, onKey = a.dtmf)
        TextButton(a.closePad) { Text("بستن صفحه‌کلید", color = textColor) }
    }
}

@Composable
private fun EndButton(layout: Int, onClick: () -> Unit) {
    when (layout) {
        1 -> Box(Modifier.size(76.dp).clip(CircleShape).background(EndRed).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CallEnd, "پایان تماس", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        2 -> Box(Modifier.fillMaxWidth(0.72f).height(68.dp).clip(RoundedCornerShape(34.dp)).background(EndRed).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CallEnd, "پایان تماس", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        else -> CallCircleButton(false, "پایان تماس", onClick = onClick)
    }
}

@Composable
private fun RingingBottom(ui: CallUi, a: CallActs, layout: Int, fg: Color) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (ui.canReply) {
            if (layout == 0) OutlinedButton(onClick = a.replies, Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(44.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("انتخاب پیامک برای رد تماس")
            } else TextButton(onClick = a.replies) {
                Icon(Icons.Default.Message, null, tint = fg); Spacer(Modifier.width(8.dp)); Text("رد با پیامک", color = fg)
            }
            Spacer(Modifier.height(8.dp))
        }
        AnswerControls(Prefs.answerStyle, a.answer, a.reject)
    }
}

// ======================= چیدمان ۰: مدرن =======================

@Composable
private fun ModernCall(ui: CallUi, a: CallActs, extras: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(callBackground(0, cs))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (ui.ringing) "تماس ورودی" else if (ui.connected) "در حال مکالمه" else "تماس",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (Prefs.on("callSim") && ui.sim != null) AssistChip(onClick = {}, label = { Text(ui.sim, fontSize = 11.sp) })
                if (ui.recording) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFFFE3E3)) {
                        Text("● ضبط", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                    }
                }
            }
            extras()
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh.copy(alpha = 0.94f))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ui.name ?: ui.number, 76.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(ui.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Start)
                        if (ui.number.isNotBlank() && ui.name != null) Text(Jalali.fa(ui.number), fontSize = 14.sp, color = cs.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(5.dp))
                        Text(ui.status, fontSize = if (ui.state == Call.STATE_ACTIVE) 19.sp else 13.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                val showGroups = ui.groups.isNotEmpty() && Prefs.on("groupsShow")
                val showRef = !ui.ref.isNullOrBlank() && Prefs.on("referrer")
                if (showGroups || showRef) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (showGroups) ui.groups.forEach { AssistChip(onClick = {}, label = { Text(it, fontSize = 11.sp) }) }
                        if (showRef) AssistChip(onClick = {}, label = { Text("معرف: " + ui.ref, fontSize = 11.sp) })
                    }
                }
            }
            InfoStrip(ui, a, cs.secondaryContainer.copy(alpha = 0.72f), cs.onSecondaryContainer)
            Spacer(Modifier.weight(1f, fill = true))
            if (ui.showPad) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Box(Modifier.padding(10.dp)) { PadPanel(ui, a, cs.onSurface, 1) }
                }
            } else if (!ui.ringing) {
                ControlGrid(buildControls(ui, a), 62.dp, CircleShape, cs.surfaceContainerHigh, cs.primary, cs.onSurface, cs.onPrimary, cs.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = cs.surface.copy(alpha = 0.96f), tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ui.ringing) RingingBottom(ui, a, 0, cs.onSurface) else EndButton(0, a.hangup)
                }
            }
        }
    }
}

// ======================= چیدمان ۱: iPhone =======================

@Composable
private fun IosCall(ui: CallUi, a: CallActs, extras: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val plain = Prefs.callTheme == 1
    val fg = if (plain) cs.onSurface else Color.White
    Box(Modifier.fillMaxSize().background(callBackground(1, cs))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            extras()
            Text(ui.title, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(ui.status, fontSize = 20.sp, color = fg.copy(alpha = 0.72f))
            val meta = metaText(ui)
            if (meta.isNotEmpty()) Text(meta, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = fg.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 2)
            if (ui.recording) Text("● در حال ضبط", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
            InfoStrip(ui, a, fg.copy(alpha = 0.12f), fg)
            Spacer(Modifier.weight(1f, fill = true))
            if (ui.showPad) PadPanel(ui, a, fg, 3)
            else if (!ui.ringing) ControlGrid(buildControls(ui, a), 74.dp, CircleShape, fg.copy(alpha = 0.16f), fg, fg,
                if (plain) cs.surface else Color(0xFF1C1C1E), fg)
            Spacer(Modifier.height(16.dp))
            if (ui.ringing) RingingBottom(ui, a, 1, fg) else EndButton(1, a.hangup)
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ======================= چیدمان ۲: Galaxy =======================

@Composable
private fun GalaxyCall(ui: CallUi, a: CallActs, extras: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val plain = Prefs.callTheme == 1
    val fg = if (plain) cs.onSurface else Color.White
    Box(Modifier.fillMaxSize().background(callBackground(2, cs))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            extras()
            Spacer(Modifier.height(18.dp))
            Avatar(ui.name ?: ui.number, 108.dp)
            Spacer(Modifier.height(14.dp))
            Text(ui.title, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, textAlign = TextAlign.Center)
            if (ui.name != null && ui.number.isNotBlank()) Text(Jalali.fa(ui.number), fontSize = 15.sp, color = fg.copy(alpha = 0.65f))
            Spacer(Modifier.height(6.dp))
            Text(ui.status, fontSize = 18.sp, color = if (ui.connected) Color(0xFF7FDBA8) else fg.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
            val meta = metaText(ui)
            if (meta.isNotEmpty()) Text(meta, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = fg.copy(alpha = 0.55f), textAlign = TextAlign.Center, maxLines = 2)
            if (ui.recording) Text("● در حال ضبط", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
            InfoStrip(ui, a, fg.copy(alpha = 0.10f), fg)
            Spacer(Modifier.weight(1f, fill = true))
            if (ui.showPad) PadPanel(ui, a, fg, 4)
            else if (!ui.ringing) ControlGrid(buildControls(ui, a), 66.dp, RoundedCornerShape(26.dp), fg.copy(alpha = 0.12f), cs.primary, fg,
                cs.onPrimary, fg.copy(alpha = 0.85f))
            Spacer(Modifier.height(18.dp))
            if (ui.ringing) RingingBottom(ui, a, 2, fg) else EndButton(2, a.hangup)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun CallScreenBody(ui: CallUi, a: CallActs, extras: @Composable ColumnScope.() -> Unit = {}) {
    when (Prefs.callLayout) {
        1 -> IosCall(ui, a, extras)
        2 -> GalaxyCall(ui, a, extras)
        else -> ModernCall(ui, a, extras)
    }
}

// ======================= پیش‌نمایش در تنظیمات =======================

@Composable
fun CallPreviewScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableIntStateOf(if (Nav.callPreview == 1) 1 else 0) }   // 0 ورودی، 1 مکالمه
    var muted by remember { mutableStateOf(false) }
    var pad by remember { mutableStateOf(false) }
    var rec by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    val state = if (mode == 0) Call.STATE_RINGING else if (held) Call.STATE_HOLDING else Call.STATE_ACTIVE
    val ui = CallUi(
        name = "علی رضایی", number = "09121234567", state = state,
        status = if (mode == 0) "تماس ورودی…" else if (held) "در انتظار • ۰۲:۱۴" else "۰۲:۱۴",
        sim = "SIM ۱ · همراه اول", groups = listOf("خانواده"), ref = "مریم", note = "قرار ساعت ۵ عصر", lastText = "دیروز • ورودی • ۳:۲۰",
        muted = muted, recording = rec, route = CallAudioState.ROUTE_EARPIECE, routeActive = false,
        held = held, showPad = pad, typed = typed, canReply = Prefs.on("quickReplies")
    )
    val acts = CallActs(
        answer = { mode = 1; toast("پاسخ (نمایشی)") }, reject = { toast("رد تماس (نمایشی)") },
        hangup = { mode = 0; pad = false; held = false; toast("پایان تماس (نمایشی)") },
        mute = { muted = !muted }, pad = { pad = true }, closePad = { pad = false }, dtmf = { typed += it },
        route = { toast("خروجی صدا (نمایشی)") }, record = { rec = !rec }, hold = { held = !held },
        note = { toast("یادداشت (نمایشی)") }, replies = { toast("پیامک آماده (نمایشی)") }
    )
    Column(Modifier.fillMaxSize()) {
        Surface(color = cs.surface) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ Nav.callPreview = -1 }) { Icon(Icons.Default.Close, "بستن پیش‌نمایش") }
                Text("پیش‌نمایش", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                FilterChip(mode == 0, { mode = 0; pad = false }, label = { Text("تماس ورودی") })
                Spacer(Modifier.width(6.dp))
                FilterChip(mode == 1, { mode = 1 }, label = { Text("در مکالمه") })
                Spacer(Modifier.width(6.dp))
            }
        }
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            CallTheme { CallScreenBody(ui, acts) }
        }
    }
}
