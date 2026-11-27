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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        handle(intent)
        setContent {
            AppTheme {
                val call = CallHolder.call
                val liveState = CallHolder.state
                LaunchedEffect(call, liveState) {
                    if (call == null || liveState == Call.STATE_DISCONNECTING || liveState == Call.STATE_DISCONNECTED) {
                        delay(250)
                        if (CallHolder.call == null || CallHolder.state == Call.STATE_DISCONNECTING || CallHolder.state == Call.STATE_DISCONNECTED) finish()
                    }
                }
                if (call != null) InCallScreen(call)
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
    var pendingReply by remember { mutableStateOf<String?>(null) }
    var showReplies by remember { mutableStateOf(false) }
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
    val lastText: String? = if (!lastLoaded) null else {
        val r = last
        if (r == null) "اولین تماس با این شماره"
        else Jalali.dateOnly(r.date) + "  •  " + typeName(r.type) + (if (r.duration > 0) "  •  " + fmtDur(r.duration) else "")
    }
    val ui = CallUi(
        name = name, number = number, state = state, status = status, sim = sim, groups = groupNames,
        ref = ref?.let { it.second.ifBlank { Jalali.fa(it.first) } }, note = note, lastText = lastText,
        muted = CallHolder.muted, recording = Recorder.recording, route = CallHolder.route,
        routeActive = CallHolder.route != CallAudioState.ROUTE_EARPIECE, held = state == Call.STATE_HOLDING,
        showPad = showPad, typed = typed,
        canReply = Prefs.on("quickReplies") && Prefs.quickReplies.any { it.isNotBlank() }
    )
    val acts = CallActs(
        answer = { call.answer(0) }, reject = { call.reject(false, null) }, hangup = { call.disconnect() },
        mute = { CallHolder.service?.setMuted(!CallHolder.muted) },
        pad = { showPad = true }, closePad = { showPad = false },
        dtmf = { ch ->
            typed += ch
            scope.launch { call.playDtmfTone(ch); delay(160); call.stopDtmfTone() }
        },
        route = {
            if (routeOpts.size > 2) showRoute = true
            else setRoute(if (CallHolder.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
        },
        record = onRecord,
        hold = { if (state == Call.STATE_HOLDING) call.unhold() else call.hold() },
        note = { editNote = true }, replies = { showReplies = true }
    )

    CallTheme {
        CallScreenBody(ui, acts) {
            AnimatedVisibility(CallHolder.waiting != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                CallHolder.waiting?.let { WaitingCard(it, call) }
            }
            CallHolder.held?.let { HeldCard(it, call) }
        }

        if (showReplies) {
            AlertDialog(onDismissRequest = { showReplies = false },
                title = { Text("انتخاب پیام آماده") },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Prefs.quickReplies.filter { it.isNotBlank() }.forEach { message ->
                            OutlinedButton(onClick = { pendingReply = message; showReplies = false },
                                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Text(message, Modifier.fillMaxWidth(), textAlign = TextAlign.Start, maxLines = 3)
                            }
                        }
                    }
                }, confirmButton = { TextButton({ showReplies = false }) { Text("بستن") } })
        }

        pendingReply?.let { message ->
            AlertDialog(
                onDismissRequest = { pendingReply = null },
                title = { Text("رد تماس با پیامک") },
                text = { Text("تماس رد شود و پیامک زیر ارسال شود؟\n\n$message") },
                confirmButton = {
                    Button(onClick = { call.reject(true, message); pendingReply = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E9F5B))) { Text("رد تماس و ارسال پیامک") }
                },
                dismissButton = { TextButton(onClick = { pendingReply = null }) { Text("انصراف") } }
            )
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
}
