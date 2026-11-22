package ir.callmanager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Contact(val id: Long, val name: String, val number: String) {
    val digits: String = number.filter { it.isDigit() }
    val lname: String = name.lowercase()
}

data class Rec(val number: String, val name: String, val type: Int, val date: Long, val duration: Long, val simId: String, val id: Long = 0L)
data class Sim(val handle: PhoneAccountHandle, val label: String, val index: Int, val isDefault: Boolean)
data class Hit(val name: String, val number: String)
data class DetailTarget(val number: String, val name: String, val contactId: Long = 0L)

fun loadContacts(c: Context): List<Contact> {
    val out = ArrayList<Contact>(4000)
    try {
        c.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER), null, null,
            "${Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC")?.use {
            while (it.moveToNext()) out.add(Contact(it.getLong(0), it.getString(1) ?: "", it.getString(2) ?: ""))
        }
    } catch (e: Exception) { }
    return out
}

fun loadCalls(c: Context, limit: Int = 5000): List<Rec> {
    val out = ArrayList<Rec>(1000)
    try {
        val q = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        c.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE,
                CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls._ID), q, null)?.use {
            while (it.moveToNext()) out.add(Rec(it.getString(0) ?: "", it.getString(1) ?: "", it.getInt(2), it.getLong(3),
                it.getLong(4), it.getString(5) ?: "", it.getLong(6)))
        }
    } catch (e: Exception) { }
    return out
}

fun loadSims(c: Context): List<Sim> = try {
    val tm = c.getSystemService(TelecomManager::class.java)
    val def = tm.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
    tm.callCapablePhoneAccounts.mapIndexed { i, h ->
        Sim(h, tm.getPhoneAccount(h)?.label?.toString() ?: "SIM ${i + 1}", i + 1, h == def)
    }
} catch (e: Exception) { emptyList() }

fun simName(c: Context, h: PhoneAccountHandle?): String? {
    if (h == null) return null
    val sims = loadSims(c)
    val s = sims.firstOrNull { it.handle == h } ?: return null
    return if (sims.size > 1) "SIM ${s.index} · ${s.label}" else s.label
}

object Repo {
    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var calls by mutableStateOf<List<Rec>>(emptyList())
    var names by mutableStateOf<Map<String, String>>(emptyMap())
    var sims by mutableStateOf<List<Sim>>(emptyList())
    var loadingContacts by mutableStateOf(false)
    var pickNumber by mutableStateOf<String?>(null)
    @Volatile private var busy = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** بارگذاری در پس‌زمینه تا رابط کاربری قفل نشود */
    fun refresh(c: Context) {
        if (busy) return
        busy = true
        loadingContacts = true
        val app = c.applicationContext
        scope.launch {
            try {
                sims = loadSims(app)
                if (calls.isEmpty()) calls = loadCalls(app, 60)   // نمایش سریع اولیه
                val ct = loadContacts(app)
                contacts = ct
                names = ct.associate { norm(it.number) to it.name }
                calls = loadCalls(app)
            } catch (e: Exception) {
            } finally {
                loadingContacts = false
                busy = false
            }
        }
    }
}

fun deleteCalls(c: Context, ids: List<Long>): Boolean = try {
    ids.chunked(400).forEach { ch ->
        c.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN (${ch.joinToString(",")})", null)
    }
    val gone = ids.toHashSet()
    Repo.calls = Repo.calls.filter { it.id !in gone }
    true
} catch (e: Exception) { false }

fun targetFor(number: String, name: String): DetailTarget {
    val n = norm(number)
    val c = if (n.isEmpty()) null else Repo.contacts.firstOrNull { norm(it.number) == n }
    return DetailTarget(number, name.ifBlank { c?.name ?: "" }, c?.id ?: 0L)
}

fun placeCall(c: Context, number: String, h: PhoneAccountHandle?) {
    if (number.isBlank()) return
    try {
        val tm = c.getSystemService(TelecomManager::class.java)
        val ex = Bundle()
        if (h != null) ex.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, h)
        tm.placeCall(Uri.fromParts("tel", number, null), ex)
    } catch (e: SecurityException) {
        Toast.makeText(c, "دسترسی تماس داده نشده است", Toast.LENGTH_SHORT).show()
    }
}

/** تماس با سیم‌کارت پیش‌فرض گوشی؛ اگر پیش‌فرض نبود و دو سیم‌کارت بود، انتخاب می‌پرسد */
fun callNumber(c: Context, number: String) {
    val s = Repo.sims
    val def = s.firstOrNull { it.isDefault }
    if (def != null) placeCall(c, number, def.handle)
    else if (s.size == 1) placeCall(c, number, s[0].handle)
    else if (s.size > 1) Repo.pickNumber = number
    else placeCall(c, number, null)
}

fun sendSms(c: Context, number: String) {
    try {
        c.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))
    } catch (e: Exception) {
        Toast.makeText(c, "برنامهٔ پیامک پیدا نشد", Toast.LENGTH_SHORT).show()
    }
}

fun typeName(t: Int) = when (t) {
    1 -> "ورودی"; 2 -> "خروجی"; 3 -> "بی‌پاسخ"; 4 -> "پیام صوتی"; 5 -> "ردشده"; 6 -> "مسدود"; else -> "سایر"
}

// ---------- خروجی اکسل (xlsx بدون کتابخانهٔ خارجی) ----------

fun contactsRows(c: Context): List<List<String>> {
    val db = Db.get(c)
    val notes = db.allNotes(); val mem = db.allMembers(); val refs = db.allReferrers()
    val gname = db.groups().associate { it.id to it.name }
    val rows = mutableListOf(listOf("نام", "شماره", "یادداشت", "گروه‌ها", "معرف"))
    for (ct in Repo.contacts) {
        val n = norm(ct.number)
        rows.add(listOf(ct.name, ct.number, notes[n] ?: "",
            (mem[n] ?: emptySet()).mapNotNull { gname[it] }.joinToString("، "),
            refs[n]?.let { it.second + " " + it.first } ?: ""))
    }
    return rows
}

fun callsRows(c: Context): List<List<String>> {
    val rows = mutableListOf(listOf("نام", "شماره", "نوع", "تاریخ (شمسی)", "ساعت", "مدت (ثانیه)", "مدت", "سیم‌کارت"))
    for (r in Repo.calls) {
        val sim = Repo.sims.firstOrNull { it.handle.id == r.simId }?.let { "SIM ${it.index}" } ?: ""
        val d = r.duration
        rows.add(listOf(r.name.ifBlank { Repo.names[norm(r.number)] ?: "" }, r.number, typeName(r.type),
            Jalali.plain(r.date), Jalali.plainTime(r.date), d.toString(), "%d:%02d:%02d".format(d / 3600, (d % 3600) / 60, d % 60), sim))
    }
    return rows
}

fun writeXlsx(out: OutputStream, sheetName: String, rows: List<List<String>>) {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it.code >= 32 || it == '\n' || it == '\t' }
    fun col(i: Int): String { var n = i; var s = ""; do { s = ('A' + n % 26).toString() + s; n = n / 26 - 1 } while (n >= 0); return s }
    ZipOutputStream(out).use { z ->
        fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        put("[Content_Types].xml", head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
        put("_rels/.rels", head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        put("xl/workbook.xml", head + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
            "<sheet name=\"" + esc(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
        put("xl/_rels/workbook.xml.rels", head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
        val sb = StringBuilder(head)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"").append(r + 1).append("\">")
            row.forEachIndexed { ci, v ->
                sb.append("<c r=\"").append(col(ci)).append(r + 1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(esc(v)).append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        put("xl/worksheets/sheet1.xml", sb.toString())
    }
}
