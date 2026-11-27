package ir.callmanager

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.provider.Settings
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
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
        // تماس قطع‌شده را هرگز به‌عنوان تماس جاری انتخاب نکن؛ بعضی نسخه‌های
        // Telecom برای مدت کوتاهی آن را در calls نگه می‌دارند.
        val live = list.filter { it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING }
        val primary = live.firstOrNull {
            it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING || it.state == Call.STATE_PULLING_CALL
        } ?: live.firstOrNull { it.state == Call.STATE_RINGING } ?: live.firstOrNull { it.state == Call.STATE_HOLDING }
        call = primary
        state = primary?.state ?: Call.STATE_NEW
        waiting = if (primary != null && primary.state != Call.STATE_RINGING) live.firstOrNull { it.state == Call.STATE_RINGING && it !== primary } else null
        held = live.firstOrNull { it.state == Call.STATE_HOLDING && it !== primary }
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
    } catch (e: Exception) { null }
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

data class CallReminder(val number: String, val name: String, val atMillis: Long)

object Reminders {
    private const val STORE = "call_reminders"
    private const val KEY = "items"

    private fun items(c: Context): MutableList<CallReminder> {
        val raw = c.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = try { org.json.JSONArray(raw) } catch (_: Exception) { org.json.JSONArray() }
        val out = mutableListOf<CallReminder>()
        for (i in 0 until arr.length()) try {
            val o = arr.getJSONObject(i)
            out += CallReminder(o.getString("number"), o.optString("name", ""), o.getLong("at"))
        } catch (_: Exception) { }
        return out
    }

    private fun save(c: Context, list: List<CallReminder>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("number", it.number).put("name", it.name).put("at", it.atMillis)) }
        c.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun all(c: Context): List<CallReminder> = items(c).sortedBy { it.atMillis }
    fun has(c: Context, number: String): Boolean = items(c).any { norm(it.number) == norm(number) && it.atMillis > System.currentTimeMillis() }
    fun numbers(c: Context): Set<String> = items(c).filter { it.atMillis > System.currentTimeMillis() }.map { norm(it.number) }.toSet()
    fun rescheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        items(c).filter { it.atMillis > System.currentTimeMillis() }.forEach { r ->
            val pi = pending(c, r)
            if (android.os.Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi)
            else am.set(AlarmManager.RTC_WAKEUP, r.atMillis, pi)
        }
    }

    fun schedule(c: Context, number: String, name: String, triggerAtMillis: Long) {
        if (number.isBlank() || triggerAtMillis <= System.currentTimeMillis()) return
        val n = norm(number)
        val list = items(c).toMutableList()
        val reminder = CallReminder(number, name, triggerAtMillis)
        list.removeAll { norm(it.number) == n && it.atMillis == triggerAtMillis }
        list += reminder
        save(c, list)
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c, reminder)
        if (android.os.Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        else am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    private fun pending(c: Context, r: CallReminder): PendingIntent {
        val code = 1000 + (31 * norm(r.number).hashCode() + r.atMillis.hashCode()).and(0x7fffffff) % 100000
        return PendingIntent.getBroadcast(c, code,
            Intent(c, ReminderReceiver::class.java).putExtra("number", r.number).putExtra("name", r.name).putExtra("at", r.atMillis),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun delete(c: Context, number: String, atMillis: Long? = null) {
        val n = norm(number)
        val current = items(c)
        val old = current.filter { norm(it.number) == n && (atMillis == null || it.atMillis == atMillis) }
        val am = c.getSystemService(AlarmManager::class.java)
        old.forEach { am.cancel(pending(c, it)) }
        save(c, current.filterNot { norm(it.number) == n && (atMillis == null || it.atMillis == atMillis) })
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == Intent.ACTION_MY_PACKAGE_REPLACED) Reminders.rescheduleAll(c)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(c: Context, i: Intent) {
        val num = i.getStringExtra("number") ?: return
        val name = i.getStringExtra("name") ?: ""
        // یادآوری پس از نمایش اعلان از فهرست فعال‌ها خارج می‌شود.
        c.getSharedPreferences("call_reminders", Context.MODE_PRIVATE).let { prefs ->
            val arr = try { org.json.JSONArray(prefs.getString("items", "[]") ?: "[]") } catch (_: Exception) { org.json.JSONArray() }
            val kept = org.json.JSONArray()
            val firedAt = i.getLongExtra("at", -1L)
            for (x in 0 until arr.length()) try {
                val o = arr.getJSONObject(x)
                if (!(norm(o.optString("number")) == norm(num) && o.optLong("at", -2L) == firedAt)) kept.put(o)
            } catch (_: Exception) { }
            prefs.edit().putString("items", kept.toString()).apply()
        }
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
        // حالت فیلتر گروه‌ها: 0 خاموش، 1 رد گروه‌های انتخابی، 2 فقط گروه‌های انتخابی مجازند
        // (اگر در حالت 2 هیچ گروهی انتخاب نشده باشد، برای جلوگیری از رد همهٔ تماس‌ها فیلتر اعمال نمی‌شود)
        val allowMode = Prefs.groupMode == 2 && db.allowOnlyActive()
        val allowedByGroup = hasNumber && (db.inAllowGroup(number) || (Prefs.on("allowFavs") && db.isFav(number)))
        val blocked = hasNumber && Prefs.on("blockedNumbers") && db.isBlocked(number)
        val reject = incoming && (
            blocked ||
            if (allowMode) !allowedByGroup
            else (hasNumber && (
                (Prefs.groupMode == 1 && db.inRejectGroup(number)) ||
                (Prefs.rejectUnknown && lookupName(this, number) == null) ||
                (quietNow() && !db.isFav(number))
            ))
        )
        val r = CallResponse.Builder()
        if (reject) r.setDisallowCall(true).setRejectCall(true).setSkipNotification(false).setSkipCallLog(false)
        respondToCall(details, r.build())
    }
}

class AppInCallService : InCallService() {
    private val handler = Handler(Looper.getMainLooper())
    private var beepRounds = 0
    private var popupView: View? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** پنجرهٔ شناور واقعی؛ فقط با مجوز صریح کاربر و هنگام باز بودن قفل گوشی */
    private fun showIncomingPopup(call: Call): Boolean {
        if (!Prefs.on("callPopup") || !Settings.canDrawOverlays(this)) return false
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return false
        removeIncomingPopup()
        return try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val number = call.details.handle?.schemeSpecificPart ?: ""
            val name = lookupName(this, number) ?: Jalali.fa(number).ifBlank { "شماره ناشناس" }
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = when (Prefs.uiStyle) {
                    1 -> GradientDrawable().apply {          // iPhone: شیشهٔ تیره
                        setColor(android.graphics.Color.argb(240, 28, 28, 30)); cornerRadius = dp(26).toFloat()
                        setStroke(dp(1), android.graphics.Color.argb(40, 255, 255, 255))
                    }
                    2 -> GradientDrawable().apply {          // Galaxy: کارت گرد و بزرگ
                        setColor(android.graphics.Color.rgb(32, 32, 34)); cornerRadius = dp(30).toFloat()
                    }
                    else -> GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(android.graphics.Color.rgb(31, 48, 70), android.graphics.Color.rgb(22, 32, 48))).apply {
                        cornerRadius = dp(24).toFloat()
                        setStroke(dp(1), android.graphics.Color.argb(70, 255, 255, 255))
                    }
                }
                elevation = dp(16).toFloat()
            }
            val heading = TextView(this).apply {
                text = "تماس ورودی"
                setTextColor(android.graphics.Color.rgb(170, 205, 255))
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val caller = TextView(this).apply {
                text = name
                setTextColor(android.graphics.Color.WHITE)
                textSize = 20f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                maxLines = 1
            }
            val phone = TextView(this).apply {
                text = Jalali.fa(number)
                setTextColor(android.graphics.Color.rgb(210, 220, 235))
                textSize = 13f
                setPadding(0, dp(3), 0, dp(12))
            }
            root.addView(heading)
            root.addView(caller, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            root.addView(phone)
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            fun action(label: String, color: Int, onClick: () -> Unit): TextView = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                background = GradientDrawable().apply { setColor(color); cornerRadius = dp(if (Prefs.uiStyle == 1) 24 else 16).toFloat() }
                setPadding(dp(8), dp(13), dp(8), dp(13))
                isClickable = true
                isFocusable = true
                setOnClickListener { removeIncomingPopup(); onClick() }
            }
            actions.addView(action("رد تماس", android.graphics.Color.rgb(190, 55, 63)) { call.reject(false, null) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
            actions.addView(action("پاسخ", android.graphics.Color.rgb(32, 145, 91)) { call.answer(0) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
            root.addView(actions)
            val params = WindowManager.LayoutParams(
                minOf(resources.displayMetrics.widthPixels - dp(28), dp(440)).coerceAtLeast(dp(240)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(42)
            }
            wm.addView(root, params)
            popupView = root
            true
        } catch (_: Exception) { popupView = null; false }
    }

    private fun removeIncomingPopup() {
        val view = popupView ?: return
        popupView = null
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) } catch (_: Exception) { }
    }

    /** تماس‌هایی که واقعاً وصل شده‌اند (برای ویبرهٔ وصل/قطع؛ برگشت از حالت انتظار دوباره ویبره نمی‌زند) */
    private val connectedCalls = HashSet<Call>()

    private fun buzz(pattern: LongArray) {
        try {
            val v = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) { }
    }

    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE && connectedCalls.add(call) && Prefs.on("vibConnect")) buzz(longArrayOf(0, 110))
            update(null)
        }
    }

    private fun update(removed: Call?) {
        CallHolder.recompute(calls.filter { it !== removed })
        if (CallHolder.call?.state != Call.STATE_RINGING) removeIncomingPopup()
        if (CallHolder.call != null) refresh() else {
            removeIncomingPopup()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            try { getSystemService(NotificationManager::class.java).cancel(Notifs.ID_CALL) } catch (_: Exception) { }
        }
    }

    override fun onCreate() { super.onCreate(); CallHolder.service = this; Notifs.channels(this) }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeIncomingPopup()
        connectedCalls.clear()
        CallHolder.service = null
        // جلوگیری از باقی‌ماندن وضعیت تماس قبلی پس از قطع سرویس
        CallHolder.call = null
        CallHolder.waiting = null
        CallHolder.held = null
        CallHolder.state = Call.STATE_NEW
        CallHolder.muted = false
        Recorder.stop()
        super.onDestroy()
    }

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
        if (call.state == Call.STATE_ACTIVE) connectedCalls.add(call)
        val isWaiting = call.state == Call.STATE_RINGING && calls.any { it !== call }
        CallHolder.recompute(calls)
        try { callAudioState?.let { applyAudio(it) } } catch (e: Exception) { }
        if (call.state == Call.STATE_RINGING) wakeScreen()
        if (isWaiting && Prefs.on("beeps")) beeps()
        val popupShown = call.state == Call.STATE_RINGING && Prefs.on("callPopup") && showIncomingPopup(call)
        refresh()
        // تماس خروجی نیز باید بلافاصله صفحه مکالمه اختصاصی را باز کند؛
        // در تماس ورودی از کارت شناور یا رابط تمام‌صفحه سیستم استفاده می‌شود.
        val outgoingOrActive = call.state != Call.STATE_RINGING
        if (outgoingOrActive || (Prefs.on("callPopup") && !popupShown)) {
            try { startActivity(Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) } catch (_: Exception) { }
        }
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
            if (ringing && popupView == null) b.setFullScreenIntent(open, true)
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
        removeIncomingPopup()
        call.unregisterCallback(cb)
        if (connectedCalls.remove(call) && Prefs.on("vibEnd")) buzz(longArrayOf(0, 80, 90, 80))
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
