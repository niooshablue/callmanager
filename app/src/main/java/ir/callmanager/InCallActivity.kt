package ir.callmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InCallActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        handle(intent)
        setContent {
            AppTheme {
                val ctx = LocalContext.current
                val call = CallHolder.call
                LaunchedEffect(call) { if (call == null) finish() }
                if (call != null) {
                    if (Prefs.callDark || Prefs.callTheme == 2) {
                        var dcs = dynamicDarkColorScheme(ctx)
                        if (Prefs.accent != 0) dcs = dcs.copy(primary = Color(Prefs.accent))
                        if (Prefs.callTheme == 2) dcs = dcs.copy(background = Color.Black, surface = Color.Black)
                        MaterialTheme(colorScheme = dcs, typography = MaterialTheme.typography) {
                            Surface(Modifier.fillMaxSize(), color = dcs.background) { InCallScreen(call) }
                        }
                    } else InCallScreen(call)
                }
            }
        }
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); handle(i) }

    private fun handle(i: Intent?) {
        if (i?.getStringExtra("act") == "answer") {
            CallHolder.call?.let { if (it.state == Call.STATE_RINGING) it.answer(0) }
        }
    }
}

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
private fun WaitBtn(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.height(44.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Text(text, fontSize = 12.sp, color = Color.White, maxLines = 1, fontWeight = FontWeight.Bold)
    }
}

/** کارت تماس دوم که پشت خط زنگ می‌خورد */
@Composable
fun WaitingCard(w: Call, primary: Call?) {
    val ctx = LocalContext.current
    val num = w.details.handle?.schemeSpecificPart ?: ""
    val name = remember(num) { lookupName(ctx, num) }
    val note = remember(num) { Db.get(ctx).note(num) }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF1F2933), Color(0xFF2B3A55))))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name ?: num, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("📞 تماس دیگر در حال زنگ‌خوردن", fontSize = 11.sp, color = Color(0xFFB0BEC5))
                    Text(name ?: Jalali.fa(num).ifBlank { "ناشناس" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    if (name != null) Text(Jalali.fa(num), fontSize = 12.sp, color = Color(0xFFCFD8DC))
                }
            }
            if (note.isNotBlank()) Text("📝 $note", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Color(0xFFFFE082), maxLines = 2)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaitBtn("رد", Color(0xFFD32F2F), Modifier.weight(1f)) { w.reject(false, null) }
                WaitBtn("نگه‌دار و پاسخ", Color(0xFF2E9F5B), Modifier.weight(1.4f)) { primary?.hold(); w.answer(0) }
                WaitBtn("قطع و پاسخ", Color(0xFFFB8C00), Modifier.weight(1.2f)) { primary?.disconnect(); w.answer(0) }
            }
        }
    }
}

@Composable
fun HeldCard(h: Call, primary: Call?) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val num = h.details.handle?.schemeSpecificPart ?: ""
    val name = remember(num) { lookupName(ctx, num) }
    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏸", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("تماس در انتظار", fontSize = 10.sp, color = cs.onSurfaceVariant)
                Text(name ?: Jalali.fa(num).ifBlank { "ناشناس" }, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            FilledTonalButton({ primary?.hold(); h.unhold() }, shape = RoundedCornerShape(14.dp)) { Text("تعویض") }
        }
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
    var last by remember(number) { mutableStateOf<Rec?>(null) }
    var lastLoaded by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        if (Prefs.on("lastCall")) { last = withContext(Dispatchers.IO) { lastCallOf(ctx, number) }; lastLoaded = true }
    }
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
    val dialing = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
    val ct = call.details.connectTimeMillis
    val secs = if (connected && ct > 0) ((now - ct) / 1000).coerceAtLeast(0) else 0L

    // سنسور مجاورت: نزدیک‌کردن گوشی به صورت (حین شماره‌گیری و مکالمه) صفحه را خاموش می‌کند
    val proxOn = (state == Call.STATE_ACTIVE || dialing) && CallHolder.route == CallAudioState.ROUTE_EARPIECE && !showPad && Prefs.on("proximity")
    DisposableEffect(proxOn) {
        var wl: PowerManager.WakeLock? = null
        try {
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (proxOn && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                val l = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callmanager:prox")
                l.setReferenceCounted(false)
                l.acquire()
                wl = l
            }
        } catch (e: Exception) { }
        onDispose {
            try { wl?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) } } catch (e: Exception) { }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) Recorder.start(ctx, number) else Toast.makeText(ctx, "برای ضبط، دسترسی میکروفون لازم است", Toast.LENGTH_SHORT).show()
    }
    val onRecord: () -> Unit = {
        if (Recorder.recording) {
            Recorder.stop(); Toast.makeText(ctx, "ضبط ذخیره شد", Toast.LENGTH_SHORT).show()
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val ok = Recorder.start(ctx, number)
            Toast.makeText(ctx, if (ok) "ضبط شروع شد؛ برای ضبط صدای طرف مقابل، بلندگو را روشن کنید" else "شروع ضبط ممکن نشد", Toast.LENGTH_LONG).show()
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
    val routeBtn: @Composable () -> Unit = {
        ActionBtn(routeOpts.firstOrNull { it.first == CallHolder.route }?.third ?: "🔊", "خروجی صدا",
            CallHolder.route != CallAudioState.ROUTE_EARPIECE) {
            if (routeOpts.size > 2) showRoute = true
            else setRoute(if (CallHolder.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
        }
    }

    val bgBrush = when (Prefs.callTheme) {
        1 -> Brush.verticalGradient(listOf(cs.surface, cs.surface))
        2 -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        else -> Brush.verticalGradient(listOf(lerp(cs.surface, cs.primary, 0.38f), cs.surface))
    }
    Box(Modifier.fillMaxSize().background(bgBrush)) {
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
            AnimatedVisibility(CallHolder.waiting != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                CallHolder.waiting?.let { WaitingCard(it, call) }
            }
            CallHolder.held?.let { HeldCard(it, call) }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Avatar(name ?: number, if (Prefs.callTheme == 1) 84.dp else 112.dp)
                Spacer(Modifier.height(14.dp))
                Text(name ?: Jalali.fa(number).ifBlank { "ناشناس" }, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (name != null && number.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = cs.surfaceContainerHigh.copy(alpha = 0.9f)) {
                        Text(Jalali.fa(number), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 16.sp, color = cs.onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(status, fontSize = if (state == Call.STATE_ACTIVE) 26.sp else 16.sp, color = cs.primary, fontWeight = FontWeight.Medium)
                if (groupNames.isNotEmpty() && Prefs.on("groupsShow")) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupNames.forEach { g -> AssistChip(onClick = {}, label = { Text(g, fontSize = 12.sp) }) }
                    }
                }
                if (ref != null && Prefs.on("referrer")) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = cs.secondaryContainer) {
                        Text("معرف: " + ref.second.ifBlank { Jalali.fa(ref.first) }, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 13.sp)
                    }
                }
                if (lastLoaded) {
                    Spacer(Modifier.height(12.dp))
                    val r = last
                    Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh.copy(alpha = 0.9f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (r != null) {
                                CallTypeBadge(r.type, 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("آخرین تماس قبلی", fontSize = 10.sp, color = cs.onSurfaceVariant)
                                    Text(Jalali.format(r.date), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(typeName(r.type) + (if (r.duration > 0) " • " + fmtDur(r.duration) else ""), fontSize = 11.sp, color = cs.onSurfaceVariant)
                                }
                            } else Text("✨ اولین تماس با این شماره", fontSize = 13.sp)
                        }
                    }
                }
                if (note.isNotBlank() && Prefs.on("notes")) {
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)) {
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
                    routeBtn()
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (Prefs.on("record")) ActionBtn("⏺", "ضبط", Recorder.recording, onRecord)
                    ActionBtn("⏸", "انتظار", state == Call.STATE_HOLDING) { if (state == Call.STATE_HOLDING) call.unhold() else call.hold() }
                    ActionBtn("📝", "یادداشت", false) { editNote = true }
                }
            } else if (dialing) {
                // حین برقراری تماس هم می‌توان خروجی صدا را تعیین کرد
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionBtn("🎙", "بی‌صدا", CallHolder.muted) { CallHolder.service?.setMuted(!CallHolder.muted) }
                    routeBtn()
                    ActionBtn("📝", "یادداشت", false) { editNote = true }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state == Call.STATE_RINGING) {
                val replies = if (Prefs.on("quickReplies")) Prefs.quickReplies.filter { it.isNotBlank() } else emptyList()
                if (replies.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        replies.forEach { m -> SuggestionChip(onClick = { call.reject(true, m) }, label = { Text(m, fontSize = 12.sp, maxLines = 1) }) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                AnswerControls(Prefs.answerStyle, { call.answer(0) }, { call.reject(false, null) })
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
