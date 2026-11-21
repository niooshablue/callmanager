package ir.callmanager

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx), content = content)
}

val dbTick = mutableIntStateOf(0)

data class Contact(val name: String, val number: String)
data class Rec(val number: String, val name: String, val type: Int, val date: Long)

fun loadContacts(c: Context): List<Contact> {
    val out = mutableListOf<Contact>()
    val p = ContactsContract.CommonDataKinds.Phone
    c.contentResolver.query(p.CONTENT_URI, arrayOf(p.DISPLAY_NAME, p.NUMBER), null, null, "${p.DISPLAY_NAME} COLLATE LOCALIZED ASC")
        ?.use { while (it.moveToNext()) out += Contact(it.getString(0) ?: "", it.getString(1) ?: "") }
    return out
}

fun loadCalls(c: Context): List<Rec> {
    val out = mutableListOf<Rec>()
    val q = android.os.Bundle().apply {
        putInt(ContentResolver.QUERY_ARG_LIMIT, 300)
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
    }
    c.contentResolver.query(CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE), q, null)
        ?.use { while (it.moveToNext()) out += Rec(it.getString(0) ?: "", it.getString(1) ?: "", it.getInt(2), it.getLong(3)) }
    return out
}

fun placeCall(c: Context, n: String) {
    if (n.isBlank()) return
    c.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null)))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val dial = intent?.data?.schemeSpecificPart ?: ""
        setContent { AppTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Root(dial) } } }
    }
}

private val PERMS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(initialDial: String) {
    val ctx = LocalContext.current
    fun granted() = PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(granted()) }
    var roleTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = granted() }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleTick++ }
    LaunchedEffect(Unit) { if (!ok) permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS) }

    val rm = ctx.getSystemService(RoleManager::class.java)
    val isDialer = remember(roleTick) { rm.isRoleHeld(RoleManager.ROLE_DIALER) }
    val isScreener = remember(roleTick) { rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }

    var tab by remember { mutableIntStateOf(if (initialDial.isNotBlank()) 1 else 0) }
    val tabs = listOf("تماس‌ها" to Icons.Default.DateRange, "شماره‌گیر" to Icons.Default.Call,
        "مخاطبین" to Icons.Default.Person, "گروه‌ها" to Icons.Default.Star)

    Scaffold(
        topBar = { TopAppBar(title = { Text("مدیریت تماس") }) },
        bottomBar = { NavigationBar { tabs.forEachIndexed { i, t ->
            NavigationBarItem(tab == i, { tab = i }, { Icon(t.second, null) }, label = { Text(t.first) }) } } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!ok) {
                Banner("برای نمایش تماس‌ها و مخاطبین، دسترسی‌ها لازم است.", "اعطای دسترسی") { permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS) }
            } else {
                if (!isDialer) Banner("برای نمایش یادداشت هنگام تماس، برنامه را «شماره‌گیر پیش‌فرض» کنید.", "فعال‌سازی") {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) }
                if (!isScreener) Banner("برای رد خودکار تماس، دسترسی «فیلتر تماس» لازم است.", "فعال‌سازی") {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
                when (tab) {
                    0 -> RecentsScreen()
                    1 -> DialerScreen(initialDial)
                    2 -> ContactsScreen()
                    else -> GroupsScreen()
                }
            }
        }
    }
}

@Composable
fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp, 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 13.sp)
            Spacer(Modifier.width(8.dp)); Button(onClick) { Text(action) }
        }
    }
}

@Composable
fun Avatar(name: String) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).ifBlank { "#" }, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun RecentsScreen() {
    val ctx = LocalContext.current
    val list = remember { loadCalls(ctx) }
    var sel by remember { mutableStateOf<Rec?>(null) }
    if (list.isEmpty()) Empty("تاریخچهٔ تماسی وجود ندارد")
    LazyColumn {
        items(list) { r ->
            val (sym, col) = when (r.type) {
                CallLog.Calls.OUTGOING_TYPE -> "↗" to Color(0xFF2E7D32)
                CallLog.Calls.INCOMING_TYPE -> "↙" to Color(0xFF1565C0)
                CallLog.Calls.MISSED_TYPE -> "✕" to Color(0xFFD32F2F)
                CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "⛔" to Color(0xFFD32F2F)
                else -> "•" to Color.Gray
            }
            ListItem(
                modifier = Modifier.clickable { sel = r },
                leadingContent = { Avatar(r.name.ifBlank { r.number }) },
                headlineContent = { Text(r.name.ifBlank { Jalali.fa(r.number).ifBlank { "ناشناس" } }) },
                supportingContent = { Text(Jalali.format(r.date), fontSize = 12.sp) },
                trailingContent = { Text(sym, color = col, fontSize = 20.sp) })
        }
    }
    sel?.let { NumberDialog(it.number, it.name) { sel = null } }
}

@Composable
fun DialerScreen(initial: String) {
    val ctx = LocalContext.current
    var num by remember { mutableStateOf(initial) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(num, fontSize = 34.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center, maxLines = 1)
            listOf("123", "456", "789", "*0#").forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { ch ->
                        FilledTonalButton({ num += ch }, Modifier.padding(6.dp).size(76.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
                            Text(ch.toString(), fontSize = 26.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Spacer(Modifier.size(56.dp))
                FloatingActionButton({ placeCall(ctx, num) }, containerColor = Color(0xFF2E7D32), contentColor = Color.White, shape = CircleShape,
                    modifier = Modifier.size(76.dp)) { Icon(Icons.Default.Call, "تماس") }
                IconButton({ num = num.dropLast(1) }, Modifier.size(56.dp)) { Icon(Icons.Default.Clear, "حذف") }
            }
        }
    }
}

@Composable
fun ContactsScreen() {
    val ctx = LocalContext.current
    val all = remember { loadContacts(ctx) }
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Contact?>(null) }
    val list = all.filter { q.isBlank() || it.name.contains(q, true) || it.number.contains(q) }
    Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("جستجو") },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(8.dp))
            FilledIconButton({ ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)) }) { Icon(Icons.Default.Add, "افزودن") }
        }
        LazyColumn {
            items(list) { c ->
                ListItem(Modifier.clickable { sel = c },
                    leadingContent = { Avatar(c.name) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(Jalali.fa(c.number)) })
            }
        }
    }
    sel?.let { NumberDialog(it.number, it.name) { sel = null } }
}

@Composable
fun GroupsScreen() {
    val ctx = LocalContext.current; val db = Db.get(ctx)
    var groups by remember { mutableStateOf(db.groups()) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    fun reload() { groups = db.groups(); dbTick.intValue++ }
    Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("گروه‌های مخاطبین", Modifier.weight(1f), fontSize = 18.sp)
            FilledIconButton({ adding = true }) { Icon(Icons.Default.Add, "گروه جدید") }
        }
        Text("برای افزودن مخاطب به گروه، روی مخاطب بزنید و گروه را انتخاب کنید.", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(groups) { g ->
                ListItem(
                    headlineContent = { Text(g.name) },
                    supportingContent = { Text(if (g.autoReject) "رد خودکار تماس: فعال" else "رد خودکار تماس: غیرفعال", fontSize = 12.sp) },
                    leadingContent = { Switch(g.autoReject, { db.setGroupReject(g.id, it); reload() }) },
                    trailingContent = { IconButton({ db.deleteGroup(g.id); reload() }) { Icon(Icons.Default.Delete, "حذف") } })
            }
        }
    }
    if (adding) AlertDialog({ adding = false }, title = { Text("گروه جدید") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("نام گروه") }) },
        confirmButton = { TextButton({ if (name.isNotBlank()) db.addGroup(name); name = ""; adding = false; reload() }) { Text("افزودن") } },
        dismissButton = { TextButton({ adding = false }) { Text("انصراف") } })
}

@Composable
fun NumberDialog(num: String, name: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val db = Db.get(ctx)
    var note by remember { mutableStateOf(db.note(num)) }
    var blocked by remember { mutableStateOf(db.isBlocked(num)) }
    val groups = remember { db.groups() }
    val mem = remember { mutableStateListOf<Int>().apply { addAll(db.memberGroups(num)) } }
    AlertDialog(onDismiss,
        title = { Text(name.ifBlank { Jalali.fa(num) }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(Jalali.fa(num), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت (هنگام تماس نمایش داده می‌شود)") }, minLines = 2)
                Spacer(Modifier.height(12.dp))
                Text("گروه‌ها", fontSize = 14.sp)
                groups.forEach { g ->
                    Row(Modifier.fillMaxWidth().clickable { if (g.id in mem) mem.remove(g.id) else mem.add(g.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(g.id in mem, { if (it) mem.add(g.id) else mem.remove(g.id) }); Text(g.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(blocked, { blocked = it }); Spacer(Modifier.width(8.dp)); Text("رد خودکار تماس این شماره")
                }
            }
        },
        confirmButton = { TextButton({
            db.setNote(num, note); db.setMembers(num, mem); db.setBlocked(num, blocked); dbTick.intValue++; onDismiss() }) { Text("ذخیره") } },
        dismissButton = { Row {
            TextButton({ placeCall(ctx, num); onDismiss() }) { Text("تماس") }
            TextButton(onDismiss) { Text("بستن") } } })
}

@Composable
fun Empty(t: String) { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text(t, color = Color.Gray) } }
