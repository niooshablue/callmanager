package ir.callmanager

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallScreeningService
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
    var speaker by mutableStateOf(false)
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
        override fun onStateChanged(call: Call, state: Int) { CallHolder.state = state }
    }

    override fun onCreate() { super.onCreate(); CallHolder.service = this }
    override fun onDestroy() { CallHolder.service = null; super.onDestroy() }

    override fun onCallAudioStateChanged(a: CallAudioState) {
        CallHolder.muted = a.isMuted
        CallHolder.speaker = a.route == CallAudioState.ROUTE_SPEAKER
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(cb)
        CallHolder.call = call
        CallHolder.state = call.state
        val intent = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (call.state == Call.STATE_RINGING) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("incoming", "تماس ورودی", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val num = call.details.handle?.schemeSpecificPart ?: ""
            val n = Notification.Builder(this, "incoming")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("تماس ورودی")
                .setContentText(lookupName(this, num) ?: num)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(pi, true).setContentIntent(pi).setOngoing(true).build()
            nm.notify(1, n)
        } else startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(cb)
        getSystemService(NotificationManager::class.java).cancel(1)
        Recorder.stop()
        if (CallHolder.call == call) CallHolder.call = null
    }
}
