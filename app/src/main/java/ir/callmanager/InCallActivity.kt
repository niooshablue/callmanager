package ir.callmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        setContent {
            AppTheme {
                val call = CallHolder.call
                LaunchedEffect(call) { if (call == null) finish() }
                if (call != null) InCallScreen(call)
            }
        }
    }
}

private val QUICK = listOf("الان نمی‌توانم صحبت کنم، بعداً تماس می‌گیرم.", "در جلسه هستم، لطفاً پیام بدهید.", "تا چند دقیقهٔ دیگر تماس می‌گیرم.")

@Composable
fun ActionBtn(emoji: String, label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(if (active) cs.primary else cs.surfaceContainerHigh).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, color = cs.onSurface)
    }
}

@Composable
fun InCallScreen(call: Call) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val number = call.details.handle?.schemeSpecificPart ?: ""
    val name = remember(number) { lookupName(ctx, number) }
    var note by remember(number) { mutableStateOf(db.note(number)) }
    val ref = remember(number) { db.referrer(number) }
    val groupNames = remember(number) { val m = db.memberGroups(number); db.groups().filter { it.id in m }.map { it.name } }
    val sim = remember(call) { simName(ctx, call.details.accountHandle) }
    val state = CallHolder.state
    var showPad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf(false) }
    var showRoute by remember { mutableStateOf(false) }
    val routeOpts = listOf(
        Triple(CallAudioState.ROUTE_EARPIECE, "بلندگوی مکالمه (گوشی)", "📱"),
        Triple(CallAudioState.ROUTE_SPEAKER, "بلندگوی گوشی", "🔊"),
        Triple(CallAudioState.ROUTE_BLUETOOTH, "بلوتوث", "🎧"),
        Triple(CallAudioState.ROUTE_WIRED_HEADSET, "هدفون سیمی", "🎧")
    ).filter { (CallHolder.routeMask and it.first) != 0 }
    fun setRoute(r: Int) {
        @Suppress("DEPRECATION")
        CallHolder.service?.setAudioRoute(r)
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val connected = state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
    val ct = call.details.connectTimeMillis
    val secs = if (connected && ct > 0) ((now - ct) / 1000).coerceAtLeast(0) else 0L

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) Recorder.start(ctx, number) else Toast.makeText(ctx, "برای ضبط، دسترسی میکروفون لازم است", Toast.LENGTH_SHORT).show()
    }
    val onRecord: () -> Unit = {
        if (Recorder.recording) {
            Recorder.stop(); Toast.makeText(ctx, "ضبط ذخیره شد", Toast.LENGTH_SHORT).show()
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val ok = Recorder.start(ctx, number)
            Toast.makeText(ctx, if (ok) "ضبط شروع شد؛ برای ضبط صدای طرف مقابل بلندگو را روشن کنید" else "شروع ضبط ممکن نشد", Toast.LENGTH_LONG).show()
        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val status = when (state) {
        Call.STATE_RINGING -> "تماس ورودی…"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "در حال شماره‌گیری…"
        Call.STATE_ACTIVE -> fmtDur(secs)
        Call.STATE_HOLDING -> "در انتظار • " + fmtDur(secs)
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "پایان تماس"
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(cs.surface, cs.primary, 0.38f), cs.surface)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (sim != null) Surface(shape = RoundedCornerShape(50), color = cs.surfaceContainerHigh) {
                    Text(Jalali.fa(sim), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                if (Recorder.recording) Surface(shape = RoundedCornerShape(50), color = Color(0xFFE53935)) {
                    Text("● در حال ضبط", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, color = Color.White)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Avatar(name ?: number, 118.dp)
                Spacer(Modifier.height(16.dp))
                Text(name ?: Jalali.fa(number).ifBlank { "ناشناس" }, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (name != null) Text(Jalali.fa(number), fontSize = 15.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = if (state == Call.STATE_ACTIVE) 24.sp else 16.sp, color = cs.primary, fontWeight = FontWeight.Medium)
                if (groupNames.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupNames.forEach { g -> AssistChip(onClick = {}, label = { Text(g, fontSize = 12.sp) }) }
                    }
                }
                if (ref != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = cs.secondaryContainer) {
                        Text("معرف: " + ref.second.ifBlank { Jalali.fa(ref.first) }, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 13.sp)
                    }
                }
                if (note.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp).fillMaxWidth()) {
                            Text("📝 یادداشت", fontSize = 12.sp, color = cs.onTertiaryContainer)
                            Spacer(Modifier.height(4.dp))
                            Text(note, fontSize = 18.sp, color = cs.onTertiaryContainer)
                        }
                    }
                }
            }

            if (showPad) {
                Text(typed.takeLast(24), fontSize = 22.sp, modifier = Modifier.padding(4.dp))
                Keypad(Modifier.widthIn(max = 340.dp), keyHeight = 50.dp, onKey = { ch ->
                    typed += ch
                    scope.launch { call.playDtmfTone(ch); delay(160); call.stopDtmfTone() }
                })
                TextButton({ showPad = false }) { Text("بستن صفحه‌کلید") }
            } else if (connected) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionBtn("🎙", "بی‌صدا", CallHolder.muted) { CallHolder.service?.setMuted(!CallHolder.muted) }
                    ActionBtn("⌨", "صفحه‌کلید", false) { showPad = true }
                    ActionBtn(routeOpts.firstOrNull { it.first == CallHolder.route }?.third ?: "🔊", "خروجی صدا",
                        CallHolder.route != CallAudioState.ROUTE_EARPIECE) {
                        if (routeOpts.size > 2) showRoute = true
                        else setRoute(if (CallHolder.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionBtn("⏺", "ضبط", Recorder.recording, onRecord)
                    ActionBtn("⏸", "انتظار", state == Call.STATE_HOLDING) { if (state == Call.STATE_HOLDING) call.unhold() else call.hold() }
                    ActionBtn("📝", "یادداشت", false) { editNote = true }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state == Call.STATE_RINGING) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK.forEach { m -> SuggestionChip(onClick = { call.reject(true, m) }, label = { Text(m, fontSize = 12.sp) }) }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    CallCircleButton(false, "رد تماس") { call.reject(false, null) }
                    CallCircleButton(true, "پاسخ", pulse = true) { call.answer(0) }
                }
            } else {
                CallCircleButton(false, "پایان تماس") { call.disconnect() }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showRoute) AlertDialog(onDismissRequest = { showRoute = false }, title = { Text("خروجی صدا") },
        text = {
            Column {
                routeOpts.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { setRoute(o.first); showRoute = false }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(CallHolder.route == o.first, { setRoute(o.first); showRoute = false })
                        Text(o.third + "  " + o.second)
                    }
                }
            }
        }, confirmButton = { TextButton({ showRoute = false }) { Text("بستن") } })

    if (editNote) {
        var tmp by remember { mutableStateOf(note) }
        AlertDialog(onDismissRequest = { editNote = false }, title = { Text("یادداشت") },
            text = { OutlinedTextField(tmp, { tmp = it }, Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { TextButton({ db.setNote(number, tmp); note = tmp.trim(); editNote = false }) { Text("ذخیره") } },
            dismissButton = { TextButton({ editNote = false }) { Text("انصراف") } })
    }
}
