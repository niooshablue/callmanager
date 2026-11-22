package ir.callmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

object CallHolder {
    var call by mutableStateOf<Call?>(null)
    var state by mutableIntStateOf(Call.STATE_NEW)
    var muted by mutableStateOf(false)
    var route by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE)
    var routeMask by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
    var service: InCallService? = null
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
                Notifs.clearMissed(c)
                val s = loadSims(c)
                placeCall(c, n, (s.firstOrNull { it.isDefault } ?: s.firstOrNull())?.handle)
            }
        }
    }
}

// ---------------- سرویس‌ها ----------------

class AppCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart ?: ""
        val reject = incoming && number.isNotBlank() &&
            (Db.get(this).shouldReject(number) || (Prefs.rejectUnknown && lookupName(this, number) == null))
        val r = CallResponse.Builder()
        if (reject) r.setDisallowCall(true).setRejectCall(true).setSkipNotification(false).setSkipCallLog(false)
        respondToCall(details, r.build())
    }
}

class AppInCallService : InCallService() {
    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (call === CallHolder.call) { CallHolder.state = state; refresh() }
        }
    }

    override fun onCreate() { super.onCreate(); CallHolder.service = this; Notifs.channels(this) }
    override fun onDestroy() { CallHolder.service = null; super.onDestroy() }

    override fun onCallAudioStateChanged(a: CallAudioState) { applyAudio(a) }

    private fun applyAudio(a: CallAudioState) {
        CallHolder.muted = a.isMuted
        CallHolder.route = a.route
        CallHolder.routeMask = a.supportedRouteMask
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        call.registerCallback(cb)
        CallHolder.call = call
        CallHolder.state = call.state
        try { callAudioState?.let { applyAudio(it) } } catch (e: Exception) { }
        refresh()
        // علاوه بر اعلان تمام‌صفحه، مستقیم هم تلاش می‌کنیم صفحهٔ تماس باز شود
        try { startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }

    /** اعلان تماس: برای تماس ورودی تمام‌صفحه؛ برای تماس جاری غیرقابل‌بستن با دکمهٔ قطع */
    @Suppress("DEPRECATION")
    private fun refresh() {
        val call = CallHolder.call ?: return
        val num = call.details.handle?.schemeSpecificPart ?: ""
        val name = lookupName(this, num) ?: Jalali.fa(num).ifBlank { "ناشناس" }
        val ringing = call.state == Call.STATE_RINGING
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(this, if (ringing) "incoming" else "ongoing")
            .setSmallIcon(if (ringing) android.R.drawable.sym_call_incoming else android.R.drawable.stat_sys_phone_call)
            .setContentTitle(name)
            .setContentText(if (ringing) "تماس ورودی" else "در حال مکالمه • برای بازگشت به صفحه تماس لمس کنید")
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
        if (ringing) {
            b.setFullScreenIntent(open, true)
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "رد", Notifs.action(this, Notifs.A_DECLINE, 11))
            b.addAction(android.R.drawable.ic_menu_call, "پاسخ", Notifs.action(this, Notifs.A_ANSWER, 12))
        } else {
            val ct = call.details.connectTimeMillis
            if (ct > 0) b.setUsesChronometer(true).setWhen(ct)
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع", Notifs.action(this, Notifs.A_HANGUP, 13))
        }
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        try {
            startForeground(Notifs.ID_CALL, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            getSystemService(NotificationManager::class.java).notify(Notifs.ID_CALL, n)
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
        val next = calls.firstOrNull { it !== call }
        if (CallHolder.call === call) {
            CallHolder.call = next
            if (next != null) CallHolder.state = next.state
        }
        if (CallHolder.call == null) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) { }
            getSystemService(NotificationManager::class.java).cancel(Notifs.ID_CALL)
        } else refresh()
    }
}
