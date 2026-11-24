@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.filter

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
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
    val tabs = listOf("تماس‌ها" to Icons.Default.DateRange, "شماره‌گیر" to Icons.Default.Call, "مخاطبین" to Icons.Default.Person,
        "گروه‌ها" to Icons.Default.Star, "تنظیمات" to Icons.Default.Settings)

    SimChooser()
    val d = detail
    if (d != null) {
        DetailScreen(d) { detail = null }
    } else {
        Scaffold(
            topBar = { AppHeader(tabs[tab].first) },
            bottomBar = {
                NavigationBar(Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(tab == i, { tab = i }, { Icon(t.second, null) }, label = { Text(t.first, fontSize = 11.sp, maxLines = 1) })
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (!ok) {
                    Banner("برای نمایش تماس‌ها و مخاطبین، دسترسی‌ها لازم است.", "اعطای دسترسی") { permLauncher.launch(ask) }
                } else {
                    if (!isDialer) Banner("برای نمایش یادداشت هنگام تماس، برنامه را «شماره‌گیر پیش‌فرض» کنید.", "فعال‌سازی") {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    }
                    if (!isScreener) Banner("برای رد خودکار تماس، دسترسی «فیلتر تماس» لازم است.", "فعال‌سازی") {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    }
                    if (!fsiOk) Banner("برای نمایش صفحه تماس روی صفحه قفل، مجوز «اعلان تمام‌صفحه» لازم است.", "اجازه") {
                        roleLauncher.launch(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + ctx.packageName)))
                    }
                    when (tab) {
                        0 -> RecentsScreen { detail = it }
                        1 -> DialerScreen(initialDial) { detail = it }
                        2 -> ContactsScreen { detail = it }
                        3 -> GroupsScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp, 6.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 13.sp)
            Spacer(Modifier.width(8.dp)); Button(onClick) { Text(action) }
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
    var query by remember { mutableStateOf("") }
    var limit by remember(filter, query) { mutableIntStateOf(30) }
    val selected = remember { mutableStateListOf<Long>() }
    var selMode by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<List<Long>?>(null) }
    val all = Repo.calls
    val list = remember(all, filter, query) {
        val base = when (filter) {
            1 -> all.filter { it.type == 3 }
            2 -> all.filter { it.type == 1 }
            3 -> all.filter { it.type == 2 }
            else -> all
        }
        val qn = Jalali.en(query).trim().lowercase()
        val qd = qn.filter { it.isDigit() }
        if (qn.isEmpty()) base else base.filter { r ->
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
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), singleLine = true,
                placeholder = { Text("جستجو در تاریخچه (نام یا شماره)") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(28.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("همه", "بی‌پاسخ", "ورودی", "خروجی").forEachIndexed { i, t -> FilterChip(filter == i, { filter = i }, label = { Text(t) }) }
            }
        }
        if (list.isEmpty()) Empty(if (Repo.loadingContacts) "در حال بارگذاری…" else "تماسی وجود ندارد")
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 12.dp)) {
            grouped.forEach { (day, rows) ->
                item(key = "h$day") {
                    Text(Jalali.dayLabel(rows.first().date), Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                }
                items(rows, key = { it.id }) { r ->
                    SwipeRow(if (selMode) 0 else Prefs.hSwL, if (selMode) 0 else Prefs.hSwR, { a -> perform(a, r) }) {
                        CallRow(r, selMode, r.id in selected,
                            onTap = { if (selMode) toggle(r) else perform(Prefs.hTap, r) },
                            onLong = { if (selMode) toggle(r) else perform(Prefs.hLong, r) })
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
fun CallRow(r: Rec, selMode: Boolean, isSel: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val title = r.name.ifBlank { Repo.names[norm(r.number)] ?: "" }.ifBlank { Jalali.fa(r.number).ifBlank { "ناشناس" } }
    val simIdx = Repo.sims.indexOfFirst { it.handle.id == r.simId }
    val sim = if (simIdx >= 0 && Repo.sims.size > 1) "  •  SIM ${Jalali.fa(simIdx + 1)}" else ""
    val dur = if (r.duration > 0) "  •  " + fmtDur(r.duration) else ""
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(18.dp))
        .combinedClickable(onClick = onTap, onLongClick = onLong),
        shape = RoundedCornerShape(18.dp), color = if (isSel) cs.primaryContainer else cs.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selMode) Checkbox(isSel, null) else CallTypeBadge(r.type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (r.type == 3) Color(0xFFE53935) else cs.onSurface)
                Text(Jalali.time(r.date) + dur + sim, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            if (!selMode) IconButton({ callNumber(ctx, r.number) }) { Handset(cs.primary, 22.dp) }
        }
    }
}

// ---------------- شماره‌گیر ----------------

@Composable
fun DialerScreen(initial: String, onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
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
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (q.isEmpty() && results.isEmpty()) Empty("شماره را وارد کنید؛ مخاطبین و تماس‌های مشابه اینجا نمایش داده می‌شوند")
            else if (results.isEmpty()) Empty("موردی یافت نشد")
            else LazyColumn {
                if (q.isEmpty()) item {
                    Text("⭐ علاقه‌مندی‌ها", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                items(results) { h ->
                    ListItem(
                        modifier = Modifier.clickable { num = h.number },
                        leadingContent = { Avatar(h.name.ifBlank { "#" }, 40.dp) },
                        headlineContent = { Text(h.name.ifBlank { Jalali.fa(h.number) }) },
                        supportingContent = { Text(if (h.name.isBlank()) "از تاریخچهٔ تماس" else Jalali.fa(h.number), fontSize = 12.sp) },
                        trailingContent = { IconButton({ onOpen(targetFor(h.number, h.name)) }) { Icon(Icons.Default.Info, null) } })
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (num.isNotBlank()) IconButton({
                    ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).putExtra(ContactsContract.Intents.Insert.PHONE, num))
                }) { Icon(Icons.Default.Add, "ذخیره") } else Spacer(Modifier.size(48.dp))
                Text(Jalali.fa(num), Modifier.weight(1f), fontSize = 30.sp, textAlign = TextAlign.Center, maxLines = 1)
                Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = { num = num.dropLast(1) }, onLongClick = { num = "" }),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Clear, "حذف") }
            }
            Keypad(keyHeight = 54.dp, onKey = { num += it }, onLongZero = { num += "+" })
            Spacer(Modifier.height(6.dp))
            SimCallRow(num, enabled = num.isNotBlank())   // دکمه‌های تماس از ابتدا ثابت هستند
        }
    }
}

// ---------------- مخاطبین ----------------

@Composable
fun ContactRow(c: Contact, onTap: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(18.dp))
        .combinedClickable(onClick = onTap, onLongClick = onLong),
        shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(Jalali.fa(c.number), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            IconButton({ sendSms(ctx, c.number) }) { Icon(Icons.Default.Email, "پیامک", tint = cs.onSurfaceVariant) }
            IconButton({ callNumber(ctx, c.number) }) { Handset(cs.primary, 22.dp) }
        }
    }
}

@Composable
fun ContactsScreen(onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var q by remember { mutableStateOf("") }
    var gsel by remember { mutableIntStateOf(0) }
    val groups = remember(dbTick.intValue) { db.groups() }
    val members = remember(dbTick.intValue) { db.allMembers() }
    val favs = remember(dbTick.intValue) { db.allFavs() }
    val all = Repo.contacts
    val list = remember(all, q, gsel, members, favs) {
        val qn = Jalali.en(q).trim().lowercase()
        val qd = qn.filter { it.isDigit() }
        all.filter { c ->
            (qn.isEmpty() || c.lname.contains(qn) || (qd.isNotEmpty() && c.digits.contains(qd))) &&
                (gsel == 0 || (gsel == -1 && norm(c.number) in favs) || (gsel > 0 && members[norm(c.number)]?.contains(gsel) == true))
        }
    }
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
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("جستجوی نام یا شماره") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(28.dp))
            Spacer(Modifier.width(8.dp))
            FilledIconButton({ ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)) }) { Icon(Icons.Default.Add, "افزودن") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(gsel == 0, { gsel = 0 }, label = { Text("همه") })
            FilterChip(gsel == -1, { gsel = -1 }, label = { Text("⭐ علاقه‌مندی‌ها") })
            groups.forEach { g -> FilterChip(gsel == g.id, { gsel = g.id }, label = { Text(g.name) }) }
        }
        Text(Jalali.fa("${list.size} مخاطب"), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (Repo.loadingContacts && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(list) { c ->
                SwipeRow(Prefs.cSwL, Prefs.cSwR, { a -> perform(a, c) }) {
                    ContactRow(c, onTap = { perform(Prefs.cTap, c) }, onLong = { perform(Prefs.cLong, c) })
                }
            }
        }
    }
}

// ---------------- گروه‌ها ----------------

@Composable
fun GroupsScreen() {
    val ctx = LocalContext.current; val db = remember { Db.get(ctx) }
    var groups by remember { mutableStateOf(db.groups()) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val members = remember(groups, dbTick.intValue) { db.allMembers() }
    val cs = MaterialTheme.colorScheme
    fun reload() { groups = db.groups(); dbTick.intValue++ }
    Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("گروه‌های مخاطبین", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FilledIconButton({ adding = true }) { Icon(Icons.Default.Add, "گروه جدید") }
        }
        Text("برای افزودن مخاطب به گروه، روی مخاطب بزنید و گروه را انتخاب کنید.",
            Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
        if (groups.any { it.allowOnly } && Prefs.on("allowGroups")) {
            Card(Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)) {
                Text("⚠️ حالت «فقط گروه مجاز» فعال است: فقط اعضای گروه‌های علامت‌خورده می‌توانند تماس بگیرند و بقیهٔ تماس‌ها (حتی ناشناس) رد می‌شود.",
                    Modifier.padding(12.dp), fontSize = 13.sp, color = cs.onTertiaryContainer)
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups) { g ->
                val cnt = members.values.count { g.id in it }
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(g.name, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, fontWeight = FontWeight.Bold)
                                Text(Jalali.fa("$cnt عضو"), fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                            IconButton({ db.deleteGroup(g.id); reload() }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🚫 رد خودکار تماس اعضا", Modifier.weight(1f), fontSize = 14.sp)
                            Switch(g.autoReject, { db.setGroupReject(g.id, it); if (it) db.setGroupAllow(g.id, false); reload() })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("✅ فقط اعضای این گروه مجازند (بقیه رد شوند)", Modifier.weight(1f), fontSize = 14.sp)
                            Switch(g.allowOnly, { db.setGroupAllow(g.id, it); if (it) db.setGroupReject(g.id, false); reload() })
                        }
                    }
                }
            }
        }
    }
    if (adding) AlertDialog({ adding = false }, title = { Text("گروه جدید") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("نام گروه") }) },
        confirmButton = { TextButton({ if (name.isNotBlank()) db.addGroup(name); name = ""; adding = false; reload() }) { Text("افزودن") } },
        dismissButton = { TextButton({ adding = false }) { Text("انصراف") } })
}
