package ir.callmanager

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight

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

    val defaultReplies = listOf("الان نمی‌توانم صحبت کنم، بعداً تماس می‌گیرم.", "در جلسه هستم، لطفاً پیام بدهید.", "تا چند دقیقهٔ دیگر تماس می‌گیرم.")
    var quickReplies by mutableStateOf(defaultReplies)

    var callDark by mutableStateOf(false)
    var callTheme by mutableIntStateOf(0)      // 0 رنگی/گرادیان، 1 ساده، 2 تیره AMOLED
    // 0 دکمه‌های گرد، 1 کشویی iOS، 2 کپسولی، 3 کشیدن بالا/پایین، 4 کشیدن دایره‌ها (Galaxy)، 5 کشویی دوطرفه
    var answerStyle by mutableIntStateOf(0)
    // 0 کلاسیک، 1 دایره‌ای، 2 مینیمال، 3 iPhone، 4 Galaxy
    var dialerStyle by mutableIntStateOf(0)

    // ---- قالب‌های ظاهری ----
    var uiStyle by mutableIntStateOf(0)        // 0 مدرن، 1 iPhone 17، 2 Galaxy S26
    var navStyle by mutableIntStateOf(-1)      // -1 خودکار (مطابق قالب)، 0 شناور مدرن، 1 شیشه‌ای iPhone، 2 چسبیده Galaxy، 3 کلاسیک
    var headerStyle by mutableIntStateOf(-1)   // -1 خودکار، 0 گرادیان مدرن، 1 عنوان بزرگ iPhone، 2 Galaxy، 3 فشرده
    var callLayout by mutableIntStateOf(0)     // 0 مدرن، 1 iPhone، 2 Galaxy
    var darkMode by mutableIntStateOf(0)       // 0 سیستم، 1 روشن، 2 تیره
    // فیلتر گروه‌ها: 0 خاموش، 1 رد گروه‌های انتخابی، 2 فقط گروه‌های انتخابی مجازند
    var groupMode by mutableIntStateOf(0)

    fun nav(): Int = if (navStyle >= 0) navStyle else uiStyle
    fun header(): Int = if (headerStyle >= 0) headerStyle else uiStyle

    val flags = mutableStateMapOf<String, Boolean>()
    private val defaults = HashMap<String, Boolean>()
    fun on(k: String): Boolean = flags[k] ?: defaults[k] ?: true
    fun setFlag(k: String, v: Boolean) { flags[k] = v; sp?.edit()?.putBoolean("flag_$k", v)?.apply() }

    class Feature(val key: String, val label: String, val section: String, val def: Boolean = true)

    /** همهٔ بخش‌هایی که از تنظیمات › ویژگی‌ها قابل روشن/خاموش‌اند */
    val features = listOf(
        Feature("notes", "نمایش یادداشت در صفحه تماس", "صفحه تماس"),
        Feature("referrer", "نمایش معرف در صفحه تماس", "صفحه تماس"),
        Feature("groupsShow", "نمایش گروه‌ها در صفحه تماس", "صفحه تماس"),
        Feature("lastCall", "نمایش آخرین تماس قبلی", "صفحه تماس"),
        Feature("callSim", "نمایش نام سیم‌کارت در صفحه تماس", "صفحه تماس"),
        Feature("ctrlMute", "دکمهٔ بی‌صدا", "صفحه تماس"),
        Feature("ctrlKeypad", "دکمهٔ صفحه‌کلید در مکالمه", "صفحه تماس"),
        Feature("ctrlRoute", "دکمهٔ خروجی صدا", "صفحه تماس"),
        Feature("ctrlHold", "دکمهٔ انتظار (Hold)", "صفحه تماس"),
        Feature("ctrlNote", "دکمهٔ یادداشت در مکالمه", "صفحه تماس"),
        Feature("record", "دکمهٔ ضبط مکالمه", "صفحه تماس"),
        Feature("quickReplies", "پیامک‌های آماده هنگام زنگ‌خوردن", "صفحه تماس"),
        Feature("vibConnect", "ویبره هنگام وصل‌شدن مکالمه", "صفحه تماس"),
        Feature("vibEnd", "ویبره هنگام قطع‌شدن مکالمه", "صفحه تماس"),
        Feature("proximity", "خاموش‌شدن صفحه با سنسور مجاورت", "صفحه تماس"),
        Feature("beeps", "بوق تماس دوم (پشت خط)", "صفحه تماس"),
        Feature("callPopup", "نمایش پنجره تماس هنگام استفاده از برنامه‌های دیگر", "صفحه تماس"),

        Feature("favorites", "علاقه‌مندی‌ها در شماره‌گیر", "شماره‌گیر"),
        Feature("dialerSuggest", "پیشنهاد مخاطب هنگام شماره‌گیری", "شماره‌گیر"),
        Feature("dialLetters", "نمایش حروف زیر ارقام (ABC)", "شماره‌گیر"),
        Feature("haptics", "لرزش ملایم با لمس کلیدها", "شماره‌گیر"),
        Feature("keypadSound", "صدای لمس کلیدهای شماره‌گیر", "شماره‌گیر"),
        Feature("dialerPaste", "دکمهٔ جای‌گذاری (Paste) شماره", "شماره‌گیر"),
        Feature("dialerSave", "دکمهٔ ذخیرهٔ شماره در مخاطبین", "شماره‌گیر"),
        Feature("dialerKeyboard", "باز شدن کیبورد گوشی با لمس کادر شماره", "شماره‌گیر", false),

        Feature("showSimInfo", "نمایش سیم‌کارت در تاریخچه", "تاریخچه و مخاطبین"),
        Feature("showDuration", "نمایش مدت مکالمه در تاریخچه", "تاریخچه و مخاطبین"),
        Feature("remindBell", "نشان زنگوله برای شماره‌های دارای یادآوری", "تاریخچه و مخاطبین"),
        Feature("letterHeaders", "سرفصل حرف اول در فهرست مخاطبین", "تاریخچه و مخاطبین"),

        Feature("blockedNumbers", "رد خودکار شماره‌های مسدود", "رد و مجاز کردن تماس‌ها"),
        Feature("allowFavs", "در حالت «فقط گروه‌های انتخابی»، علاقه‌مندی‌ها هم مجاز باشند", "رد و مجاز کردن تماس‌ها", false),

        Feature("headerDate", "نمایش تاریخ زیر عنوان صفحه‌ها", "ظاهر"),
        Feature("dynamicColor", "رنگ‌های پویای سیستم (Material You) در قالب مدرن", "ظاهر")
    )
    var quietOn by mutableStateOf(false)
    var quietStart by mutableIntStateOf(22)
    var quietEnd by mutableIntStateOf(7)
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
        callDark = s.getBoolean("callDark", false)
        callTheme = s.getInt("callTheme", 0); answerStyle = s.getInt("answerStyle", 0); dialerStyle = s.getInt("dialerStyle", 0)
        uiStyle = s.getInt("uiStyle", 0); navStyle = s.getInt("navStyle", -1); headerStyle = s.getInt("headerStyle", -1)
        callLayout = s.getInt("callLayout", 0); darkMode = s.getInt("darkMode", 0)
        features.forEach { defaults[it.key] = it.def; flags[it.key] = s.getBoolean("flag_" + it.key, it.def) }
        groupMode = s.getInt("groupMode", -1)
        if (groupMode < 0) {
            // مهاجرت از نسخهٔ قبل: رد گروه‌ها / فقط گروه مجاز
            groupMode = try {
                val gs = Db.get(c).groups()
                val allowOld = s.getBoolean("flag_allowGroups", true) && gs.any { it.allowOnly }
                val rejectOld = s.getBoolean("flag_rejectGroups", true) && gs.any { it.autoReject }
                if (allowOld) 2 else if (rejectOld) 1 else 0
            } catch (e: Exception) { 0 }
            s.edit().putInt("groupMode", groupMode).apply()
        }
        quietOn = s.getBoolean("quietOn", false); quietStart = s.getInt("quietStart", 22); quietEnd = s.getInt("quietEnd", 7)
        missedNotify = s.getBoolean("missedNotify", true); missedBadge = s.getBoolean("missedBadge", true)
        s.getString("replies", null)?.let { quickReplies = if (it.isEmpty()) emptyList() else it.split("\u001F") }
        missedCount = s.getInt("missedCount", 0); missedLabels = s.getString("missedLabels", "") ?: ""; missedLast = s.getString("missedLast", "") ?: ""
    }

    fun save() {
        sp?.edit()?.putInt("bg", bg)?.putInt("card", card)?.putInt("text", text)?.putInt("accent", accent)
            ?.putFloat("fontScale", fontScale)?.putInt("font", font)
            ?.putBoolean("persian", persian)?.putBoolean("rejectUnknown", rejectUnknown)
            ?.putInt("hTap", hTap)?.putInt("hLong", hLong)?.putInt("hSwL", hSwL)?.putInt("hSwR", hSwR)
            ?.putInt("cTap", cTap)?.putInt("cLong", cLong)?.putInt("cSwL", cSwL)?.putInt("cSwR", cSwR)
            ?.putBoolean("callDark", callDark)?.putInt("callTheme", callTheme)?.putInt("answerStyle", answerStyle)?.putInt("dialerStyle", dialerStyle)?.putBoolean("quietOn", quietOn)?.putInt("quietStart", quietStart)?.putInt("quietEnd", quietEnd)?.putBoolean("missedNotify", missedNotify)?.putBoolean("missedBadge", missedBadge)
            ?.putInt("uiStyle", uiStyle)?.putInt("navStyle", navStyle)?.putInt("headerStyle", headerStyle)
            ?.putInt("callLayout", callLayout)?.putInt("darkMode", darkMode)?.putInt("groupMode", groupMode)?.apply()
    }

    /** قالب‌های آمادهٔ کل برنامه: 0 مدرن، 1 iPhone 17، 2 Galaxy S26 */
    fun applyPreset(p: Int) {
        bg = 0; card = 0; text = 0; accent = 0
        navStyle = -1; headerStyle = -1; callTheme = 0
        when (p) {
            1 -> { uiStyle = 1; callLayout = 1; dialerStyle = 3; answerStyle = 1 }
            2 -> { uiStyle = 2; callLayout = 2; dialerStyle = 4; answerStyle = 4 }
            else -> { uiStyle = 0; callLayout = 0; dialerStyle = 0; answerStyle = 0 }
        }
        save()
    }

    fun saveReplies() { sp?.edit()?.putString("replies", quickReplies.joinToString("\u001F"))?.apply() }

    fun reset() { bg = 0; card = 0; text = 0; accent = 0; fontScale = 1f; font = 0; darkMode = 0; save() }

    fun addMissed(label: String, number: String) {
        missedCount++; missedLast = number
        missedLabels = (missedLabels.split("\n").filter { it.isNotBlank() } + label).takeLast(3).joinToString("\n")
        persistMissed()
    }

    fun clearMissed() { missedCount = 0; missedLabels = ""; missedLast = ""; persistMissed() }

    private fun persistMissed() {
        sp?.edit()?.putInt("missedCount", missedCount)?.putString("missedLabels", missedLabels)?.putString("missedLast", missedLast)?.apply()
    }

    // فونت‌های فارسی/عربیِ همراه برنامه؛ نام‌ها بر اساس خانواده واقعی فونت هستند.
    // این‌ها جایگزین‌های متن‌باز نزدیک به سبک‌های رایج‌اند، نه فایل‌های تجاری Nazanin/Vazir/Yekan.
    val fontNames = listOf("پیش‌فرض سیستم", "وزیری‌مانند · Noto Sans Arabic", "یکان‌مانند · Noto Kufi Arabic", "نازنین‌مانند · Noto Naskh Arabic", "امیری · Amiri")
    fun family(): FontFamily = when (font) {
        1 -> FontFamily(Font(R.font.noto_sans_arabic_regular, FontWeight.Normal), Font(R.font.noto_sans_arabic_bold, FontWeight.Bold))
        2 -> FontFamily(Font(R.font.noto_kufi_arabic_regular, FontWeight.Normal), Font(R.font.noto_kufi_arabic_bold, FontWeight.Bold))
        3 -> FontFamily(Font(R.font.noto_naskh_arabic_regular, FontWeight.Normal), Font(R.font.noto_naskh_arabic_bold, FontWeight.Bold))
        4 -> FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))
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
