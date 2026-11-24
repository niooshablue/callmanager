package ir.callmanager

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallScreeningService
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Calendar

object CallHolder {
    var call by mutableStateOf<Call?>(null)       // تماس اصلی
    var state by mutableIntStateOf(Call.STATE_NEW)
    var waiting by mutableStateOf<Call?>(null)    // تماس دیگری که پشت خط زنگ می‌خورد
    var held by mutableStateOf<Call?>(null)       // تماس در انتظار (Hold)
    var muted by mutableStateOf(false)
    var route by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE)
    var routeMask by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
    var service: InCallService? = null

    fun recompute(list: List<Call>) {
        val primary = list.firstOrNull {
            it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING || it.state == Call.STATE_PULLING_CALL
        } ?: list.firstOrNull { it.state == Call.STATE_RINGING } ?: list.firstOrNull()
        call = primary
        state = primary?.state ?: Call.STATE_NEW
        waiting = if (primary != null && primary.state != Call.STATE_RINGING) list.firstOrNull { it.state == Call.STATE_RINGING && it !== primary } else null
        held = list.firstOrNull { it.state == Call.STATE_HOLDING && it !== primary }
    }
}

object Recorder {
    var recording by mutableStateOf(false)
    private var rec: MediaRecorder? = null

    fun dir(c: Context): File = File(c.getExternalFilesDir(null), "Recordings").apply { mkdirs() }

    fun start(c: Context, number: String): Boolean {
        if (recording) return true
        return try {
            val f = File(dir(c), "rec_${System.currentTimeMillis()}_${norm(number)}.m4a")
            val r = MediaRecorder(c)
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            rec = r; recording = true; true
        } catch (e: Exception) { rec = null; recording = false; false }
    }

    fun stop() {
        try { rec?.stop() } catch (e: Exception) { }
        try { rec?.release() } catch (e: Exception) { }
        rec = null; recording = false
    }
}

fun lookupName(c: Context, number: String): String? {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return try {
        c.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: SecurityException) { null }
}

// ---------------- اعلان‌ها ----------------

object Notifs {
    const val ID_CALL = 1
    const val ID_MISSED = 2
    const val A_ANSWER = "ir.callmanager.ANSWER"
    const val A_DECLINE = "ir.callmanager.DECLINE"
    const val A_HANGUP = "ir.callmanager.HANGUP"
    const val A_CALLBACK = "ir.callmanager.CALLBACK"

    private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("incoming", "تماس ورودی", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("ongoing", "تماس جاری", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        nm.createNotificationChannel(NotificationChannel("missed", "تماس‌های بی‌پاسخ", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("reminder", "یادآوری تماس", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("missed_badge", "نشان تماس بی‌پاسخ (بی‌صدا)", NotificationManager.IMPORTANCE_MIN))
    }

    fun action(c: Context, act: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(c, req, Intent(c, CallActionReceiver::class.java).setAction(act), flags())

    @Suppress("DEPRECATION")
    fun postMissed(c: Context) {
        val count = Prefs.missedCount
        if (count <= 0 || (!Prefs.missedNotify && !Prefs.missedBadge)) return
        channels(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(c, 2,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags())
        val title = Jalali.fa("$count تماس بی‌پاسخ")
        val labels = Prefs.missedLabels.split("\n").filter { it.isNotBlank() }.joinToString("، ")
        if (Prefs.missedNotify) {
            val last = Prefs.missedLast
            val cb = PendingIntent.getBroadcast(c, 14,
                Intent(c, CallActionReceiver::class.java).setAction(A_CALLBACK).putExtra("number", last), flags())
            val sms = PendingIntent.getActivity(c, 15, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(last))), flags())
            val b = Notification.Builder(c, "missed").setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(title).setContentText(labels).setNumber(count).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_CALL).setContentIntent(open)
            if (last.isNotBlank()) {
                b.addAction(android.R.drawable.ic_menu_call, "تماس", cb)
                b.addAction(android.R.drawable.ic_menu_send, "پیامک", sms)
            }
            nm.notify(ID_MISSED, b.build())
        } else {
            val b = Notification.Builder(c, "missed_badge").setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(title).setNumber(count).setContentIntent(open)
            nm.notify(ID_MISSED, b.build())
        }
    }

    fun refreshMissed(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID_MISSED)
        postMissed(c)
    }

    fun clearMissed(c: Context) {
        if (Prefs.missedCount > 0) Prefs.clearMissed()
        c.getSystemService(NotificationManager::class.java).cancel(ID_MISSED)
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Notifs.A_ANSWER -> CallHolder.call?.answer(0)
            Notifs.A_DECLINE -> CallHolder.call?.reject(false, null)
            Notifs.A_HANGUP -> CallHolder.call?.disconnect()
            Notifs.A_CALLBACK -> {
                val n = i.getStringExtra("number") ?: ""
                if (!i.getBooleanExtra("noclear", false)) Notifs.clearMissed(c)
                val s = loadSims(c)
                placeCall(c, n, (s.firstOrNull { it.isDefault } ?: s.firstOrNull())?.handle)
            }
        }
    }
}

/** آیا اکنون در بازهٔ «مزاحم نشو» هستیم؟ */
fun quietNow(): Boolean {
    if (!Prefs.quietOn) return false
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val s = Prefs.quietStart; val e = Prefs.quietEnd
    return if (s == e) false else if (s < e) h in s until e else (h >= s || h < e)
}

// ---------------- یادآوری تماس ----------------

object Reminders {
    fun schedule(c: Context, number: String, name: String, delayMs: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 1000 + (norm(number).hashCode() and 0xffff),
            Intent(c, ReminderReceiver::class.java).putExtra("number", number).putExtra("name", name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
    }

    fun tomorrowNine(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        return cal.timeInMillis - System.currentTimeMillis()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(c: Context, i: Intent) {
        val num = i.getStringExtra("number") ?: return
        val name = i.getStringExtra("name") ?: ""
        Notifs.channels(c)
        val fl = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val code = norm(num).hashCode() and 0xffff
        val cb = PendingIntent.getBroadcast(c, 2000 + code,
            Intent(c, CallActionReceiver::class.java).setAction(Notifs.A_CALLBACK).putExtra("number", num).putExtra("noclear", true), fl)
        val sms = PendingIntent.getActivity(c, 3000 + code, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))), fl)
        val open = PendingIntent.getActivity(c, 4000 + code,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), fl)
        val b = Notification.Builder(c, "reminder").setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ یادآوری تماس")
            .setContentText("با " + name.ifBlank { Jalali.fa(num) } + " تماس بگیرید")
            .setAutoCancel(true).setContentIntent(open).setCategory(Notification.CATEGORY_REMINDER)
            .addAction(android.R.drawable.ic_menu_call, "تماس", cb)
            .addAction(android.R.drawable.ic_menu_send, "پیامک", sms)
        c.getSystemService(NotificationManager::class.java).notify(100 + (code and 0xff), b.build())
    }
}

// ---------------- سرویس‌ها ----------------

class AppCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart ?: ""
        val db = Db.get(this)
        val hasNumber = number.isNotBlank()
        val reject = incoming && (
            (hasNumber && (
                (Prefs.on("blockedNumbers") && db.isBlocked(number)) ||
                (Prefs.on("rejectGroups") && db.inRejectGroup(number)) ||
                (Prefs.rejectUnknown && lookupName(this, number) == null) ||
                (quietNow() && !db.isFav(number)))) ||
            // حالت «فقط گروه مجاز»: هر تماسی که عضو یک گروه مجاز نباشد رد می‌شود
            (Prefs.on("allowGroups") && db.allowOnlyActive() && !db.inAllowGroup(number)))
        val r = CallResponse.Builder()
        if (reject) r.setDisallowCall(true).setRejectCall(true).setSkipNotification(false).setSkipCallLog(false)
        respondToCall(details, r.build())
    }
}

class AppInCallService : InCallService() {
    private val handler = Handler(Looper.getMainLooper())
    private var beepRounds = 0

    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) { update(null) }
    }

    private fun update(removed: Call?) {
        CallHolder.recompute(calls.filter { it !== removed })
        if (CallHolder.call != null) refresh()
    }

    override fun onCreate() { super.onCreate(); CallHolder.service = this; Notifs.channels(this) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); CallHolder.service = null; super.onDestroy() }

    override fun onCallAudioStateChanged(a: CallAudioState) { applyAudio(a) }

    private fun applyAudio(a: CallAudioState) {
        CallHolder.muted = a.isMuted
        CallHolder.route = a.route
        CallHolder.routeMask = a.supportedRouteMask
    }

    /** روشن‌کردن صفحه هنگام تماس ورودی (حتی در حالت قفل) */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            val wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "callmanager:ring")
            wl.acquire(10_000L)
        } catch (e: Exception) { }
    }

    /** چند بوق کوتاه برای تماس دوم (پشت خط) */
    private fun beeps() { beepRounds = 0; beepRound() }

    private fun beepRound() {
        if (CallHolder.waiting == null || beepRounds >= 4) return
        beepRounds++
        try {
            val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 85)
            for (i in 0..2) handler.postDelayed({ try { tg.startTone(ToneGenerator.TONE_PROP_BEEP, 160) } catch (e: Exception) { } }, i * 380L)
            handler.postDelayed({ try { tg.release() } catch (e: Exception) { } }, 1500L)
        } catch (e: Exception) { }
        handler.postDelayed({ beepRound() }, 4000L)
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        call.registerCallback(cb)
        val isWaiting = call.state == Call.STATE_RINGING && calls.any { it !== call }
        CallHolder.recompute(calls)
        try { callAudioState?.let { applyAudio(it) } } catch (e: Exception) { }
        refresh()
        if (call.state == Call.STATE_RINGING) wakeScreen()
        if (isWaiting && Prefs.on("beeps")) beeps()
        // علاوه بر اعلان تمام‌صفحه، مستقیم هم تلاش می‌کنیم صفحهٔ تماس باز شود
        try { startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }

    /** اعلان تماس: ورودی تمام‌صفحه؛ جاری غیرقابل‌بستن با دکمهٔ قطع */
    @Suppress("DEPRECATION")
    private fun refresh() {
        val call = CallHolder.call ?: return
        val num = call.details.handle?.schemeSpecificPart ?: ""
        val name = lookupName(this, num) ?: Jalali.fa(num).ifBlank { "ناشناس" }
        val ringing = call.state == Call.STATE_RINGING
        val fl = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), fl)
        val answer = PendingIntent.getActivity(this, 12,
            Intent(this, InCallActivity::class.java).putExtra("act", "answer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), fl)
        val decline = Notifs.action(this, Notifs.A_DECLINE, 11)
        val hangup = Notifs.action(this, Notifs.A_HANGUP, 13)
        val wName = CallHolder.waiting?.let { w -> val wn = w.details.handle?.schemeSpecificPart ?: ""; lookupName(this, wn) ?: Jalali.fa(wn) }

        fun build(useStyle: Boolean): Notification {
            val b = Notification.Builder(this, if (ringing) "incoming" else "ongoing")
                .setSmallIcon(if (ringing) android.R.drawable.sym_call_incoming else android.R.drawable.stat_sys_phone_call)
                .setContentTitle(name)
                .setContentText(when {
                    ringing -> "تماس ورودی"
                    wName != null -> "تماس دیگر: $wName"
                    else -> "در حال مکالمه • برای بازگشت به صفحه تماس لمس کنید"
                })
                .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            if (ringing) b.setFullScreenIntent(open, true)
            else {
                val ct = call.details.connectTimeMillis
                if (ct > 0) b.setUsesChronometer(true).setWhen(ct)
            }
            if (useStyle) {
                val person = Person.Builder().setName(name).setImportant(true).build()
                b.setStyle(if (ringing) Notification.CallStyle.forIncomingCall(person, decline, answer) else Notification.CallStyle.forOngoingCall(person, hangup))
            } else if (ringing) {
                b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "رد", decline)
                b.addAction(android.R.drawable.ic_menu_call, "پاسخ", answer)
            } else b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع", hangup)
            val n = b.build()
            n.flags = n.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            return n
        }

        val styled = try { build(true) } catch (e: Exception) { build(false) }
        try {
            startForeground(Notifs.ID_CALL, styled, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            try { getSystemService(NotificationManager::class.java).notify(Notifs.ID_CALL, build(false)) } catch (e2: Exception) { }
        }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(cb)
        Recorder.stop()
        if (call.details.disconnectCause?.code == DisconnectCause.MISSED) {
            val num = call.details.handle?.schemeSpecificPart ?: ""
            Prefs.addMissed(lookupName(this, num) ?: Jalali.fa(num).ifBlank { "ناشناس" }, num)
            Notifs.postMissed(this)
        }
        update(call)
        if (CallHolder.call == null) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) { }
            getSystemService(NotificationManager::class.java).cancel(Notifs.ID_CALL)
        }
    }
}
