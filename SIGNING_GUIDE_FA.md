# راهنمای ساخت و امضای نسخه انتشار CallManager

این راهنما برای ساخت APK قابل انتشار با کلید اختصاصی برنامه است.

## روش اول: Android Studio

1. پروژه را در Android Studio باز کنید و منتظر بمانید Gradle Sync کامل شود.
2. از منوی **Build → Generate Signed Bundle / APK** گزینه **APK** را انتخاب کنید.
3. در بخش **Key store path** گزینه **Create new** را بزنید.
4. یک فایل با نامی مانند `release.jks` انتخاب کنید. رمز keystore، نام مستعار کلید (Key alias) و رمز کلید را تعیین کنید.
5. اطلاعات مالک کلید را وارد کنید و تاریخ اعتبار مناسبی انتخاب کنید.
6. مرحله بعد را با نوع ساخت **release** ادامه دهید و در پایان APK امضاشده را دریافت کنید.

## روش دوم: فایل تنظیمات Gradle

1. فایل نمونه `keystore.properties.example` را به `keystore.properties` کپی کنید.
2. چهار مقدار زیر را با اطلاعات واقعی خود جایگزین کنید:
   - `storeFile`: مسیر فایل JKS نسبت به پوشه اصلی پروژه
   - `storePassword`: رمز keystore
   - `keyAlias`: نام مستعار کلید
   - `keyPassword`: رمز کلید انتخاب‌شده
3. فایل `release.jks` را در مسیر معرفی‌شده قرار دهید.
4. در محیطی که JDK 17، Android SDK و Gradle 8.7 نصب است، اجرا کنید:

   ```bash
   gradle assembleRelease
   ```

5. فایل خروجی در مسیر زیر ساخته می‌شود:

   ```text
   app/build/outputs/apk/release/app-release.apk
   ```

اسکریپت `build_signed_apk.sh` نیز برای ساخت کلید محلی در اولین اجرا و ساخت APK انتشار فراهم شده است.

## امضای خودکار با GitHub Actions

در مخزن GitHub به **Settings → Secrets and variables → Actions** بروید و این چهار Secret را بسازید:

- `SIGNING_KEY_BASE64`: محتوای فایل JKS به‌صورت Base64؛ برای نمونه در Linux از `base64 -w 0 release.jks` استفاده کنید.
- `SIGNING_STORE_PASSWORD`: رمز keystore
- `SIGNING_KEY_ALIAS`: نام مستعار کلید
- `SIGNING_KEY_PASSWORD`: رمز کلید

سپس workflow با نام **Build signed APK** را از بخش Actions اجرا کنید. فایل APK در بخش Artifacts همان اجرا قرار می‌گیرد.

## نکات امنیتی بسیار مهم

- فایل `release.jks` و `keystore.properties` را در Git یا مخزن عمومی قرار ندهید؛ این مسیرها در `.gitignore` ثبت شده‌اند.
- از فایل JKS و رمزهای آن نسخه پشتیبان رمزگذاری‌شده و امن تهیه کنید.
- برای انتشار به‌روزرسانی برنامه با شناسه بسته یکسان (`ir.callmanager`)، باید همان کلید امضای نسخه‌های قبلی استفاده شود. با کلید جدید، اندروید معمولاً به‌روزرسانی روی نسخه نصب‌شده را نمی‌پذیرد.
- اگر کلید امضا یا رمزهای آن گم شوند، امکان انتشار به‌روزرسانی سازگار برای نصب‌های قبلی ممکن است از بین برود.
- فایل APK را پس از ساخت روی چند دستگاه واقعی نصب و آزمایش کنید.
