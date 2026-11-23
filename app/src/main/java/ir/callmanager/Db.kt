package ir.callmanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Group(val id: Int, val name: String, val autoReject: Boolean, val allowOnly: Boolean = false)

fun norm(n: String?): String = (n ?: "").filter { it.isDigit() }.takeLast(10)

class Db private constructor(c: Context) : SQLiteOpenHelper(c, "app.db", null, 5) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE notes(num TEXT PRIMARY KEY, note TEXT)")
        d.execSQL("CREATE TABLE groups_t(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, reject INTEGER DEFAULT 0, allow INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE members(num TEXT, gid INTEGER, PRIMARY KEY(num, gid))")
        d.execSQL("CREATE TABLE blocked(num TEXT PRIMARY KEY)")
        createRef(d)
        d.execSQL("CREATE TABLE sim_choices(num TEXT PRIMARY KEY, account_id TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        listOf("خانواده", "دوستان", "همکاران").forEach { d.execSQL("INSERT INTO groups_t(name) VALUES(?)", arrayOf(it)) }
    }

    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {
        if (o < 3) createRef(d)
        if (o < 4) try { d.execSQL("ALTER TABLE groups_t ADD COLUMN allow INTEGER DEFAULT 0") } catch (e: Exception) { }
        if (o < 5) d.execSQL("CREATE TABLE IF NOT EXISTS sim_choices(num TEXT PRIMARY KEY, account_id TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    }

    private fun createRef(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE IF NOT EXISTS referrers(num TEXT PRIMARY KEY, rnum TEXT, rname TEXT)")
        d.execSQL("CREATE TABLE IF NOT EXISTS favorites(num TEXT PRIMARY KEY)")
    }


    fun saveSimChoice(num: String, accountId: String) {
        val n = norm(num); if (n.isEmpty() || accountId.isBlank()) return
        writableDatabase.insertWithOnConflict("sim_choices", null, ContentValues().apply {
            put("num", n); put("account_id", accountId); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun simChoice(num: String): String? = readableDatabase.rawQuery(
        "SELECT account_id FROM sim_choices WHERE num=?", arrayOf(norm(num))
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun note(num: String): String =
        readableDatabase.rawQuery("SELECT note FROM notes WHERE num=?", arrayOf(norm(num))).use { if (it.moveToFirst()) it.getString(0) else "" }

    fun setNote(num: String, note: String) {
        if (norm(num).isEmpty()) return
        if (note.isBlank()) writableDatabase.delete("notes", "num=?", arrayOf(norm(num)))
        else writableDatabase.insertWithOnConflict("notes", null,
            ContentValues().apply { put("num", norm(num)); put("note", note.trim()) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun allNotes(): Map<String, String> = readableDatabase.rawQuery("SELECT num,note FROM notes", null).use {
        val m = HashMap<String, String>(); while (it.moveToNext()) m[it.getString(0)] = it.getString(1); m
    }

    fun groups(): List<Group> = readableDatabase.rawQuery("SELECT id,name,reject,allow FROM groups_t ORDER BY id", null).use {
        val l = mutableListOf<Group>(); while (it.moveToNext()) l += Group(it.getInt(0), it.getString(1), it.getInt(2) == 1, it.getInt(3) == 1); l
    }

    fun addGroup(name: String) { writableDatabase.execSQL("INSERT INTO groups_t(name) VALUES(?)", arrayOf(name.trim())) }

    fun deleteGroup(id: Int) {
        writableDatabase.delete("groups_t", "id=?", arrayOf("$id")); writableDatabase.delete("members", "gid=?", arrayOf("$id"))
    }

    fun setGroupReject(id: Int, on: Boolean) {
        writableDatabase.execSQL("UPDATE groups_t SET reject=? WHERE id=?", arrayOf(if (on) 1 else 0, id))
    }

    fun setGroupAllow(id: Int, on: Boolean) {
        writableDatabase.execSQL("UPDATE groups_t SET allow=? WHERE id=?", arrayOf(if (on) 1 else 0, id))
    }

    /** آیا حداقل یک گروه در حالت «فقط اعضا مجاز» است؟ */
    fun allowOnlyActive() = readableDatabase.rawQuery("SELECT 1 FROM groups_t WHERE allow=1", null).use { it.moveToFirst() }

    fun inAllowGroup(num: String): Boolean {
        val n = norm(num); if (n.isEmpty()) return false
        return readableDatabase.rawQuery(
            "SELECT 1 FROM members m JOIN groups_t g ON g.id=m.gid WHERE m.num=? AND g.allow=1", arrayOf(n)).use { it.moveToFirst() }
    }

    fun inRejectGroup(num: String): Boolean {
        val n = norm(num); if (n.isEmpty()) return false
        return readableDatabase.rawQuery(
            "SELECT 1 FROM members m JOIN groups_t g ON g.id=m.gid WHERE m.num=? AND g.reject=1", arrayOf(n)).use { it.moveToFirst() }
    }

    fun memberGroups(num: String): Set<Int> = readableDatabase.rawQuery("SELECT gid FROM members WHERE num=?", arrayOf(norm(num))).use {
        val s = mutableSetOf<Int>(); while (it.moveToNext()) s += it.getInt(0); s
    }

    fun allMembers(): Map<String, Set<Int>> = readableDatabase.rawQuery("SELECT num,gid FROM members", null).use {
        val m = HashMap<String, MutableSet<Int>>()
        while (it.moveToNext()) m.getOrPut(it.getString(0)) { mutableSetOf() }.add(it.getInt(1))
        m
    }

    fun setMembers(num: String, ids: Collection<Int>) {
        if (norm(num).isEmpty()) return
        val d = writableDatabase; d.delete("members", "num=?", arrayOf(norm(num)))
        ids.forEach { d.execSQL("INSERT OR IGNORE INTO members(num,gid) VALUES(?,?)", arrayOf(norm(num), it)) }
    }

    fun referrer(num: String): Pair<String, String>? =
        readableDatabase.rawQuery("SELECT rnum,rname FROM referrers WHERE num=?", arrayOf(norm(num))).use {
            if (it.moveToFirst()) Pair(it.getString(0) ?: "", it.getString(1) ?: "") else null
        }

    fun allReferrers(): Map<String, Pair<String, String>> = readableDatabase.rawQuery("SELECT num,rnum,rname FROM referrers", null).use {
        val m = HashMap<String, Pair<String, String>>()
        while (it.moveToNext()) m[it.getString(0)] = Pair(it.getString(1) ?: "", it.getString(2) ?: ""); m
    }

    fun setReferrer(num: String, rnum: String?, rname: String?) {
        if (norm(num).isEmpty()) return
        if (rnum.isNullOrBlank()) writableDatabase.delete("referrers", "num=?", arrayOf(norm(num)))
        else writableDatabase.insertWithOnConflict("referrers", null, ContentValues().apply {
            put("num", norm(num)); put("rnum", rnum); put("rname", rname ?: "")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isBlocked(num: String) = readableDatabase.rawQuery("SELECT 1 FROM blocked WHERE num=?", arrayOf(norm(num))).use { it.moveToFirst() }

    fun setBlocked(num: String, on: Boolean) {
        if (norm(num).isEmpty()) return
        if (on) writableDatabase.execSQL("INSERT OR IGNORE INTO blocked(num) VALUES(?)", arrayOf(norm(num)))
        else writableDatabase.delete("blocked", "num=?", arrayOf(norm(num)))
    }

    fun isFav(num: String) = readableDatabase.rawQuery("SELECT 1 FROM favorites WHERE num=?", arrayOf(norm(num))).use { it.moveToFirst() }

    fun setFav(num: String, on: Boolean) {
        if (norm(num).isEmpty()) return
        if (on) writableDatabase.execSQL("INSERT OR IGNORE INTO favorites(num) VALUES(?)", arrayOf(norm(num)))
        else writableDatabase.delete("favorites", "num=?", arrayOf(norm(num)))
    }

    fun allFavs(): Set<String> = readableDatabase.rawQuery("SELECT num FROM favorites", null).use {
        val s = HashSet<String>(); while (it.moveToNext()) s.add(it.getString(0)); s
    }

    fun allBlocked(): List<String> = readableDatabase.rawQuery("SELECT num FROM blocked", null).use {
        val l = ArrayList<String>(); while (it.moveToNext()) l.add(it.getString(0)); l
    }

    /** پشتیبان‌گیری از یادداشت‌ها، گروه‌ها، معرف‌ها، علاقه‌مندی‌ها و شماره‌های مسدود */
    fun exportJson(): String {
        val o = JSONObject()
        val gl = groups()
        val gs = JSONArray(); gl.forEach { gs.put(JSONObject().put("name", it.name).put("reject", it.autoReject).put("allow", it.allowOnly)) }
        o.put("groups", gs)
        val ns = JSONObject(); allNotes().forEach { (k, v) -> ns.put(k, v) }; o.put("notes", ns)
        val gname = gl.associate { it.id to it.name }
        val ms = JSONObject(); allMembers().forEach { (k, v) -> ms.put(k, JSONArray(v.mapNotNull { gname[it] })) }; o.put("members", ms)
        o.put("blocked", JSONArray(allBlocked()))
        val rs = JSONObject(); allReferrers().forEach { (k, v) -> rs.put(k, JSONArray(listOf(v.first, v.second))) }; o.put("referrers", rs)
        o.put("favorites", JSONArray(allFavs().toList()))
        o.put("replies", JSONArray(Prefs.quickReplies))
        o.put("version", 1)
        return o.toString(2)
    }

    fun importJson(text: String): Boolean = try {
        val o = JSONObject(text)
        val gs = o.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until gs.length()) {
            val g = gs.getJSONObject(i); val name = g.getString("name")
            var id = groups().firstOrNull { it.name == name }?.id
            if (id == null) { addGroup(name); id = groups().firstOrNull { it.name == name }?.id }
            if (id != null && g.optBoolean("reject", false)) setGroupReject(id, true)
            if (id != null && g.optBoolean("allow", false)) setGroupAllow(id, true)
        }
        val idByName = groups().associate { it.name to it.id }
        val ns = o.optJSONObject("notes")
        if (ns != null) { val k = ns.keys(); while (k.hasNext()) { val n = k.next(); setNote(n, ns.getString(n)) } }
        val ms = o.optJSONObject("members")
        if (ms != null) {
            val k = ms.keys()
            while (k.hasNext()) {
                val n = k.next(); val arr = ms.getJSONArray(n)
                val ids = HashSet<Int>(memberGroups(n))
                for (i in 0 until arr.length()) idByName[arr.getString(i)]?.let { ids.add(it) }
                setMembers(n, ids)
            }
        }
        val bl = o.optJSONArray("blocked")
        if (bl != null) for (i in 0 until bl.length()) setBlocked(bl.getString(i), true)
        val rs = o.optJSONObject("referrers")
        if (rs != null) { val k = rs.keys(); while (k.hasNext()) { val n = k.next(); val a = rs.getJSONArray(n); setReferrer(n, a.getString(0), a.optString(1, "")) } }
        val fv = o.optJSONArray("favorites")
        if (fv != null) for (i in 0 until fv.length()) setFav(fv.getString(i), true)
        val rp = o.optJSONArray("replies")
        if (rp != null) {
            val l = ArrayList<String>(); for (i in 0 until rp.length()) l.add(rp.getString(i))
            Prefs.quickReplies = l; Prefs.saveReplies()
        }
        true
    } catch (e: Exception) { false }

    /** آیا تماس ورودی از این شماره باید خودکار رد شود؟ */
    fun shouldReject(num: String): Boolean {
        val n = norm(num); if (n.isEmpty()) return false
        if (isBlocked(n)) return true
        return readableDatabase.rawQuery(
            "SELECT 1 FROM members m JOIN groups_t g ON g.id=m.gid WHERE m.num=? AND g.reject=1", arrayOf(n)).use { it.moveToFirst() }
    }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(c: Context) = inst ?: synchronized(this) { inst ?: Db(c.applicationContext).also { inst = it } }
    }
}
