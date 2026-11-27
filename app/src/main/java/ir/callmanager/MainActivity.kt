@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.filter

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge()
        val dial = intent?.data?.schemeSpecificPart ?: ""
        setContent { AppTheme { Root(dial) } }
    }

    override fun onResume() {
        super.onResume()
        if (hasPerms(this)) Repo.refresh(this)
        Notifs.clearMissed(this)   // با باز شدن برنامه، نشان و اعلان تماس بی‌پاسخ پاک می‌شود
    }
}

val PERMS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)

fun hasPerms(c: Context) = PERMS.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

val dbTick = mutableIntStateOf(0)

/** افزودن شماره به مخاطبین: کاربر می‌تواند مخاطب جدید بسازد یا شماره را به مخاطب موجود اضافه کند */
fun addToContacts(ctx: Context, number: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(ctx, "برنامهٔ مخاطبین روی این دستگاه پیدا نشد", Toast.LENGTH_LONG).show()
    }
}

fun cleanNumber(s: String): String = Jalali.en(s).filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

private class Todo(val text: String, val action: String, val run: () -> Unit)

@Composable
fun Root(initialDial: String) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(hasPerms(ctx)) }
    var roleTick by remember { mutableIntStateOf(0) }
    val ask = PERMS + Manifest.permission.POST_NOTIFICATIONS + Manifest.permission.WRITE_CALL_LOG
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = hasPerms(ctx); if (ok) Repo.refresh(ctx)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleTick++ }
    LaunchedEffect(Unit) { if (!ok) permLauncher.launch(ask) else Repo.refresh(ctx) }

    val rm = ctx.getSystemService(RoleManager::class.java)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val isDialer = remember(roleTick) { rm.isRoleHeld(RoleManager.ROLE_DIALER) }
    val isScreener = remember(roleTick) { rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val fsiOk = remember(roleTick) { Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent() }

    var tab by remember { mutableIntStateOf(if (initialDial.isNotBlank()) 1 else 0) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    val tabs = listOf(NavTab("تماس‌ها", Icons.Default.History), NavTab("شماره‌گیر", Icons.Default.Dialpad),
        NavTab("مخاطبین", Icons.Default.Contacts), NavTab("تنظیمات", Icons.Default.Settings))
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // فقط اولین مورد لازم نمایش داده می‌شود تا صفحه شلوغ نشود
    val todos = buildList {
        if (!ok) add(Todo("برای نمایش کامل تاریخچه و مخاطبین، دسترسی‌های لازم را فعال کنید. تنظیمات برنامه همچنان در دسترس است.", "اعطای دسترسی") { permLauncher.launch(ask) })
        if (ok && !isDialer) add(Todo("برای مدیریت کامل تماس و نمایش صفحه تماس، برنامه را «شماره‌گیر پیش‌فرض» کنید.", "فعال‌سازی") {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        })
        if (ok && !isScreener) add(Todo("برای رد خودکار تماس، دسترسی «فیلتر تماس» لازم است.", "فعال‌سازی") {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        })
        if (ok && !fsiOk) add(Todo("برای نمایش صفحه تماس روی صفحه قفل، مجوز «اعلان تمام‌صفحه» لازم است.", "اجازه") {
            roleLauncher.launch(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + ctx.packageName)))
        })
    }

    SimChooser()
    val d = detail
    if (Nav.callPreview >= 0) {
        BackHandler { Nav.callPreview = -1 }
        CallPreviewScreen()
    } else if (d != null) {
        DetailScreen(d) { detail = null }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { AppHeader(tabs[tab].label, compact = tab == 1) },
            bottomBar = { if (!imeVisible) AppNavBar(tabs, tab) { tab = it } }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
                todos.firstOrNull()?.let { SetupBanner(it, todos.size - 1) }
                when (tab) {
                    0 -> RecentsScreen { detail = it }
                    1 -> DialerScreen(initialDial) { detail = it }
                    2 -> ContactsScreen { detail = it }
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SetupBanner(t: Todo, more: Int) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    Card(Modifier.fillMaxWidth().padding(12.dp, 6.dp), shape = RoundedCornerShape(look.row),
        colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = cs.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.text, fontSize = 13.sp)
                if (more > 0) Text(Jalali.fa("$more مورد دیگر هم در انتظار است"), fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp)); Button(t.run) { Text(t.action) }
        }
    }
}

// ---------------- تاریخچهٔ تماس ----------------

@Composable
fun RecentsScreen(onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val cs = MaterialTheme.colorScheme
    val deleter = rememberCallDeleter()
    var filter by remember { mutableIntStateOf(0) }
    var groupFilter by remember { mutableIntStateOf(0) }
    var simFilter by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var limit by remember(filter, query) { mutableIntStateOf(30) }
    val selected = remember { mutableStateListOf<Long>() }
    var selMode by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<List<Long>?>(null) }
    val all = Repo.calls
    val groups = remember(dbTick.intValue) { db.groups() }
    val members = remember(dbTick.intValue) { db.allMembers() }
    val reminderNumbers = remember(dbTick.intValue, Repo.calls) { Reminders.all(ctx).map { norm(it.number) }.toSet() }
    val sims = Repo.sims
    val list = remember(all, filter, query, groupFilter, simFilter, members) {
        val typed = when (filter) {
            1 -> all.filter { it.type == 3 }
            2 -> all.filter { it.type == 1 }
            3 -> all.filter { it.type == 2 }
            else -> all
        }
        val byGroup = if (groupFilter == 0) typed else typed.filter { groupFilter in (members[norm(it.number)] ?: emptySet()) }
        val bySim = if (simFilter.isBlank()) byGroup else byGroup.filter { it.simId == simFilter }
        val qn = Jalali.en(query).trim().lowercase()
        val qd = qn.filter { it.isDigit() }
        if (qn.isEmpty()) bySim else bySim.filter { r ->
            val nm = r.name.ifBlank { Repo.names[norm(r.number)] ?: "" }.lowercase()
            nm.contains(qn) || (qd.isNotEmpty() && r.number.filter { it.isDigit() }.contains(qd))
        }
    }
    val shown = remember(list, limit) { list.take(limit) }
    val grouped = remember(shown) { shown.groupBy { Jalali.dayIndex(it.date) } }
    val state = rememberLazyListState()

    // بارگذاری تدریجی: ۳۰ مورد اول، با اسکرول موارد بعدی
    LaunchedEffect(state, list.size) {
        snapshotFlow {
            val li = state.layoutInfo
            (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 6
        }.filter { it }.collect { if (limit < list.size) limit += 30 }
    }
    BackHandler(selMode) { selMode = false; selected.clear() }

    fun toggle(r: Rec) {
        if (r.id in selected) selected.remove(r.id) else selected.add(r.id)
        if (selected.isEmpty()) selMode = false
    }
    fun perform(a: Int, r: Rec) {
        when (a) {
            1 -> onOpen(targetFor(r.number, r.name))
            2 -> callNumber(ctx, r.number)
            3 -> sendSms(ctx, r.number)
            4 -> confirm = listOf(r.id)
            5 -> { selMode = true; if (r.id !in selected) selected.add(r.id) }
            6 -> {
                val b = !db.isBlocked(r.number); db.setBlocked(r.number, b)
                Toast.makeText(ctx, if (b) "رد خودکار این شماره فعال شد" else "رد خودکار این شماره لغو شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column {
        if (selMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ selMode = false; selected.clear() }) { Icon(Icons.Default.Close, "لغو") }
                Text(Jalali.fa("${selected.size} مورد انتخاب شد"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton({ selected.clear(); selected.addAll(list.map { it.id }) }) { Text("همه") }
                IconButton({ if (selected.isNotEmpty()) confirm = selected.toList() }) { Icon(Icons.Default.Delete, "حذف", tint = Color(0xFFD32F2F)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }, shape = CircleShape,
                    color = if (searchOpen) cs.primaryContainer else cs.surfaceContainerHigh) {
                    Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, if (searchOpen) "بستن جستجو" else "جستجو",
                        Modifier.padding(7.dp).size(20.dp), tint = if (searchOpen) cs.onPrimaryContainer else cs.onSurface)
                }
                FilterPill("نوع", listOf("همه" to "0", "بی‌پاسخ" to "1", "ورودی" to "2", "خروجی" to "3"), filter.toString()) { filter = it.toInt() }
                if (groups.isNotEmpty()) FilterPill("گروه", listOf("همه گروه‌ها" to "0") + groups.map { it.name to it.id.toString() }, groupFilter.toString()) { groupFilter = it.toInt() }
                if (sims.size > 1) FilterPill("سیم‌کارت", listOf("همه" to "") + sims.map { "SIM ${Jalali.fa(it.index)}" to it.handle.id }, simFilter) { simFilter = it }
            }
            if (searchOpen) SearchField(query, { query = it }, "جستجوی نام یا شماره", autoFocus = true)
        }
        if (list.isEmpty()) Empty(if (Repo.loadingContacts) "در حال بارگذاری…" else "تماسی وجود ندارد")
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 12.dp)) {
            grouped.forEach { (day, rows) ->
                stickyHeader(key = "h$day") {
                    Box(Modifier.fillMaxWidth().background(cs.background)) {
                        Text(Jalali.dayLabel(rows.first().date), Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                    }
                }
                items(rows, key = { it.id }) { r ->
                    SwipeRow(if (selMode) 0 else Prefs.hSwL, if (selMode) 0 else Prefs.hSwR, { a -> perform(a, r) }) {
                        CallRow(r, selMode, r.id in selected, hasReminder = norm(r.number) in reminderNumbers,
                            onTap = { if (selMode) toggle(r) else perform(Prefs.hTap, r) },
                            onLong = { if (selMode) toggle(r) else perform(Prefs.hLong, r) },
                            onInfo = { onOpen(targetFor(r.number, r.name)) })
                    }
                }
            }
        }
    }
    confirm?.let { ids ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("حذف از تاریخچه") },
            text = { Text(Jalali.fa("${ids.size} مورد از تاریخچه تماس حذف شود؟")) },
            confirmButton = { TextButton({ deleter(ids); confirm = null; selected.clear(); selMode = false }) { Text("حذف", color = Color(0xFFD32F2F)) } },
            dismissButton = { TextButton({ confirm = null }) { Text("انصراف") } })
    }
}

@Composable
fun CallRow(r: Rec, selMode: Boolean, isSel: Boolean, hasReminder: Boolean = false, onTap: () -> Unit, onLong: () -> Unit,
            onInfo: () -> Unit = {}) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val title = r.name.ifBlank { Repo.names[norm(r.number)] ?: "" }.ifBlank { Jalali.fa(r.number).ifBlank { "ناشناس" } }
    val simIdx = Repo.sims.indexOfFirst { it.handle.id == r.simId }
    val sim = if (Prefs.on("showSimInfo") && simIdx >= 0 && Repo.sims.size > 1) "  •  SIM ${Jalali.fa(simIdx + 1)}" else ""
    val dur = if (Prefs.on("showDuration") && r.duration > 0) "  •  " + fmtDur(r.duration) else ""
    ListRowShell(selected = isSel, onClick = onTap, onLong = onLong) {
        if (selMode) { Checkbox(isSel, null); Spacer(Modifier.width(12.dp)) } else { Avatar(title, 46.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (r.type == 3) Color(0xFFE53935) else cs.onSurface)
                if (hasReminder && Prefs.on("remindBell")) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.NotificationsActive, "یادآوری فعال", tint = cs.primary, modifier = Modifier.size(15.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeGlyph(r.type, 16.dp)
                Spacer(Modifier.width(3.dp))
                Text(typeName(r.type) + "  •  " + Jalali.time(r.date) + dur + sim, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
        if (!selMode) {
            if (look.style == 1) IconButton(onInfo) { Icon(Icons.Default.Info, "جزئیات", tint = cs.primary) }
            else IconButton({ callNumber(ctx, r.number) }) { Handset(cs.primary, 22.dp) }
        }
    }
}

// ---------------- شماره‌گیر ----------------

@Composable
private fun rememberKeyTone(): (Char) -> Unit {
    val tg = remember { try { ToneGenerator(AudioManager.STREAM_DTMF, 55) } catch (_: Exception) { null } }
    DisposableEffect(Unit) { onDispose { try { tg?.release() } catch (_: Exception) { } } }
    return { ch ->
        if (Prefs.on("keypadSound")) {
            val t = when (ch) {
                '0' -> ToneGenerator.TONE_DTMF_0; '1' -> ToneGenerator.TONE_DTMF_1; '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3; '4' -> ToneGenerator.TONE_DTMF_4; '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6; '7' -> ToneGenerator.TONE_DTMF_7; '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9; '*' -> ToneGenerator.TONE_DTMF_S; '#' -> ToneGenerator.TONE_DTMF_P
                else -> -1
            }
            if (t >= 0) try { tg?.startTone(t, 90) } catch (_: Exception) { }
        }
    }
}

@Composable
fun DialerScreen(initial: String, onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var num by remember { mutableStateOf(initial) }
    val q = num.filter { it.isDigit() }
    val contacts = Repo.contacts
    val calls = Repo.calls
    val favs = remember(dbTick.intValue) { Db.get(ctx).allFavs() }
    val favOn = Prefs.on("favorites"); val sugOn = Prefs.on("dialerSuggest")
    val results = remember(q, contacts, calls, favs, favOn, sugOn) {
        if (q.isEmpty() && !favOn) emptyList<Hit>()
        else if (q.isNotEmpty() && !sugOn) emptyList<Hit>()
        else if (q.isEmpty()) contacts.filter { norm(it.number) in favs }.take(12).map { Hit(it.name, it.number) }
        else {
            val a = contacts.filter { it.digits.contains(q) }
                .sortedBy { !it.digits.startsWith(q) }.take(30).map { Hit(it.name, it.number) }
            val seen = HashSet<String>(); a.forEach { seen.add(norm(it.number)) }
            val b = ArrayList<Hit>()
            for (r in calls) {
                if (b.size >= 10) break
                if (r.number.filter { it.isDigit() }.contains(q) && seen.add(norm(r.number))) b.add(Hit("", r.number))
            }
            a + b
        }
    }
    val tone = rememberKeyTone()
    val kbMode = Prefs.on("dialerKeyboard")
    val keyH = (LocalConfiguration.current.screenHeightDp * 0.07f).coerceIn(42f, 60f).dp

    fun paste() {
        val t = try {
            ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(ctx)?.toString()
        } catch (_: Exception) { null } ?: ""
        val v = cleanNumber(t)
        if (v.isNotEmpty()) num = v else Toast.makeText(ctx, "شماره‌ای در کلیپ‌بورد پیدا نشد", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (q.isEmpty() && results.isEmpty()) Empty("شماره را وارد کنید؛ مخاطبین و تماس‌های مشابه اینجا نمایش داده می‌شوند")
            else if (results.isEmpty()) Empty("موردی یافت نشد")
            else LazyColumn {
                if (q.isEmpty()) item {
                    Text("⭐ علاقه‌مندی‌ها", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = cs.primary)
                }
                items(results) { h ->
                    ListRowShell(onClick = { num = h.number }) {
                        Avatar(h.name.ifBlank { "#" }, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name.ifBlank { Jalali.fa(h.number) }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(if (h.name.isBlank()) "از تاریخچهٔ تماس" else Jalali.fa(h.number), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        IconButton({ onOpen(targetFor(h.number, h.name)) }) { Icon(Icons.Default.Info, "جزئیات", tint = cs.onSurfaceVariant) }
                    }
                }
            }
        }

        // ---- کادر شماره: به‌صورت پیش‌فرض فقط نمایشی است و با لمس آن کیبورد باز نمی‌شود ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (num.isNotBlank() && Prefs.on("dialerSave")) IconButton({ addToContacts(ctx, num) }) { Icon(Icons.Default.PersonAdd, "ذخیره در مخاطبین") }
                else if (num.isBlank() && Prefs.on("dialerPaste")) IconButton({ paste() }) { Icon(Icons.Default.ContentPaste, "جای‌گذاری شماره") }
            }
            val fs = when { num.length <= 9 -> 38; num.length <= 13 -> 32; num.length <= 17 -> 26; else -> 20 }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (kbMode) {
                    BasicTextField(num, { num = cleanNumber(it) }, Modifier.weight(1f), singleLine = true,
                        textStyle = TextStyle(fontSize = fs.sp, textAlign = TextAlign.Center, color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight().combinedClickable(onClick = { }, onLongClick = { paste() }),
                        contentAlignment = Alignment.Center) {
                        if (num.isEmpty()) Text("شماره را وارد کنید", fontSize = 16.sp, color = cs.onSurfaceVariant)
                        else Text(Jalali.fa(num), fontSize = fs.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                    }
                }
            }
            Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = { num = num.dropLast(1) }, onLongClick = { num = "" }),
                contentAlignment = Alignment.Center) {
                if (num.isNotEmpty()) Icon(Icons.Default.Backspace, "حذف رقم")
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Keypad(keyHeight = keyH, onKey = { tone(it); num += it }, onLongZero = { num += "+" })
            Spacer(Modifier.height(6.dp))
            SimCallRow(num, enabled = num.isNotBlank())   // دکمه‌های تماس از ابتدا ثابت هستند
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ---------------- مخاطبین ----------------

@Composable
fun ContactRow(c: Contact, hasReminder: Boolean = false, onTap: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    ListRowShell(onClick = onTap, onLong = onLong) {
        Avatar(c.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (hasReminder && Prefs.on("remindBell")) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.NotificationsActive, "یادآوری فعال", tint = cs.primary, modifier = Modifier.size(16.dp))
                }
            }
            Text(Jalali.fa(c.number), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        IconButton({ sendSms(ctx, c.number) }) { Icon(Icons.Default.Email, "پیامک", tint = cs.onSurfaceVariant) }
        IconButton({ callNumber(ctx, c.number) }) { Handset(cs.primary, 22.dp) }
    }
}

private fun letterOf(name: String): String {
    val ch = name.trim().firstOrNull() ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}

@Composable
fun ContactsScreen(onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val db = remember { Db.get(ctx) }
    var q by remember { mutableStateOf("") }
    var gsel by remember { mutableIntStateOf(0) }
    val groups = remember(dbTick.intValue) { db.groups() }
    val members = remember(dbTick.intValue) { db.allMembers() }
    val favs = remember(dbTick.intValue) { db.allFavs() }
    val reminderNumbers = remember(dbTick.intValue) { Reminders.numbers(ctx) }
    val all = Repo.contacts
    val list = remember(all, q, gsel, members, favs) {
        val qn = Jalali.en(q).trim().lowercase()
        val qd = qn.filter { it.isDigit() }
        all.filter { c ->
            (qn.isEmpty() || c.lname.contains(qn) || (qd.isNotEmpty() && c.digits.contains(qd))) &&
                (gsel == 0 || (gsel == -1 && norm(c.number) in favs) || (gsel > 0 && members[norm(c.number)]?.contains(gsel) == true))
        }
    }
    val headersOn = Prefs.on("letterHeaders")
    val sections = remember(list, headersOn) { if (headersOn) list.groupBy { letterOf(it.name) } else null }
    fun perform(a: Int, c: Contact) {
        when (a) {
            1 -> onOpen(DetailTarget(c.number, c.name, c.id))
            2 -> callNumber(ctx, c.number)
            3 -> sendSms(ctx, c.number)
            6 -> {
                val b = !db.isBlocked(c.number); db.setBlocked(c.number, b)
                Toast.makeText(ctx, if (b) "رد خودکار این شماره فعال شد" else "رد خودکار این شماره لغو شد", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Column {
        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SearchField(q, { q = it }, "جستجوی نام یا شماره") }
            FilledIconButton({ ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)) }) { Icon(Icons.Default.Add, "افزودن") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(gsel == 0, { gsel = 0 }, label = { Text("همه") })
            FilterChip(gsel == -1, { gsel = -1 }, label = { Text("⭐ علاقه‌مندی‌ها") })
            groups.forEach { g -> FilterChip(gsel == g.id, { gsel = g.id }, label = { Text(g.name) }) }
        }
        Text(Jalali.fa("${list.size} مخاطب"), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
        if (Repo.loadingContacts && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            val secs = sections
            if (secs == null) {
                items(list) { c ->
                    SwipeRow(Prefs.cSwL, Prefs.cSwR, { a -> perform(a, c) }) {
                        ContactRow(c, hasReminder = norm(c.number) in reminderNumbers,
                            onTap = { perform(Prefs.cTap, c) }, onLong = { perform(Prefs.cLong, c) })
                    }
                }
            } else secs.forEach { (letter, rows) ->
                stickyHeader {
                    Box(Modifier.fillMaxWidth().background(cs.background)) {
                        Text(letter, Modifier.padding(start = 22.dp, top = 8.dp, bottom = 2.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                    }
                }
                items(rows) { c ->
                    SwipeRow(Prefs.cSwL, Prefs.cSwR, { a -> perform(a, c) }) {
                        ContactRow(c, hasReminder = norm(c.number) in reminderNumbers,
                            onTap = { perform(Prefs.cTap, c) }, onLong = { perform(Prefs.cLong, c) })
                    }
                }
            }
        }
    }
}

// ---------------- گروه‌ها ----------------

@Composable
private fun ModeOption(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if (selected) cs.primaryContainer else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 12.sp, color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupToggle(title: String, checked: Boolean, active: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (active) cs.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
        .padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, color = if (active) cs.onSurface else cs.onSurfaceVariant)
        Switch(checked, onChange)
    }
}

@Composable
private fun GroupMembersDialog(g: Group, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val all = remember { db.allMembers() }
    val original = remember { all.filter { g.id in it.value }.keys.toSet() }
    val sel = remember { mutableStateListOf<String>().apply { addAll(original) } }
    var q by remember { mutableStateOf("") }
    val contacts = Repo.contacts
    val shown = remember(q, contacts) {
        val qn = Jalali.en(q).trim().lowercase(); val qd = qn.filter { it.isDigit() }
        (if (qn.isEmpty()) contacts else contacts.filter { it.lname.contains(qn) || (qd.isNotEmpty() && it.digits.contains(qd)) }).take(300)
    }
    AlertDialog(onDismissRequest = onDone, title = { Text("اعضای «${g.name}»") },
        text = {
            Column {
                SearchField(q, { q = it }, "جستجوی مخاطب")
                if (contacts.isEmpty()) Empty("مخاطبی برای نمایش نیست؛ دسترسی مخاطبین را بررسی کنید")
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown) { c ->
                        val key = norm(c.number)
                        Row(Modifier.fillMaxWidth().clickable { if (key in sel) sel.remove(key) else sel.add(key) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(key in sel, null)
                            Spacer(Modifier.width(8.dp))
                            Avatar(c.name, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(c.name, fontSize = 14.sp, maxLines = 1)
                                Text(Jalali.fa(c.number), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                val cur = sel.toSet()
                (cur - original).forEach { n -> db.setMembers(n, (all[n] ?: emptySet()) + g.id) }
                (original - cur).forEach { n -> db.setMembers(n, (all[n] ?: emptySet()) - g.id) }
                dbTick.intValue++
                onDone()
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onDone) { Text("انصراف") } })
}

@Composable
fun GroupsScreen() {
    val ctx = LocalContext.current; val db = remember { Db.get(ctx) }
    var loadError by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf(try { db.groups() } catch (_: Exception) { loadError = true; emptyList() }) }
    var adding by remember { mutableStateOf(false) }
    var membersFor by remember { mutableStateOf<Group?>(null) }
    var name by remember { mutableStateOf("") }
    val members = remember(groups, dbTick.intValue) { try { db.allMembers() } catch (_: Exception) { loadError = true; emptyMap() } }
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val mode = Prefs.groupMode
    fun reload() {
        try { groups = db.groups(); loadError = false }
        catch (_: Exception) { loadError = true; Toast.makeText(ctx, "خواندن گروه‌ها از پایگاه داده ناموفق بود", Toast.LENGTH_LONG).show() }
        dbTick.intValue++
    }
    fun setMode(m: Int) { Prefs.groupMode = m; Prefs.save() }
    Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("گروه‌های مخاطبین", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FilledIconButton({ adding = true }) { Icon(Icons.Default.Add, "گروه جدید") }
        }
        if (loadError) Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = cs.errorContainer)) {
            Text("خواندن اطلاعات گروه‌ها با خطا مواجه شد. یک‌بار صفحه را دوباره باز کنید؛ اگر مشکل ادامه داشت، پایگاه داده برنامه نیاز به بررسی دارد.",
                Modifier.padding(12.dp), color = cs.onErrorContainer, fontSize = 12.sp)
        }
        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            SectionCard("فیلتر تماس ورودی بر اساس گروه") {
                ModeOption(mode == 0, "خاموش", "گروه‌ها روی تماس‌های ورودی اثری ندارند") { setMode(0) }
                ModeOption(mode == 1, "رد تماس گروه‌های انتخابی", "تماس اعضای گروه‌هایی که «رد» آن‌ها روشن است رد می‌شود؛ بقیه عادی زنگ می‌خورند") { setMode(1) }
                ModeOption(mode == 2, "فقط گروه‌های انتخابی تماس بگیرند", "فقط اعضای گروه‌هایی که «مجاز» آن‌ها روشن است زنگ می‌خورند و بقیهٔ تماس‌ها (حتی ناشناس) رد می‌شود") { setMode(2) }
                if (mode == 2 && groups.none { it.allowOnly }) {
                    Text("⚠️ هنوز هیچ گروهی برای حالت «مجاز» انتخاب نشده است؛ تا آن زمان این فیلتر اعمال نمی‌شود تا همهٔ تماس‌ها رد نشوند.",
                        fontSize = 12.sp, color = cs.tertiary)
                }
                if (mode == 2) SettingSwitch("علاقه‌مندی‌ها هم مجاز باشند", "شماره‌های ستاره‌دار حتی خارج از گروه‌های مجاز زنگ بخورند",
                    Prefs.on("allowFavs")) { Prefs.setFlag("allowFavs", it) }
            }
        }
        Text("برای اضافه‌کردن اعضا از «مدیریت اعضا» در هر گروه استفاده کنید؛ می‌توانید از صفحهٔ جزئیات مخاطب هم گروه را تعیین کنید.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
        // این صفحه داخل SubPage و یک اسکرول عمودی قرار دارد؛ LazyColumn تو‌در‌تو
        // با ارتفاع نامحدود باعث IllegalStateException و بسته‌شدن برنامه می‌شد.
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { g ->
                val cnt = members.values.count { g.id in it }
                Card(shape = RoundedCornerShape(look.card), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(g.name, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name.ifBlank { "گروه بدون نام" }, fontWeight = FontWeight.Bold)
                                Text(Jalali.fa("$cnt عضو"), fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                            TextButton({ membersFor = g }) { Icon(Icons.Default.Groups, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("مدیریت اعضا") }
                            IconButton({ db.deleteGroup(g.id); reload() }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                        Spacer(Modifier.height(4.dp))
                        GroupToggle("🚫 رد تماس اعضای این گروه", g.autoReject, mode == 1) { db.setGroupReject(g.id, it); reload() }
                        GroupToggle("✅ مجاز بودن اعضای این گروه", g.allowOnly, mode == 2) { db.setGroupAllow(g.id, it); reload() }
                    }
                }
            }
        }
    }
    membersFor?.let { g -> GroupMembersDialog(g) { membersFor = null; reload() } }
    if (adding) AlertDialog({ adding = false }, title = { Text("گروه جدید") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("نام گروه") }) },
        confirmButton = { TextButton({
            if (name.isNotBlank()) try { db.addGroup(name) } catch (_: Exception) { Toast.makeText(ctx, "ذخیره گروه ناموفق بود", Toast.LENGTH_LONG).show() }
            name = ""; adding = false; reload()
        }) { Text("افزودن") } },
        dismissButton = { TextButton({ adding = false }) { Text("انصراف") } })
}
