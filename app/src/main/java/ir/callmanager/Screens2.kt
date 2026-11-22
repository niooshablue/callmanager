@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import android.app.NotificationManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- جزئیات مخاطب ----------------

@Composable
fun DetailScreen(t: DetailTarget, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val cs = MaterialTheme.colorScheme
    val num = t.number
    var note by remember { mutableStateOf(db.note(num)) }
    var blocked by remember { mutableStateOf(db.isBlocked(num)) }
    val groups = remember { db.groups() }
    val mem = remember { mutableStateListOf<Int>().apply { addAll(db.memberGroups(num)) } }
    var ref by remember { mutableStateOf(db.referrer(num)) }
    var picking by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf<List<Long>?>(null) }
    val deleter = rememberCallDeleter()
    val history = remember(Repo.calls, num) { Repo.calls.filter { norm(it.number) == norm(num) } }

    fun save() {
        db.setNote(num, note); db.setMembers(num, mem); db.setBlocked(num, blocked)
        db.setReferrer(num, ref?.first, ref?.second); dbTick.intValue++
    }
    BackHandler { save(); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("جزئیات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton({ save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button({ save(); Toast.makeText(ctx, "ذخیره شد", Toast.LENGTH_SHORT).show() },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp), shape = RoundedCornerShape(26.dp)) {
                    Text("ذخیره", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(t.name.ifBlank { num }, 96.dp)
                Spacer(Modifier.height(10.dp))
                Text(t.name.ifBlank { Jalali.fa(num) }, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (t.name.isNotBlank()) Text(Jalali.fa(num), color = cs.onSurfaceVariant)
                if (mem.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.filter { it.id in mem }.forEach { g -> AssistChip({}, label = { Text(g.name, fontSize = 12.sp) }) }
                }
            }
            SimCallRow(num)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ sendSms(ctx, num) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Default.Email, null); Spacer(Modifier.width(6.dp)); Text("ارسال پیامک")
                }
                if (t.contactId > 0) OutlinedButton({
                    ctx.startActivity(Intent(Intent.ACTION_EDIT).setData(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, t.contactId)))
                }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("ویرایش مخاطب")
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
    if (picking) ContactPicker({ c -> ref = Pair(c.number, c.name); picking = false }, { picking = false })
    confirmDel?.let { ids ->
        AlertDialog(onDismissRequest = { confirmDel = null }, title = { Text("حذف از تاریخچه") },
            text = { Text(Jalali.fa("${ids.size} مورد از سابقهٔ تماس حذف شود؟")) },
            confirmButton = { TextButton({ deleter(ids); confirmDel = null }) { Text("حذف", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ confirmDel = null }) { Text("انصراف") } })
    }
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
            MenuItem("🎨", "ظاهر برنامه", "رنگ‌ها، نوشته‌ها، فونت و اعداد") { path = "look" }
            MenuItem("📞", "تماس و سیم‌کارت", "سیم‌کارت پیش‌فرض، مسدودسازی، صفحه قفل") { path = "calls" }
            MenuItem("🔔", "اعلان‌ها", "تماس‌های بی‌پاسخ و نشان آیکون") { path = "notif" }
            MenuItem("👆", "حرکات و لمس", "لمس کوتاه، بلند و کشیدن در تاریخچه و مخاطبین") { path = "gest" }
            MenuItem("💾", "داده‌ها", "خروجی اکسل و مکالمات ضبط‌شده") { path = "data" }
            MenuItem("ℹ️", "دربارهٔ برنامه", "نسخه و توضیحات") { path = "about" }
        }
        "look" -> SubPage("ظاهر برنامه", back) {
            MenuItem("🎨", "رنگ‌ها", "پس‌زمینه، کادرها، نوشته‌ها و رنگ اصلی") { path = "look/colors" }
            MenuItem("🔤", "نوشته و اعداد", "اندازه، فونت و نمایش فارسی/انگلیسی اعداد") { path = "look/text" }
            OutlinedButton({ Prefs.reset() }, Modifier.fillMaxWidth()) { Text("بازگشت به تنظیمات پیش‌فرض ظاهر") }
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prefs.fontNames.forEachIndexed { i, n -> FilterChip(Prefs.font == i, { Prefs.font = i; Prefs.save() }, label = { Text(n) }) }
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
            MenuItem("🚫", "مسدودسازی", "رد خودکار شماره‌های ناشناس") { path = "calls/block" }
            MenuItem("🔒", "نمایش روی صفحه قفل", "مجوز اعلان تمام‌صفحه") { path = "calls/lock" }
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
            Text("رد خودکار گروه‌ها و شماره‌های مشخص را از تب «گروه‌ها» و جزئیات هر مخاطب تنظیم کنید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "calls/lock" -> SubPage("نمایش روی صفحه قفل", { path = "calls" }) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val ok = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
            SectionCard("اعلان تمام‌صفحه") {
                Text(if (ok) "✅ مجوز فعال است؛ تماس ورودی روی صفحه قفل نمایش داده می‌شود." else "⚠️ مجوز فعال نیست؛ برای روشن‌شدن صفحه هنگام تماس لازم است.")
                if (!ok) Button({
                    ctx.startActivity(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + ctx.packageName)))
                }) { Text("باز کردن تنظیمات مجوز") }
                Text("اگر گوشی باز و در حال استفاده باشد، ممکن است اندروید یا سازندهٔ گوشی به‌جای تمام‌صفحه یک اعلان بالای صفحه نشان دهد.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            MenuItem("📊", "خروجی اکسل", "مخاطبین یا تاریخچه تماس‌ها") { path = "data/excel" }
            MenuItem("🎙", "مکالمات ضبط‌شده", "پخش و حذف ضبط‌ها") { path = "data/rec" }
        }
        "data/excel" -> SubPage("خروجی اکسل", { path = "data" }) { ExcelContent() }
        "data/rec" -> SubPage("مکالمات ضبط‌شده", { path = "data" }) { RecordingsContent() }
        else -> SubPage("دربارهٔ برنامه", back) {
            SectionCard("مدیریت تماس") {
                Text("نسخه ۰٫۳")
                Text("شماره‌گیر، تاریخچه، مخاطبین، یادداشت هنگام تماس، گروه‌بندی و رد خودکار تماس.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
