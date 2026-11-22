@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package ir.callmanager

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val dial = intent?.data?.schemeSpecificPart ?: ""
        setContent { AppTheme { Root(dial) } }
    }

    override fun onResume() {
        super.onResume()
        if (hasPerms(this)) Repo.refresh(this)
    }
}

val PERMS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)

fun hasPerms(c: Context) = PERMS.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun Root(initialDial: String) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(hasPerms(ctx)) }
    var roleTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = hasPerms(ctx); if (ok) Repo.refresh(ctx)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleTick++ }
    LaunchedEffect(Unit) { if (!ok) permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS) else Repo.refresh(ctx) }

    val rm = ctx.getSystemService(RoleManager::class.java)
    val isDialer = remember(roleTick) { rm.isRoleHeld(RoleManager.ROLE_DIALER) }
    val isScreener = remember(roleTick) { rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }

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
            topBar = { TopAppBar(title = { Text(tabs[tab].first, fontWeight = FontWeight.Bold) }) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(tab == i, { tab = i }, { Icon(t.second, null) }, label = { Text(t.first, fontSize = 11.sp, maxLines = 1) })
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (!ok) {
                    Banner("برای نمایش تماس‌ها و مخاطبین، دسترسی‌ها لازم است.", "اعطای دسترسی") {
                        permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    if (!isDialer) Banner("برای نمایش یادداشت هنگام تماس، برنامه را «شماره‌گیر پیش‌فرض» کنید.", "فعال‌سازی") {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    }
                    if (!isScreener) Banner("برای رد خودکار تماس، دسترسی «فیلتر تماس» لازم است.", "فعال‌سازی") {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
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
    var filter by remember { mutableIntStateOf(0) }
    val all = Repo.calls
    val list = remember(all, filter) {
        when (filter) {
            1 -> all.filter { it.type == 3 }
            2 -> all.filter { it.type == 1 }
            3 -> all.filter { it.type == 2 }
            else -> all
        }
    }
    val grouped = remember(list) { list.groupBy { Jalali.dayIndex(it.date) } }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("همه", "بی‌پاسخ", "ورودی", "خروجی").forEachIndexed { i, t ->
                FilterChip(filter == i, { filter = i }, label = { Text(t) })
            }
        }
        if (list.isEmpty()) Empty(if (Repo.loadingContacts) "در حال بارگذاری…" else "تماسی وجود ندارد")
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            grouped.forEach { (_, rows) ->
                item {
                    Text(Jalali.dayLabel(rows.first().date), Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                items(rows) { r -> CallRow(r, onOpen) }
            }
        }
    }
}

@Composable
fun CallRow(r: Rec, onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val title = r.name.ifBlank { Repo.names[norm(r.number)] ?: "" }.ifBlank { Jalali.fa(r.number).ifBlank { "ناشناس" } }
    val simIdx = Repo.sims.indexOfFirst { it.handle.id == r.simId }
    val sim = if (simIdx >= 0 && Repo.sims.size > 1) "  •  SIM ${Jalali.fa(simIdx + 1)}" else ""
    val dur = if (r.duration > 0) "  •  " + fmtDur(r.duration) else ""
    Row(Modifier.fillMaxWidth().clickable { onOpen(targetFor(r.number, r.name)) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CallTypeBadge(r.type)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (r.type == 3) Color(0xFFE53935) else cs.onSurface)
            Text(Jalali.time(r.date) + dur + sim, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        IconButton({ callNumber(ctx, r.number) }) { Handset(cs.primary, 22.dp) }
    }
}

// ---------------- شماره‌گیر ----------------

@Composable
fun DialerScreen(initial: String, onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var num by remember { mutableStateOf(initial) }
    val q = num.filter { it.isDigit() }
    val contacts = Repo.contacts
    val calls = Repo.calls
    val results = remember(q, contacts, calls) {
        if (q.isEmpty()) emptyList<Hit>()
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
            if (q.isEmpty()) Empty("شماره را وارد کنید؛ مخاطبین و تماس‌های مشابه اینجا نمایش داده می‌شوند")
            else if (results.isEmpty()) Empty("موردی یافت نشد")
            else LazyColumn {
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
            SimCallRow(num)
        }
    }
}

// ---------------- مخاطبین ----------------

@Composable
fun ContactsScreen(onOpen: (DetailTarget) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var q by remember { mutableStateOf("") }
    var gsel by remember { mutableIntStateOf(0) }
    val groups = remember(dbTick.intValue) { db.groups() }
    val members = remember(dbTick.intValue) { db.allMembers() }
    val all = Repo.contacts
    val list = remember(all, q, gsel, members) {
        val qn = Jalali.en(q).trim().lowercase()
        val qd = qn.filter { it.isDigit() }
        all.filter { c ->
            (qn.isEmpty() || c.lname.contains(qn) || (qd.isNotEmpty() && c.digits.contains(qd))) &&
                (gsel == 0 || members[norm(c.number)]?.contains(gsel) == true)
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
            groups.forEach { g -> FilterChip(gsel == g.id, { gsel = g.id }, label = { Text(g.name) }) }
        }
        Text(Jalali.fa("${list.size} مخاطب"), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (Repo.loadingContacts && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn {
            items(list) { c ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(DetailTarget(c.number, c.name, c.id)) },
                    leadingContent = { Avatar(c.name) },
                    headlineContent = { Text(c.name, maxLines = 1) },
                    supportingContent = { Text(Jalali.fa(c.number), fontSize = 12.sp) },
                    trailingContent = {
                        Row {
                            IconButton({ sendSms(ctx, c.number) }) { Icon(Icons.Default.Email, "پیامک", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton({ callNumber(ctx, c.number) }) { Handset(MaterialTheme.colorScheme.primary, 22.dp) }
                        }
                    })
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
    fun reload() { groups = db.groups(); dbTick.intValue++ }
    Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("گروه‌های مخاطبین", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FilledIconButton({ adding = true }) { Icon(Icons.Default.Add, "گروه جدید") }
        }
        Text("برای افزودن مخاطب به گروه، روی مخاطب بزنید و گروه را انتخاب کنید. با روشن‌کردن سوییچ، تماس اعضای گروه خودکار رد می‌شود.",
            Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups) { g ->
                val cnt = members.values.count { g.id in it }
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(g.name, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontWeight = FontWeight.Bold)
                            Text(Jalali.fa("$cnt عضو") + if (g.autoReject) " • رد خودکار" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(g.autoReject, { db.setGroupReject(g.id, it); reload() })
                        IconButton({ db.deleteGroup(g.id); reload() }) { Icon(Icons.Default.Delete, "حذف") }
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

val dbTick = mutableIntStateOf(0)
