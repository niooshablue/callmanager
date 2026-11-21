package ir.callmanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Group(val id: Int, val name: String, val autoReject: Boolean)

fun norm(n: String?): String = (n ?: "").filter { it.isDigit() }.takeLast(10)

class Db private constructor(c: Context) : SQLiteOpenHelper(c, "app.db", null, 1) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE notes(num TEXT PRIMARY KEY, note TEXT)")
        d.execSQL("CREATE TABLE groups_t(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, reject INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE members(num TEXT, gid INTEGER, PRIMARY KEY(num, gid))")
        d.execSQL("CREATE TABLE blocked(num TEXT PRIMARY KEY)")
        listOf("خانواده", "دوستان", "همکاران").forEach { d.execSQL("INSERT INTO groups_t(name) VALUES(?)", arrayOf(it)) }
    }
    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {}

    fun note(num: String): String =
        readableDatabase.rawQuery("SELECT note FROM notes WHERE num=?", arrayOf(norm(num))).use { if (it.moveToFirst()) it.getString(0) else "" }

    fun setNote(num: String, note: String) {
        if (note.isBlank()) writableDatabase.delete("notes", "num=?", arrayOf(norm(num)))
        else writableDatabase.insertWithOnConflict("notes", null,
            ContentValues().apply { put("num", norm(num)); put("note", note.trim()) }, SQLiteDatabase.CONFLICT_REPLACE)
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
    fun setMembers(num: String, ids: Collection<Int>) {
        val d = writableDatabase; d.delete("members", "num=?", arrayOf(norm(num)))
        ids.forEach { d.execSQL("INSERT OR IGNORE INTO members(num,gid) VALUES(?,?)", arrayOf(norm(num), it)) }
    }
    fun isBlocked(num: String) = readableDatabase.rawQuery("SELECT 1 FROM blocked WHERE num=?", arrayOf(norm(num))).use { it.moveToFirst() }
    fun setBlocked(num: String, on: Boolean) {
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
