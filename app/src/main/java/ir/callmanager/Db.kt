package ir.callmanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Group(val id: Int, val name: String, val autoReject: Boolean)

fun norm(n: String?): String = (n ?: "").filter { it.isDigit() }.takeLast(10)

class Db private constructor(c: Context) : SQLiteOpenHelper(c, "app.db", null, 2) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE notes(num TEXT PRIMARY KEY, note TEXT)")
        d.execSQL("CREATE TABLE groups_t(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, reject INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE members(num TEXT, gid INTEGER, PRIMARY KEY(num, gid))")
        d.execSQL("CREATE TABLE blocked(num TEXT PRIMARY KEY)")
        createRef(d)
        listOf("خانواده", "دوستان", "همکاران").forEach { d.execSQL("INSERT INTO groups_t(name) VALUES(?)", arrayOf(it)) }
    }

    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) { if (o < 2) createRef(d) }

    private fun createRef(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE IF NOT EXISTS referrers(num TEXT PRIMARY KEY, rnum TEXT, rname TEXT)")
    }

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

    fun groups(): List<Group> = readableDatabase.rawQuery("SELECT id,name,reject FROM groups_t ORDER BY id", null).use {
        val l = mutableListOf<Group>(); while (it.moveToNext()) l += Group(it.getInt(0), it.getString(1), it.getInt(2) == 1); l
    }

    fun addGroup(name: String) { writableDatabase.execSQL("INSERT INTO groups_t(name) VALUES(?)", arrayOf(name.trim())) }

    fun deleteGroup(id: Int) {
        writableDatabase.delete("groups_t", "id=?", arrayOf("$id")); writableDatabase.delete("members", "gid=?", arrayOf("$id"))
    }

    fun setGroupReject(id: Int, on: Boolean) {
        writableDatabase.execSQL("UPDATE groups_t SET reject=? WHERE id=?", arrayOf(if (on) 1 else 0, id))
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
