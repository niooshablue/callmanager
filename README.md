# مدیریت تماس (نسخه ۰.۳)
Kotlin + Jetpack Compose (Material 3, رنگ پویا) — حداقل اندروید ۱۲ (API 31)

## اجرا
1. پوشه را در Android Studio (Koala یا جدیدتر) باز کنید و Sync بزنید.
2. Run روی گوشی/شبیه‌ساز اندروید ۱۲+.
3. دسترسی‌ها را بدهید، سپس دو دکمهٔ «فعال‌سازی» (شماره‌گیر پیش‌فرض + فیلتر تماس) را تأیید کنید.

## APK ساین‌شده
Build > Generate Signed App Bundle/APK در Android Studio، یا `./build_signed_apk.sh`
(خروجی: app/build/outputs/apk/release/app-release.apk)

## امکانات
تاریخچه تماس + شماره‌گیر + مخاطبین، تاریخ شمسی، یادداشت هر شماره (نمایش در صفحه تماس ورودی/خروجی)،
گروه‌بندی مخاطبین، رد خودکار تماس برای گروه یا شماره‌های مشخص.

## ساخت APK بدون Android Studio (GitHub Actions)
یک مخزن خصوصی در github.com بسازید، محتوای این پوشه را در آن آپلود کنید (شامل پوشهٔ .github)،
سپس Actions > Build signed APK > Run workflow. پس از ~۵ دقیقه فایل APK ساین‌شده در بخش Artifacts دانلود می‌شود.
