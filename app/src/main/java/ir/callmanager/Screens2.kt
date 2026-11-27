@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

// ---------------- جزئیات مخاطب ----------------

@Composable
fun DetailScreen(target: DetailTarget, onBack: () -> Unit) {
    val ctx = LocalContext.current
    // پس از افزودن شماره به مخاطبین و بازگشت، فهرست مخاطبین دوباره بارگذاری می‌شود و اینجا خودکار به‌روز می‌شود
    val t = remember(Repo.contacts, target) { if (target.contactId > 0) target else targetFor(target.number, target.name) }
    val db = remember { Db.get(ctx) }
    val cs = MaterialTheme.colorScheme
    val num = t.number
    var note by remember { mutableStateOf(db.note(num)) }
    var blocked by remember { mutableStateOf(db.isBlocked(num)) }
    val groups = remember { db.groups() }
    val mem = remember { mutableStateListOf<Int>().apply { addAll(db.memberGroups(num)) } }
    var ref by remember { mutableStateOf(db.referrer(num)) }
    var picking by remember { mutableStateOf(false) }
    var fav by remember { mutableStateOf(db.isFav(num)) }
    var remindDlg by remember { mutableStateOf(false) }
    var showJalaliDatePicker by remember { mutableStateOf(false) }
    var reminderTick by remember { mutableIntStateOf(0) }
    val activeReminder = remember(num, reminderTick) { Reminders.all(ctx).firstOrNull { norm(it.number) == norm(num) } }
    var confirmDel by remember { mutableStateOf<List<Long>?>(null) }
    val deleter = rememberCallDeleter()
    val history = remember(Repo.calls, num) { Repo.calls.filter { norm(it.number) == norm(num) } }

    fun save() {
        db.setNote(num, note); db.setMembers(num, mem); db.setBlocked(num, blocked)
        db.setReferrer(num, ref?.first, ref?.second); db.setFav(num, fav); dbTick.intValue++
    }
    BackHandler { save(); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("جزئیات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton({ save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.navigationBarsPadding()) {
                    Button({ save(); Toast.makeText(ctx, "ذخیره شد", Toast.LENGTH_SHORT).show() },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp), shape = RoundedCornerShape(26.dp)) {
                        Text("ذخیره", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(t.name.ifBlank { num }, 96.dp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(t.name.ifBlank { Jalali.fa(num) }, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (activeReminder != null) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.NotificationsActive, "یادآوری فعال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                }
                if (t.name.isNotBlank()) Text(Jalali.fa(num), color = cs.onSurfaceVariant)
                if (mem.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.filter { it.id in mem }.forEach { g -> AssistChip({}, label = { Text(g.name, fontSize = 12.sp) }) }
                }
            }
            SimCallRow(num)
            // شمارهٔ ناشناس (ذخیره‌نشده): افزودن سریع به مخاطبین
            if (t.contactId <= 0 && num.isNotBlank()) {
                Button({ addToContacts(ctx, num) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.secondary, contentColor = cs.onSecondary)) {
                    Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp))
                    Text("افزودن به مخاطبین", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(Icons.Default.Email, "پیامک") { sendSms(ctx, num) }
                if (t.contactId > 0) QuickTile(Icons.Default.Edit, "ویرایش") {
                    ctx.startActivity(Intent(Intent.ACTION_EDIT).setData(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, t.contactId)))
                }
                QuickTile(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "علاقه‌مندی", active = fav) { fav = !fav }
                QuickTile(Icons.Default.ContentCopy, "کپی") {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("number", num))
                    Toast.makeText(ctx, "شماره کپی شد", Toast.LENGTH_SHORT).show()
                }
                QuickTile(Icons.Default.Alarm, "یادآوری", active = activeReminder != null) { remindDlg = true }
            }

            if (activeReminder != null) {
                SectionCard("یادآوری فعال") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("تماس با این مخاطب")
                            Text(Jalali.format(activeReminder.atMillis),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton({ Reminders.delete(ctx, num, activeReminder.atMillis); reminderTick++; Toast.makeText(ctx, "یادآوری حذف شد", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Default.DeleteOutline, "حذف یادآوری", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            SectionCard("📝 یادداشت (هنگام تماس نمایش داده می‌شود)") {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("یادداشت…") })
            }
            SectionCard("گروه‌ها") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        FilterChip(g.id in mem, { if (g.id in mem) mem.remove(g.id) else mem.add(g.id) }, label = { Text(g.name) })
                    }
                }
            }
            SectionCard("معرف") {
                if (ref == null) Text("معرفی انتخاب نشده است", color = cs.onSurfaceVariant, fontSize = 13.sp)
                else Text(ref!!.second.ifBlank { "بدون نام" } + "  " + Jalali.fa(ref!!.first), fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ picking = true }) { Text("انتخاب از مخاطبین") }
                    if (ref != null) TextButton({ ref = null }) { Text("حذف معرف") }
                }
            }
            SectionCard("رد خودکار تماس") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تماس‌های این شماره خودکار رد شود", Modifier.weight(1f))
                    Switch(blocked, { blocked = it })
                }
            }

            val total = history.sumOf { it.duration }
            val talked = history.count { it.duration > 0 }
            SectionCard("آمار مکالمه") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("تعداد تماس", Jalali.fa(history.size))
                    Stat("مجموع زمان", fmtDur(total))
                    Stat("میانگین", fmtDur(if (talked > 0) total / talked else 0L))
                }
                val cur = Jalali.monthKey(System.currentTimeMillis())
                val keys = (cur - 5)..cur
                val sums = keys.map { k -> history.filter { Jalali.monthKey(it.date) == k }.sumOf { it.duration } }
                val mx = (sums.maxOrNull() ?: 0L).coerceAtLeast(1L)
                Text("زمان صحبت در ۶ ماه اخیر", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    keys.forEachIndexed { i, k ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (sums[i] > 0) Text(fmtMin(sums[i]), fontSize = 10.sp)
                            Box(Modifier.fillMaxWidth().height((110f * sums[i].toFloat() / mx.toFloat()).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(Brush.verticalGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.45f)))))
                            Text(Jalali.monthName(k), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
            SectionCard("سابقهٔ تماس‌ها") {
                if (history.isEmpty()) Text("تماسی ثبت نشده است", color = cs.onSurfaceVariant, fontSize = 13.sp)
                history.take(100).forEach { r ->
                    val simIdx = Repo.sims.indexOfFirst { it.handle.id == r.simId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CallTypeBadge(r.type, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Jalali.format(r.date), fontSize = 13.sp)
                            Text(typeName(r.type) + (if (r.duration > 0) " • " + fmtDur(r.duration) else "") +
                                (if (simIdx >= 0 && Repo.sims.size > 1) " • SIM " + Jalali.fa(simIdx + 1) else ""),
                                fontSize = 11.sp, color = cs.onSurfaceVariant)
                        }
                        IconButton({ confirmDel = listOf(r.id) }) { Icon(Icons.Default.Delete, "حذف", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
                    }
                }
                if (history.isNotEmpty()) TextButton({ confirmDel = history.map { it.id } }) {
                    Text("حذف همهٔ سابقهٔ این مخاطب", color = Color(0xFFD32F2F))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (remindDlg) {
        fun chooseCustomDateTime() { showJalaliDatePicker = true }
        AlertDialog(onDismissRequest = { remindDlg = false }, title = { Text("یادآوری تماس با " + t.name.ifBlank { Jalali.fa(num) }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("زمان یادآوری را انتخاب کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    listOf("۱۵ دقیقه دیگر" to 15L * 60_000L, "یک ساعت دیگر" to 60L * 60_000L).forEach { (label, delay) ->
                        OutlinedButton({ Reminders.schedule(ctx, num, t.name, System.currentTimeMillis() + delay); reminderTick++; remindDlg = false
                            Toast.makeText(ctx, "یادآوری تنظیم شد", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) { Text(label) }
                    }
                    Button({ chooseCustomDateTime() }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("انتخاب تاریخ و ساعت دلخواه")
                    }
                }
            }, confirmButton = {}, dismissButton = { TextButton({ remindDlg = false }) { Text("انصراف") } })
    }
    if (showJalaliDatePicker) JalaliDatePickerDialog(
        onDismiss = { showJalaliDatePicker = false },
        onDateSelected = { jy, jm, jd ->
            val (gy, gm, gd) = Jalali.toGregorian(jy, jm, jd)
            val date = Calendar.getInstance().apply { set(gy, gm - 1, gd, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            val now = Calendar.getInstance()
            TimePickerDialog(ctx, { _, hour, minute ->
                date.set(Calendar.HOUR_OF_DAY, hour); date.set(Calendar.MINUTE, minute)
                if (date.timeInMillis > System.currentTimeMillis()) {
                    Reminders.schedule(ctx, num, t.name, date.timeInMillis); reminderTick++
                    Toast.makeText(ctx, "یادآوری ثبت شد", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(ctx, "زمان انتخابی باید در آینده باشد", Toast.LENGTH_LONG).show()
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            showJalaliDatePicker = false
            remindDlg = false
        }
    )
    if (picking) ContactPicker({ c -> ref = Pair(c.number, c.name); picking = false }, { picking = false })
    confirmDel?.let { ids ->
        AlertDialog(onDismissRequest = { confirmDel = null }, title = { Text("حذف از تاریخچه") },
            text = { Text(Jalali.fa("${ids.size} مورد از سابقهٔ تماس حذف شود؟")) },
            confirmButton = { TextButton({ deleter(ids); confirmDel = null }) { Text("حذف", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ confirmDel = null }) { Text("انصراف") } })
    }
}

@Composable
fun JalaliDatePickerDialog(onDismiss: () -> Unit, onDateSelected: (Int, Int, Int) -> Unit) {
    val today = Calendar.getInstance().timeInMillis
    val (todayYear, todayMonth, todayDay) = Jalali.parts(today)
    var year by remember { mutableIntStateOf(todayYear) }
    var month by remember { mutableIntStateOf(todayMonth) }
    var selectedDay by remember { mutableIntStateOf(todayDay) }
    val first = remember(year, month) {
        val (gy, gm, gd) = Jalali.toGregorian(year, month, 1)
        Calendar.getInstance().apply { set(gy, gm - 1, gd) }.get(Calendar.DAY_OF_WEEK) % 7
    }
    val days = Jalali.daysInMonth(year, month)
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (month == 1) { month = 12; year-- } else month--; selectedDay = 1 }) { Icon(Icons.Default.ChevronRight, "ماه قبل") }
                Text("${Jalali.months[month - 1]} ${Jalali.fa(year)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++; selectedDay = 1 }) { Icon(Icons.Default.ChevronLeft, "ماه بعد") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                }
                val cells = List(first) { 0 } + (1..days).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                                if (day > 0) {
                                    if (day == selectedDay) FilledTonalButton(onClick = { selectedDay = day },
                                        modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) { Text(Jalali.fa(day), fontSize = 12.sp) }
                                    else TextButton(onClick = { selectedDay = day }, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) { Text(Jalali.fa(day), fontSize = 12.sp) }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDateSelected(year, month, selectedDay) }) { Text("تأیید تاریخ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ContactPicker(onPick: (Contact) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(q) {
        val qn = Jalali.en(q).trim().lowercase(); val qd = qn.filter { it.isDigit() }
        Repo.contacts.filter { qn.isEmpty() || it.lname.contains(qn) || (qd.isNotEmpty() && it.digits.contains(qd)) }.take(60)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("انتخاب معرف") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("جستجو") })
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list) { c ->
                        ListItem(modifier = Modifier.clickable { onPick(c) }, headlineContent = { Text(c.name) },
                            supportingContent = { Text(Jalali.fa(c.number)) })
                    }
                }
            }
        }, confirmButton = { TextButton(onDismiss) { Text("بستن") } })
}

// ---------------- تنظیمات چندسطحی ----------------

@Composable
private fun RowScope.QuickTile(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Column(Modifier.weight(1f).clip(RoundedCornerShape(look.row)).background(if (active) cs.primaryContainer else cs.surfaceContainerHigh)
        .clickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (active) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, maxLines = 1, color = if (active) cs.onPrimaryContainer else cs.onSurface)
    }
}

/** پیش‌نمایش کوچک و ثابت از ظاهر هر قالب */
@Composable
private fun MiniPhone(style: Int) {
    val bg = when (style) { 1 -> Color(0xFFF2F2F7); 2 -> Color(0xFFF4F4F4); else -> Color(0xFFF5F7FB) }
    val accent = when (style) { 1 -> Color(0xFF007AFF); 2 -> Color(0xFF2F82F5); else -> Color(0xFF3B5BDB) }
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(72.dp, 128.dp).clip(shape).background(bg).border(1.dp, Color.Gray.copy(alpha = 0.4f), shape)) {
        when (style) {
            1 -> Box(Modifier.padding(8.dp, 10.dp).width(36.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.8f)))
            2 -> Box(Modifier.padding(8.dp, 20.dp).width(30.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.75f)))
            else -> Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(accent.copy(alpha = 0.35f)))
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(if (style == 1) 2.dp else 7.dp)).background(Color.White)) }
        }
        when (style) {
            0 -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(56.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White)) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 3.dp).size(10.dp).clip(CircleShape).background(accent))
            }
            1 -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(58.dp).height(16.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White).border(0.5.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)))
            else -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(18.dp).background(Color.White)) {
                Box(Modifier.align(Alignment.Center).width(16.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.35f)))
            }
        }
    }
}

@Composable
private fun PresetCard(style: Int, title: String, desc: String, current: Boolean, onApply: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(look.card),
        colors = CardDefaults.cardColors(containerColor = if (current) cs.primaryContainer else cs.surfaceContainerHigh)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniPhone(style)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(desc, fontSize = 12.sp, color = if (current) cs.onPrimaryContainer else cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (current) Text("✓ فعال است", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                else Button(onApply, shape = RoundedCornerShape(20.dp)) { Text("اعمال روی کل برنامه") }
            }
        }
    }
}

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ColorRow(title: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { c ->
                val sel = c == selected
                Box(Modifier.size(38.dp).clip(CircleShape)
                    .background(if (c == 0) MaterialTheme.colorScheme.surfaceVariant else Color(c))
                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f), CircleShape)
                    .clickable { onSelect(c) }, contentAlignment = Alignment.Center) {
                    if (c == 0) Text("پ", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var path by rememberSaveable { mutableStateOf("") }
    val back = { path = path.substringBeforeLast('/', "") }
    BackHandler(path.isNotEmpty()) { back() }

    when (path) {
        "" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuItem(Icons.Default.Groups, "مدیریت گروه‌ها", "رد تماس گروه‌های انتخابی یا فقط گروه‌های مجاز", Color(0xFF7E57C2)) { path = "groups" }
            MenuItem(Icons.Default.Palette, "ظاهر و قالب‌ها", "iPhone 17، Galaxy S26، شماره‌گیر، صفحه تماس و رنگ‌ها", Color(0xFFEC407A)) { path = "look" }
            MenuItem(Icons.Default.Call, "تماس و سیم‌کارت", "سیم‌کارت پیش‌فرض، مسدودسازی، صفحه قفل", Color(0xFF26A69A)) { path = "calls" }
            MenuItem(Icons.Default.Extension, "ویژگی‌ها", "روشن/خاموش‌کردن تک‌تک امکانات برنامه", Color(0xFFFF7043)) { path = "features" }
            MenuItem(Icons.Default.NotificationsActive, "یادآوری‌های تماس", "مشاهده و حذف یادآوری‌های ثبت‌شده", Color(0xFFFFA000)) { path = "reminders" }
            MenuItem(Icons.Default.Notifications, "اعلان‌ها", "تماس‌های بی‌پاسخ و نشان آیکون", Color(0xFF42A5F5)) { path = "notif" }
            MenuItem(Icons.Default.TouchApp, "حرکات و لمس", "لمس کوتاه، بلند و کشیدن در تاریخچه و مخاطبین", Color(0xFF8D6E63)) { path = "gest" }
            MenuItem(Icons.Default.Storage, "داده‌ها", "خروجی اکسل و مکالمات ضبط‌شده", Color(0xFF607D8B)) { path = "data" }
            MenuItem(Icons.Default.Info, "دربارهٔ برنامه", "نسخه و توضیحات", Color(0xFF78909C)) { path = "about" }
        }
        "groups" -> SubPage("مدیریت گروه‌ها", back) { GroupsScreen() }
        "reminders" -> SubPage("یادآوری‌های تماس", back) { ReminderListContent() }
        "features" -> SubPage("ویژگی‌ها", back) {
            Prefs.features.groupBy { it.section }.forEach { (section, items) ->
                SectionCard(section) {
                    items.forEach { f -> SettingSwitch(f.label, null, Prefs.on(f.key)) { Prefs.setFlag(f.key, it) } }
                }
            }
            Text("هر مورد غیرفعال شود، آن بخش نمایش داده نمی‌شود یا اجرا نمی‌شود. تنظیمات اعلان تماس بی‌پاسخ، «مزاحم نشو»، رد تماس ناشناس‌ها و حالت فیلتر گروه‌ها در بخش‌های خودشان هستند.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "look" -> SubPage("ظاهر و قالب‌ها", back) {
            MenuItem(Icons.Default.AutoAwesome, "قالب‌های آماده", "یک‌کلیک: iPhone 17، Galaxy S26 یا مدرن برای کل برنامه", Color(0xFF5C6BC0)) { path = "look/presets" }
            MenuItem(Icons.Default.Smartphone, "ساختار برنامه", "تم روشن/تیره، سربرگ بالا و منوی پایین", Color(0xFF26A69A)) { path = "look/app" }
            MenuItem(Icons.Default.Dialpad, "شماره‌گیر", "قالب کلیدها، لرزش، صدا و امکانات", Color(0xFFEF5350)) { path = "look/dialer" }
            MenuItem(Icons.Default.PhoneInTalk, "صفحه تماس", "چیدمان، پس‌زمینه، شیوهٔ پاسخ و پیش‌نمایش", Color(0xFF66BB6A)) { path = "look/call" }
            MenuItem(Icons.Default.Palette, "رنگ‌ها", "پس‌زمینه، کادرها، نوشته‌ها و رنگ اصلی", Color(0xFFEC407A)) { path = "look/colors" }
            MenuItem(Icons.Default.TextFields, "نوشته و اعداد", "اندازه، فونت و نمایش فارسی/انگلیسی اعداد", Color(0xFF8D6E63)) { path = "look/text" }
            OutlinedButton({ Prefs.reset() }, Modifier.fillMaxWidth()) { Text("بازگشت رنگ‌ها و فونت به پیش‌فرض") }
        }
        "look/presets" -> SubPage("قالب‌های آماده", { path = "look" }) {
            Text("با انتخاب هر قالب، ظاهر کل برنامه، سربرگ و منوی پایین، شماره‌گیر، صفحه تماس و شیوهٔ پاسخ یک‌جا تغییر می‌کند. بعد از آن می‌توانید هر بخش را جداگانه هم تغییر دهید.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val cur = if (Prefs.uiStyle == Prefs.callLayout) Prefs.uiStyle else -1
            PresetCard(1, "iPhone 17", "الهام‌گرفته از iOS: عنوان بزرگ، منوی شیشه‌ای شناور، فهرست تخت، کلیدهای گرد با حروف، صفحه تماس شیشه‌ای و پاسخ کشویی", cur == 1) {
                Prefs.applyPreset(1); Toast.makeText(ctx, "قالب iPhone 17 اعمال شد", Toast.LENGTH_SHORT).show()
            }
            PresetCard(2, "Galaxy S26", "الهام‌گرفته از One UI: گوشه‌های گرد و بزرگ، منوی پایین با نشانگر قرصی، صفحه تماس با آواتار بزرگ و پاسخ با کشیدن دایره‌ها", cur == 2) {
                Prefs.applyPreset(2); Toast.makeText(ctx, "قالب Galaxy S26 اعمال شد", Toast.LENGTH_SHORT).show()
            }
            PresetCard(0, "مدرن (پیش‌فرض)", "سربرگ گرادیان، منوی شناور با برچسب متحرک، کارت‌های نرم و رنگ‌های پویای سیستم", cur == 0) {
                Prefs.applyPreset(0); Toast.makeText(ctx, "قالب مدرن اعمال شد", Toast.LENGTH_SHORT).show()
            }
            Text("اعمال قالب، رنگ‌های سفارشی (پس‌زمینه، کارت، نوشته و رنگ اصلی) را هم به حالت پیش‌فرض قالب برمی‌گرداند. این قالب‌ها فقط از نظر ظاهر الهام گرفته‌اند و فونت و آیکون‌های اختصاصی آن سیستم‌ها را ندارند.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "look/app" -> SubPage("ساختار برنامه", { path = "look" }) {
            SectionCard("تم روشن / تیره") {
                OptionChips(listOf("مطابق سیستم", "روشن", "تیره"), Prefs.darkMode) { Prefs.darkMode = it; Prefs.save() }
            }
            SectionCard("قالب کلی برنامه") {
                OptionChips(listOf("مدرن", "iPhone 17", "Galaxy S26"), Prefs.uiStyle) { Prefs.uiStyle = it; Prefs.save() }
                Text("رنگ‌ها، گوشه‌ها، فهرست‌ها و کارت‌ها از قالب پیروی می‌کنند. اگر رنگ سفارشی انتخاب کرده‌اید، روی رنگ قالب اولویت دارد.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingSwitch("رنگ‌های پویای سیستم (Material You)", "فقط در قالب «مدرن»", Prefs.on("dynamicColor")) { Prefs.setFlag("dynamicColor", it) }
            }
            SectionCard("سربرگ بالای صفحه‌ها") {
                OptionChips(listOf("خودکار (مطابق قالب)", "گرادیان مدرن", "عنوان بزرگ iPhone", "Galaxy", "فشرده"), Prefs.headerStyle + 1) { Prefs.headerStyle = it - 1; Prefs.save() }
                SettingSwitch("نمایش تاریخ زیر عنوان", null, Prefs.on("headerDate")) { Prefs.setFlag("headerDate", it) }
            }
            SectionCard("منوی پایین") {
                OptionChips(listOf("خودکار (مطابق قالب)", "شناور مدرن", "شیشه‌ای iPhone", "چسبیده Galaxy", "کلاسیک"), Prefs.navStyle + 1) { Prefs.navStyle = it - 1; Prefs.save() }
            }
        }
        "look/dialer" -> SubPage("شماره‌گیر", { path = "look" }) {
            SectionCard("قالب صفحه‌کلید") {
                OptionChips(listOf("کلاسیک", "دایره‌ای", "مینیمال", "iPhone", "Galaxy"), Prefs.dialerStyle) { Prefs.dialerStyle = it; Prefs.save() }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Keypad(Modifier.widthIn(max = 300.dp), keyHeight = 40.dp, style = Prefs.dialerStyle, onKey = {})
                }
            }
            SectionCard("امکانات شماره‌گیر") {
                Prefs.features.filter { it.section == "شماره‌گیر" }.forEach { f ->
                    SettingSwitch(f.label, null, Prefs.on(f.key)) { Prefs.setFlag(f.key, it) }
                }
                Text("به‌صورت پیش‌فرض با لمس کادر شماره، کیبورد گوشی باز نمی‌شود. برای جای‌گذاری، دکمهٔ Paste یا لمس طولانی روی کادر را بزنید.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "look/call" -> SubPage("صفحه تماس", { path = "look" }) {
            SectionCard("چیدمان صفحه تماس") {
                OptionChips(listOf("مدرن", "iPhone", "Galaxy"), Prefs.callLayout) { Prefs.callLayout = it; Prefs.save() }
                Button({ Nav.callPreview = 0 }, Modifier.fillMaxWidth()) { Text("پیش‌نمایش تماس ورودی") }
                OutlinedButton({ Nav.callPreview = 1 }, Modifier.fillMaxWidth()) { Text("پیش‌نمایش در حال مکالمه") }
            }
            SectionCard("پس‌زمینهٔ صفحه تماس") {
                OptionChips(listOf("رنگی / گرادیان", "ساده", "تیره (AMOLED)"), Prefs.callTheme) { Prefs.callTheme = it; Prefs.save() }
                SettingSwitch("همیشه تیره", "صفحهٔ تماس با رنگ‌های تیره نمایش داده شود", Prefs.callDark) { Prefs.callDark = it; Prefs.save() }
                Text("«تیره (AMOLED)» پس‌زمینهٔ کاملاً مشکی دارد. در چیدمان‌های iPhone و Galaxy حالت «رنگی» همان پس‌زمینهٔ تیرهٔ مخصوص آن قالب است و «ساده» از تم برنامه پیروی می‌کند.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard("شیوهٔ پاسخ به تماس") {
                OptionChips(answerStyleNames, Prefs.answerStyle) { Prefs.answerStyle = it; Prefs.save() }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF101820)).padding(16.dp)) {
                    AnswerControls(Prefs.answerStyle, {}, {})
                }
                Text("پیش‌نمایش قابل امتحان است؛ در تماس واقعی همین حرکت پاسخ می‌دهد.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard("دکمه‌ها و اطلاعات صفحه تماس") {
                Prefs.features.filter { it.section == "صفحه تماس" }.forEach { f ->
                    SettingSwitch(f.label, null, Prefs.on(f.key)) { Prefs.setFlag(f.key, it) }
                }
            }
        }
        "look/colors" -> SubPage("رنگ‌ها", { path = "look" }) {
            SectionCard("رنگ‌بندی") {
                ColorRow("رنگ پس‌زمینه", Prefs.bgPresets, Prefs.bg) { Prefs.bg = it; Prefs.save() }
                ColorRow("رنگ کادرها (کارت‌ها)", Prefs.cardPresets, Prefs.card) { Prefs.card = it; Prefs.save() }
                ColorRow("رنگ نوشته‌ها", Prefs.textPresets, Prefs.text) { Prefs.text = it; Prefs.save() }
                ColorRow("رنگ اصلی (دکمه‌ها و تأکید)", Prefs.accentPresets, Prefs.accent) { Prefs.accent = it; Prefs.save() }
            }
            SectionCard("پیش‌نمایش") { Text("نمونه متن: سلام، این یک متن آزمایشی است ۱۲۳۴۵") }
        }
        "look/text" -> SubPage("نوشته و اعداد", { path = "look" }) {
            SectionCard("اندازهٔ نوشته‌ها") {
                Text(Jalali.fa("${(Prefs.fontScale * 100).toInt()}٪"), fontSize = 13.sp)
                Slider(Prefs.fontScale, { Prefs.fontScale = it }, valueRange = 0.8f..1.6f, onValueChangeFinished = { Prefs.save() })
            }
            SectionCard("فونت نوشته‌ها") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prefs.fontNames.forEachIndexed { i, n ->
                        FilterChip(Prefs.font == i, { Prefs.font = i; Prefs.save() }, label = { Text(n) })
                    }
                    Text("فونت‌های همراه برنامه متن‌باز هستند و برای نمایش فارسی بهینه شده‌اند. نام‌های وزیری‌مانند، یکان‌مانند و نازنین‌مانند توصیف سبک بصری‌اند و به معنی استفاده از فایل تجاری همان فونت‌ها نیست.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard("نمایش اعداد") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(Prefs.persian, { Prefs.persian = true; Prefs.save() }, label = { Text("فارسی ۱۲۳") })
                    FilterChip(!Prefs.persian, { Prefs.persian = false; Prefs.save() }, label = { Text("English 123") })
                }
            }
            SectionCard("پیش‌نمایش") { Text("نمونه متن: سلام، این یک متن آزمایشی است ۱۲۳۴۵", fontSize = 15.sp) }
        }
        "calls" -> SubPage("تماس و سیم‌کارت", back) {
            MenuItem("📶", "سیم‌کارت‌ها", "سیم‌کارت پیش‌فرض تماس") { path = "calls/sim" }
            MenuItem("🚫", "مسدودسازی", "شماره‌های مسدود و رد خودکار ناشناس‌ها") { path = "calls/block" }
            MenuItem("🌙", "حالت مزاحم نشو", "رد خودکار تماس در ساعات مشخص") { path = "calls/quiet" }
            MenuItem("💬", "پیامک‌های آماده رد تماس", "متن‌هایی که هنگام زنگ‌خوردن برای رد تماس انتخاب می‌کنید") { path = "calls/replies" }
            MenuItem("🔒", "نمایش روی صفحه قفل", "مجوز اعلان تمام‌صفحه و روشن‌شدن صفحه") { path = "calls/lock" }
            MenuItem("🪟", "پنجره شناور و اجرای پس‌زمینه", "کارت پاسخ سریع روی برنامه‌ها و مدیریت محدودیت باتری") { path = "calls/popup" }
        }
        "calls/sim" -> SubPage("سیم‌کارت‌ها", { path = "calls" }) {
            SectionCard("سیم‌کارت‌های شناسایی‌شده") {
                if (Repo.sims.isEmpty()) Text("سیم‌کارتی شناسایی نشد (دسترسی تلفن را بررسی کنید).", fontSize = 13.sp)
                Repo.sims.forEach { s -> Text("SIM ${Jalali.fa(s.index)} · ${s.label}" + if (s.isDefault) "  ★ پیش‌فرض تماس" else "", fontSize = 14.sp) }
                Text("سیم‌کارت پیش‌فرض از تنظیمات گوشی خوانده می‌شود. اگر روی «هر بار بپرس» باشد، هنگام تماس از شما سؤال می‌شود.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({
                    try { ctx.startActivity(Intent("android.telecom.action.CHANGE_PHONE_ACCOUNTS")) }
                    catch (e: Exception) { try { ctx.startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) } catch (e2: Exception) { } }
                }) { Text("باز کردن تنظیمات سیم‌کارت گوشی") }
            }
        }
        "calls/block" -> SubPage("مسدودسازی", { path = "calls" }) {
            SectionCard("شماره‌های ناشناس") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("رد تماس شماره‌های ناشناس")
                        Text("تماس شماره‌هایی که در مخاطبین نیستند خودکار رد می‌شود", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(Prefs.rejectUnknown, { Prefs.rejectUnknown = it; Prefs.save() })
                }
            }
            BlockedListCard()
            Text("رد خودکار گروه‌ها و شماره‌های مشخص را از بخش «مدیریت گروه‌ها» در تنظیمات و جزئیات هر مخاطب تنظیم کنید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "calls/quiet" -> SubPage("حالت مزاحم نشو", { path = "calls" }) {
            SectionCard("رد خودکار در ساعات مشخص") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("فعال", Modifier.weight(1f)); Switch(Prefs.quietOn, { Prefs.quietOn = it; Prefs.save() })
                }
                Text(Jalali.fa("از ساعت ${Prefs.quietStart}:00 تا ${Prefs.quietEnd}:00"), fontWeight = FontWeight.Bold)
                Text("ساعت شروع", fontSize = 12.sp)
                Slider(Prefs.quietStart.toFloat(), { Prefs.quietStart = it.toInt() }, valueRange = 0f..23f, steps = 22, onValueChangeFinished = { Prefs.save() })
                Text("ساعت پایان", fontSize = 12.sp)
                Slider(Prefs.quietEnd.toFloat(), { Prefs.quietEnd = it.toInt() }, valueRange = 0f..23f, steps = 22, onValueChangeFinished = { Prefs.save() })
                Text("در این بازه تماس همهٔ شماره‌ها خودکار رد می‌شود، به‌جز مخاطبینی که «علاقه‌مندی» علامت زده‌اید. رد شدن در تاریخچه ثبت می‌شود.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "calls/replies" -> SubPage("پیامک‌های آماده رد تماس", { path = "calls" }) {
            val list = Prefs.quickReplies
            SectionCard("پیام‌ها") {
                list.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(m, { v ->
                            Prefs.quickReplies = list.toMutableList().also { it[i] = v.take(160) }; Prefs.saveReplies()
                        }, Modifier.weight(1f), minLines = 1, maxLines = 4)
                        IconButton({ Prefs.quickReplies = list.filterIndexed { j, _ -> j != i }; Prefs.saveReplies() }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ Prefs.quickReplies = list + "پیام جدید"; Prefs.saveReplies() }) { Text("افزودن پیام") }
                    TextButton({ Prefs.quickReplies = Prefs.defaultReplies; Prefs.saveReplies() }) { Text("بازگشت به پیش‌فرض") }
                }
                Text("حداکثر ۱۶۰ نویسه. هنگام تماس ورودی، ابتدا فهرست پیام‌ها باز می‌شود؛ سپس پس از انتخاب پیام و تأیید نهایی، تماس رد و پیامک ارسال خواهد شد.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "calls/lock" -> SubPage("نمایش روی صفحه قفل", { path = "calls" }) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val ok = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
            SectionCard("اعلان تمام‌صفحه") {
                Text(if (ok) "✅ مجوز فعال است؛ تماس ورودی روی صفحه قفل نمایش داده می‌شود." else "⚠️ مجوز فعال نیست؛ برای روشن‌شدن صفحه هنگام تماس لازم است.")
                if (!ok) Button({
                    ctx.startActivity(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + ctx.packageName)))
                }) { Text("باز کردن تنظیمات مجوز") }
                Text("اگر هنوز صفحه روشن نمی‌شود: در تنظیمات گوشی برای این برنامه «نمایش روی صفحه قفل»، «باز شدن پنجره در پس‌زمینه» (شیائومی، سامسونگ و…) و «بدون محدودیت باتری» را فعال کنید.", fontSize = 12.sp)
                OutlinedButton({
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                }) { Text("باز کردن تنظیمات برنامه") }
                if (!android.provider.Settings.canDrawOverlays(ctx)) OutlinedButton({
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                }) { Text("اجازهٔ نمایش روی سایر برنامه‌ها") }
                Text("اگر گوشی باز و در حال استفاده باشد، ممکن است اندروید یا سازندهٔ گوشی به‌جای تمام‌صفحه یک اعلان بالای صفحه نشان دهد.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "calls/popup" -> SubPage("پنجره شناور و اجرای پس‌زمینه", { path = "calls" }) {
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            val batteryOk = Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(ctx.packageName)
            val overlayOk = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(ctx)
            SectionCard("نمایش پنجره تماس") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("نمایش کارت شناور هنگام استفاده از برنامه‌های دیگر")
                        Text("یک کارت کوچک با نام تماس‌گیرنده و دکمه‌های پاسخ و رد روی برنامه فعلی نمایش داده می‌شود.", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(Prefs.on("callPopup"), { Prefs.setFlag("callPopup", it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مجوز نمایش روی سایر پنجره‌ها")
                        Text(if (overlayOk) "مجوز سیستم فعال است" else "برای پنجره شناور، مجوز سیستم لازم است", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (overlayOk) "فعال" else "غیرفعال", color = if (overlayOk) Color(0xFF2E9F5B) else MaterialTheme.colorScheme.error)
                }
                if (!overlayOk) Button(onClick = {
                    try { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + ctx.packageName))) } catch (_: Exception) { }
                }) { Text("اعطای مجوز نمایش روی برنامه‌ها") }
            }
            SectionCard("محدودیت باتری") {
                Text(if (batteryOk) "✅ برنامه از محدودیت بهینه‌سازی باتری مستثنا است."
                    else "⚠️ برای دریافت مطمئن تماس در پس‌زمینه، محدودیت بهینه‌سازی باتری را بررسی کنید.")
                if (!batteryOk && Build.VERSION.SDK_INT >= 23) Button(onClick = {
                    try { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + ctx.packageName))) } catch (_: Exception) {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }) { Text("درخواست عدم بهینه‌سازی باتری") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + ctx.packageName)))
                }) { Text("تنظیمات باتری و اجرای خودکار برنامه") }
                Text("در گوشی‌های شیائومی، هواوی، اوپو و برخی مدل‌های سامسونگ، علاوه بر این گزینه باید اجرای خودکار و فعالیت پس‌زمینه را نیز در تنظیمات سازنده فعال کنید.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("با فعال بودن این گزینه و اعطای مجوز نمایش روی برنامه‌ها، هنگام باز بودن قفل گوشی یک کارت شناور با دکمه‌های پاسخ و رد نمایش داده می‌شود. روی صفحه قفل از رابط استاندارد تماس و اعلان تمام‌صفحه اندروید استفاده می‌شود؛ رفتار نهایی به نسخه اندروید و سیاست سازنده گوشی وابسته است.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "notif" -> SubPage("اعلان‌ها", back) {
            SectionCard("تماس‌های بی‌پاسخ") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اعلان تماس بی‌پاسخ")
                        Text("با دکمه‌های «تماس» و «پیامک»", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(Prefs.missedNotify, { Prefs.missedNotify = it; Prefs.save(); Notifs.refreshMissed(ctx) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("نشان عددی روی آیکون برنامه")
                        Text("تعداد تماس‌های بی‌پاسخ؛ اگر اعلان خاموش باشد، یک اعلان بی‌صدا فقط برای نشان ثبت می‌شود", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(Prefs.missedBadge, { Prefs.missedBadge = it; Prefs.save(); Notifs.refreshMissed(ctx) })
                }
                OutlinedButton({
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }) { Text("تنظیمات اعلان‌های سیستم") }
                Text("نمایش نشان روی آیکون به لانچر گوشی بستگی دارد و با پاک‌شدن اعلان یا باز کردن برنامه حذف می‌شود.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "gest" -> SubPage("حرکات و لمس", back) {
            MenuItem("🕘", "تاریخچه تماس‌ها", "لمس، لمس طولانی و کشیدن") { path = "gest/hist" }
            MenuItem("👤", "مخاطبین", "لمس، لمس طولانی و کشیدن") { path = "gest/con" }
        }
        "gest/hist" -> SubPage("حرکات تاریخچه", { path = "gest" }) {
            val opts = listOf(0, 1, 2, 3, 4, 5, 6)
            SectionCard("عمل هر حرکت") {
                ChoiceRow("لمس کوتاه", Prefs.hTap, opts) { Prefs.hTap = it; Prefs.save() }
                ChoiceRow("لمس طولانی", Prefs.hLong, opts) { Prefs.hLong = it; Prefs.save() }
                ChoiceRow("کشیدن به چپ", Prefs.hSwL, opts) { Prefs.hSwL = it; Prefs.save() }
                ChoiceRow("کشیدن به راست", Prefs.hSwR, opts) { Prefs.hSwR = it; Prefs.save() }
            }
        }
        "gest/con" -> SubPage("حرکات مخاطبین", { path = "gest" }) {
            val opts = listOf(0, 1, 2, 3, 6)
            SectionCard("عمل هر حرکت") {
                ChoiceRow("لمس کوتاه", Prefs.cTap, opts) { Prefs.cTap = it; Prefs.save() }
                ChoiceRow("لمس طولانی", Prefs.cLong, opts) { Prefs.cLong = it; Prefs.save() }
                ChoiceRow("کشیدن به چپ", Prefs.cSwL, opts) { Prefs.cSwL = it; Prefs.save() }
                ChoiceRow("کشیدن به راست", Prefs.cSwR, opts) { Prefs.cSwR = it; Prefs.save() }
            }
        }
        "data" -> SubPage("داده‌ها", back) {
            MenuItem("📈", "آمار تماس‌ها", "خلاصه، پرمکالمه‌ترین‌ها و نمودار ماهانه") { path = "data/stats" }
            MenuItem("🗄", "پشتیبان‌گیری و بازیابی", "یادداشت‌ها، گروه‌ها، معرف‌ها و علاقه‌مندی‌ها") { path = "data/backup" }
            MenuItem("📊", "خروجی اکسل", "مخاطبین یا تاریخچه تماس‌ها") { path = "data/excel" }
            MenuItem("🎙", "مکالمات ضبط‌شده", "پخش و حذف ضبط‌ها") { path = "data/rec" }
        }
        "data/stats" -> SubPage("آمار تماس‌ها", { path = "data" }) { StatsContent() }
        "data/backup" -> SubPage("پشتیبان‌گیری و بازیابی", { path = "data" }) { BackupContent() }
        "data/excel" -> SubPage("خروجی اکسل", { path = "data" }) { ExcelContent() }
        "data/rec" -> SubPage("مکالمات ضبط‌شده", { path = "data" }) { RecordingsContent() }
        else -> SubPage("دربارهٔ برنامه", back) {
            SectionCard("مدیریت تماس") {
                Text("نسخه ۱٫۲", fontWeight = FontWeight.Bold)
                Text("شماره‌گیر، تاریخچه، مخاطبین، یادآوری تماس، گروه‌بندی و مدیریت هوشمند تماس‌ها.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Divider()
                Text("برنامه‌نویس", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("علیرضا روحانی", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://alirezarouhani.ir"))) }) {
                    Icon(Icons.Default.Language, null); Spacer(Modifier.width(6.dp)); Text("alirezarouhani.ir")
                }
            }
        }
    }
}

@Composable
fun ReminderListContent() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val reminders = remember(tick) { Reminders.all(ctx).filter { it.atMillis > System.currentTimeMillis() } }
    if (reminders.isEmpty()) {
        SectionCard("فهرست یادآوری‌ها") { Text("یادآوری فعالی ثبت نشده است", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        reminders.forEach { r ->
            SectionCard(r.name.ifBlank { Jalali.fa(r.number) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Jalali.fa(r.number), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Jalali.format(r.atMillis), fontWeight = FontWeight.Medium)
                    }
                    IconButton({ Reminders.delete(ctx, r.number, r.atMillis); tick++ }) {
                        Icon(Icons.Default.DeleteOutline, "حذف یادآوری", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ExcelContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    if (kind == 0) writeXlsx(it, "مخاطبین", contactsRows(ctx)) else writeXlsx(it, "تماس‌ها", callsRows(ctx))
                }
                true
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, if (ok) "فایل اکسل ذخیره شد" else "خطا در ذخیره فایل", Toast.LENGTH_LONG).show()
            }
        }
    }
    SectionCard("ذخیره به‌صورت فایل .xlsx") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ kind = 0; launcher.launch("contacts.xlsx") }, Modifier.weight(1f)) { Text("مخاطبین") }
            Button({ kind = 1; launcher.launch("call-history.xlsx") }, Modifier.weight(1f)) { Text("تاریخچه تماس‌ها") }
        }
        Text("تاریخ‌ها در فایل به‌صورت شمسی ثبت می‌شوند.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecordingsContent() {
    val ctx = LocalContext.current
    val recDir = remember { Recorder.dir(ctx) }
    var files by remember { mutableStateOf(recDir.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList<File>()) }
    var playing by remember { mutableStateOf<String?>(null) }
    val holder = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) { onDispose { try { holder[0]?.release() } catch (e: Exception) { } } }
    fun toggle(f: File) {
        try { holder[0]?.release() } catch (e: Exception) { }
        holder[0] = null
        if (playing == f.name) { playing = null; return }
        try {
            val m = MediaPlayer(); m.setDataSource(f.absolutePath); m.prepare(); m.start()
            m.setOnCompletionListener { playing = null }
            holder[0] = m; playing = f.name
        } catch (e: Exception) { playing = null }
    }
    SectionCard("ضبط‌ها") {
        if (files.isEmpty()) Text("ضبطی وجود ندارد.", fontSize = 13.sp)
        files.forEach { f ->
            val parts = f.nameWithoutExtension.split("_")
            val ts = parts.getOrNull(1)?.toLongOrNull() ?: f.lastModified()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Jalali.format(ts), fontSize = 13.sp)
                    Text(Jalali.fa(parts.getOrNull(2) ?: ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ toggle(f) }) { Icon(if (playing == f.name) Icons.Default.Close else Icons.Default.PlayArrow, "پخش") }
                IconButton({ if (playing == f.name) { holder[0]?.release(); holder[0] = null; playing = null }; f.delete(); files = files.filter { it != f } }) {
                    Icon(Icons.Default.Delete, "حذف")
                }
            }
        }
        Text("توجه: ضبط با میکروفون انجام می‌شود و در بسیاری از گوشی‌ها فقط با روشن‌بودن بلندگو صدای طرف مقابل ضبط می‌شود. رعایت قوانین مربوط به ضبط مکالمه بر عهدهٔ کاربر است.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


// ---------------- اجزای جدید ----------------

@Composable
fun BarChart(labels: List<String>, values: List<Long>, fmt: (Long) -> String) {
    val cs = MaterialTheme.colorScheme
    val mx = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text(fmt(v), fontSize = 10.sp)
                Box(Modifier.fillMaxWidth().height((110f * v.toFloat() / mx.toFloat()).coerceAtLeast(3f).dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Brush.verticalGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.45f)))))
                Text(labels[i], fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatsContent() {
    val cs = MaterialTheme.colorScheme
    val calls = Repo.calls
    if (calls.isEmpty()) { SectionCard("آمار") { Text("تاریخچه‌ای برای نمایش وجود ندارد.", fontSize = 13.sp) }; return }
    val total = calls.sumOf { it.duration }
    SectionCard("خلاصهٔ کل تاریخچه") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("کل تماس‌ها", Jalali.fa(calls.size))
            Stat("ورودی", Jalali.fa(calls.count { it.type == 1 }))
            Stat("خروجی", Jalali.fa(calls.count { it.type == 2 }))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("بی‌پاسخ", Jalali.fa(calls.count { it.type == 3 }))
            Stat("ردشده", Jalali.fa(calls.count { it.type == 5 || it.type == 6 }))
            Stat("مجموع مکالمه", fmtDur(total))
        }
    }
    val top = remember(calls) {
        calls.filter { norm(it.number).isNotEmpty() }.groupBy { norm(it.number) }
            .map { (k, v) -> Triple(k, v.sumOf { it.duration }, v.size) }.sortedByDescending { it.second }.take(5)
    }
    SectionCard("پرمکالمه‌ترین‌ها") {
        top.forEach { (k, dur, cnt) ->
            val first = calls.first { norm(it.number) == k }
            val nm = first.name.ifBlank { Repo.names[k] ?: "" }.ifBlank { Jalali.fa(first.number) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(nm, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(nm, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(Jalali.fa("$cnt تماس"), fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
                Text(fmtDur(dur), fontWeight = FontWeight.Bold, color = cs.primary)
            }
        }
    }
    val cur = Jalali.monthKey(System.currentTimeMillis())
    val keys = ((cur - 5)..cur).toList()
    SectionCard("تعداد تماس در ۶ ماه اخیر") {
        BarChart(keys.map { Jalali.monthName(it) }, keys.map { k -> calls.count { Jalali.monthKey(it.date) == k }.toLong() }) { Jalali.fa(it.toString()) }
    }
    SectionCard("زمان مکالمه در ۶ ماه اخیر") {
        BarChart(keys.map { Jalali.monthName(it) }, keys.map { k -> calls.filter { Jalali.monthKey(it.date) == k }.sumOf { it.duration } }) { fmtMin(it) }
    }
}

@Composable
fun BackupContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Db.get(ctx).exportJson().toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) { Toast.makeText(ctx, if (ok) "پشتیبان ذخیره شد" else "خطا در ذخیره", Toast.LENGTH_LONG).show() }
        }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = try {
                val t = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                Db.get(ctx).importJson(t)
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) {
                dbTick.intValue++
                Toast.makeText(ctx, if (ok) "بازیابی انجام شد" else "فایل پشتیبان معتبر نیست", Toast.LENGTH_LONG).show()
            }
        }
    }
    SectionCard("پشتیبان‌گیری") {
        Text("یادداشت‌ها، گروه‌ها، عضویت‌ها، معرف‌ها، علاقه‌مندی‌ها، شماره‌های مسدود و پیامک‌های آماده در یک فایل ذخیره می‌شوند؛ برای انتقال به گوشی جدید یا بعد از نصب دوباره مفید است.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ exp.launch("callmanager-backup.json") }, Modifier.weight(1f)) { Text("ذخیره پشتیبان") }
            OutlinedButton({ imp.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("بازیابی از فایل") }
        }
    }
}

@Composable
fun BlockedListCard() {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var list by remember { mutableStateOf(db.allBlocked()) }
    var add by remember { mutableStateOf("") }
    SectionCard("شماره‌های مسدود (رد خودکار)") {
        if (list.isEmpty()) Text("شماره‌ای ثبت نشده است.", fontSize = 13.sp)
        list.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Repo.names[n] ?: Jalali.fa(n))
                    if (Repo.names[n] != null) Text(Jalali.fa(n), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ db.setBlocked(n, false); list = db.allBlocked() }) { Icon(Icons.Default.Delete, "حذف") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(add, { add = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("افزودن شماره") })
            Spacer(Modifier.width(8.dp))
            Button({
                val e = Jalali.en(add)
                if (norm(e).isNotEmpty()) { db.setBlocked(e, true); add = ""; list = db.allBlocked() }
            }) { Text("افزودن") }
        }
    }
}
