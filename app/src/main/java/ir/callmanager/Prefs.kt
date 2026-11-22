package ir.callmanager

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily

object Prefs {
    private var sp: SharedPreferences? = null
    var bg by mutableIntStateOf(0)
    var card by mutableIntStateOf(0)
    var text by mutableIntStateOf(0)
    var accent by mutableIntStateOf(0)
    var fontScale by mutableFloatStateOf(1f)
    var font by mutableIntStateOf(0)
    var persian by mutableStateOf(true)
    var rejectUnknown by mutableStateOf(false)

    // حرکات: 0 هیچ، 1 جزئیات، 2 تماس، 3 پیامک، 4 حذف از تاریخچه، 5 انتخاب چندتایی، 6 رد خودکار
    var hTap by mutableIntStateOf(1)
    var hLong by mutableIntStateOf(5)
    var hSwL by mutableIntStateOf(2)
    var hSwR by mutableIntStateOf(3)
    var cTap by mutableIntStateOf(1)
    var cLong by mutableIntStateOf(0)
    var cSwL by mutableIntStateOf(2)
    var cSwR by mutableIntStateOf(3)

    var missedNotify by mutableStateOf(true)
    var missedBadge by mutableStateOf(true)
    var missedCount = 0
    var missedLabels = ""
    var missedLast = ""

    fun init(c: Context) {
        val s = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sp = s
        bg = s.getInt("bg", 0); card = s.getInt("card", 0); text = s.getInt("text", 0); accent = s.getInt("accent", 0)
        fontScale = s.getFloat("fontScale", 1f); font = s.getInt("font", 0)
        persian = s.getBoolean("persian", true); rejectUnknown = s.getBoolean("rejectUnknown", false)
        hTap = s.getInt("hTap", 1); hLong = s.getInt("hLong", 5); hSwL = s.getInt("hSwL", 2); hSwR = s.getInt("hSwR", 3)
        cTap = s.getInt("cTap", 1); cLong = s.getInt("cLong", 0); cSwL = s.getInt("cSwL", 2); cSwR = s.getInt("cSwR", 3)
        missedNotify = s.getBoolean("missedNotify", true); missedBadge = s.getBoolean("missedBadge", true)
        missedCount = s.getInt("missedCount", 0); missedLabels = s.getString("missedLabels", "") ?: ""; missedLast = s.getString("missedLast", "") ?: ""
    }

    fun save() {
        sp?.edit()?.putInt("bg", bg)?.putInt("card", card)?.putInt("text", text)?.putInt("accent", accent)
            ?.putFloat("fontScale", fontScale)?.putInt("font", font)
            ?.putBoolean("persian", persian)?.putBoolean("rejectUnknown", rejectUnknown)
            ?.putInt("hTap", hTap)?.putInt("hLong", hLong)?.putInt("hSwL", hSwL)?.putInt("hSwR", hSwR)
            ?.putInt("cTap", cTap)?.putInt("cLong", cLong)?.putInt("cSwL", cSwL)?.putInt("cSwR", cSwR)
            ?.putBoolean("missedNotify", missedNotify)?.putBoolean("missedBadge", missedBadge)?.apply()
    }

    fun reset() { bg = 0; card = 0; text = 0; accent = 0; fontScale = 1f; font = 0; save() }

    fun addMissed(label: String, number: String) {
        missedCount++; missedLast = number
        missedLabels = (missedLabels.split("\n").filter { it.isNotBlank() } + label).takeLast(3).joinToString("\n")
        persistMissed()
    }

    fun clearMissed() { missedCount = 0; missedLabels = ""; missedLast = ""; persistMissed() }

    private fun persistMissed() {
        sp?.edit()?.putInt("missedCount", missedCount)?.putString("missedLabels", missedLabels)?.putString("missedLast", missedLast)?.apply()
    }

    val fontNames = listOf("پیش‌فرض سیستم", "Sans Serif", "Serif", "Monospace", "Cursive")
    fun family(): FontFamily = when (font) {
        1 -> FontFamily.SansSerif
        2 -> FontFamily.Serif
        3 -> FontFamily.Monospace
        4 -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    val bgPresets = listOf(0, 0xFFF8F9FB.toInt(), 0xFFFFF8E7.toInt(), 0xFFECEFF1.toInt(), 0xFFE3F2FD.toInt(), 0xFFE8F5E9.toInt(),
        0xFF121212.toInt(), 0xFF0D1B2A.toInt(), 0xFF1E1B2E.toInt(), 0xFF000000.toInt())
    val cardPresets = listOf(0, 0xFFFFFFFF.toInt(), 0xFFF1F3F8.toInt(), 0xFFFFF3E0.toInt(), 0xFFE1F5FE.toInt(),
        0xFF1E1E1E.toInt(), 0xFF1B263B.toInt(), 0xFF2A2540.toInt(), 0xFF37474F.toInt())
    val textPresets = listOf(0, 0xFF000000.toInt(), 0xFF212121.toInt(), 0xFF37474F.toInt(), 0xFF1A237E.toInt(),
        0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFFFFEB3B.toInt())
    val accentPresets = listOf(0, 0xFF1E6BFF.toInt(), 0xFF00897B.toInt(), 0xFFE53935.toInt(), 0xFF8E24AA.toInt(),
        0xFFFB8C00.toInt(), 0xFF43A047.toInt(), 0xFF3949AB.toInt())
}
