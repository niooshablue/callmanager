package ir.callmanager

import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class InCallActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val call = CallHolder.call
                    LaunchedEffect(call) { if (call == null) finish() }
                    if (call != null) InCallScreen(call)
                }
            }
        }
    }
}

@Composable
fun InCallScreen(call: Call) {
    val ctx = LocalContext.current
    val number = call.details.handle?.schemeSpecificPart ?: ""
    val name = remember(number) { lookupName(ctx, number) }
    val note = remember(number) { Db.get(ctx).note(number) }
    val state = CallHolder.state
    val label = when (state) {
        Call.STATE_RINGING -> "تماس ورودی"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "در حال شماره‌گیری…"
        Call.STATE_ACTIVE -> "در حال مکالمه"
        Call.STATE_HOLDING -> "در انتظار"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "پایان تماس"
        else -> ""
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text(name ?: Jalali.fa(number).ifBlank { "ناشناس" }, fontSize = 32.sp, textAlign = TextAlign.Center)
            if (name != null) Text(Jalali.fa(number), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (note.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("یادداشت", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(note, fontSize = 18.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(CallHolder.muted, { CallHolder.service?.setMuted(!CallHolder.muted) }, label = { Text("بی‌صدا") })
                    FilterChip(CallHolder.speaker, {
                        @Suppress("DEPRECATION")
                        CallHolder.service?.setAudioRoute(if (CallHolder.speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                    }, label = { Text("بلندگو") })
                }
                Spacer(Modifier.height(24.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (state == Call.STATE_RINGING) {
                    CircleBtn("رد", Color(0xFFD32F2F)) { call.reject(false, null) }
                    CircleBtn("پاسخ", Color(0xFF2E7D32)) { call.answer(0) }
                } else CircleBtn("قطع", Color(0xFFD32F2F)) { call.disconnect() }
            }
        }
    }
}

@Composable
fun CircleBtn(text: String, color: Color, onClick: () -> Unit) {
    Button(onClick, Modifier.size(80.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(0.dp)) { Text(text, fontSize = 15.sp, color = Color.White) }
}
