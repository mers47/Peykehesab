# پیک‌حساب — نسخه ۱.۳.۰ (v5 Five-Pass Production Candidate)

اپ شخصی، آفلاین و فارسی برای ثبت سفارش و حسابداری پیک موتوری. طراحی پروژه بر مبنای یک APK یونیورسال Android، رابط کاملاً RTL، تاریخ شمسی ثابت در لحظه ثبت و حسابداری مبتنی بر دفتر رویدادهای واقعی است.

## وضعیت این بسته

این سورس در سطح محلی از پنج ممیزی مستقل Release-blocking، ممیزی ایستا، Proof اجرایی هسته مالی/KDF و مقایسه مستقل تقویم شمسی عبور کرده است. پنج ممیزی **ساب‌ایجنت مدل زبانی نیستند**؛ اسکریپت‌های مستقل و قابل‌اجرایی هستند که هرکدام فقط یک کلاس خطا را بررسی می‌کنند و CI در صورت شکست هرکدام متوقف می‌شود.

گواهی Production نهایی Android فقط زمانی معتبر است که GitHub Actions روی همین سورس واقعاً سبز شود؛ چون محیط ساخت این بسته Android SDK/Gradle/Emulator ندارد و هیچ APK صوری در این بسته قرار داده نشده است.

## قابلیت‌های عملیاتی

- مشتری، راننده و محله: ایجاد/ویرایش/بایگانی با حفظ سوابق تاریخی
- ثبت سریع سفارش بدون نیاز به آدرس کامل؛ محله از فهرست ازپیش‌ساخته‌شده
- چند سفارش هم‌زمان برای یک راننده با مبلغ مستقل
- کمیسیون پیش‌فرض ۲۰٪، Snapshot شده در خود سفارش
- وضعیت مستقل وجه هر سفارش: نامشخص، دست راننده، تحویل دفتر، پرداخت‌نشده
- تاریخچه کامل تغییر محل وجه
- دفتر تسویه مستقل راننده/دفتر با جلوگیری از Over-settlement
- گزارش روزانه، ماهانه و تاریخ شمسی تا تاریخ شمسی
- داشبورد فروش، کمیسیون، بدهی/طلب و سفارش‌های باز
- ارسال متن سفارش به برنامه پیامک با Android Intent، بدون Permission حساس SMS
- بکاپ منطقی کامل و رمزگذاری‌شده و Restore اتمیک
- جست‌وجوی نرمال‌شده فارسی و شماره موبایل ایران
- رابط Material 3 فارسی، RTL، روشن/تیره و سازگار با Rotation و صفحه بزرگ

## زمان و تقویم

در لحظه ثبت سفارش از ساعت و منطقه زمانی خود موبایل Snapshot گرفته می‌شود و این موارد ذخیره می‌شوند:

- epoch milliseconds
- شناسه منطقه زمانی موبایل
- offset همان لحظه
- کلید تاریخ شمسی ثابت
- ثانیه محلی روز

به همین دلیل تغییر Time Zone بعدی، تاریخ گزارش سفارش قدیمی را جابه‌جا نمی‌کند.

## حسابداری

تمام مبالغ در لایه داده با `Long` و واحد ریال نگهداری می‌شوند. محاسبات کمیسیون و جمع مانده از عملیات exact استفاده می‌کنند؛ Overflow به‌جای تولید عدد جعلی باعث خطای صریح و توقف عملیات می‌شود.

- پول دست راننده → راننده به اندازه کمیسیون به دفتر بدهکار است.
- پول تحویل دفتر → دفتر به اندازه سهم راننده به راننده بدهکار است.
- تسویه یک رویداد جدا از وضعیت وجه سفارش است.
- سفارش لغوشده اثر مالی صفر دارد و قبل از لغو نباید وجه به‌عنوان دست راننده/دفتر باقی مانده باشد.

## دیتابیس و بکاپ

- Room 3 + Bundled SQLite
- Foreign Key و Transaction برای عملیات حساس
- بدون destructive migration fallback
- Audit Event برای ایجاد/ویرایش/تغییرات مالی
- بکاپ نسخه ۲ با AES-256-GCM و PBKDF2-HMAC-SHA256
- بازیابی کامل در یک Transaction
- بررسی سازگاری شناسه‌ها، روابط، مبالغ، کمیسیون، تاریخ شمسی، وضعیت‌ها و Audit قبل از Restore

## پنج ممیزی مستقل

اجرای محلی:

```bash
python3 scripts/static_audit.py
python3 scripts/five_pass_audit.py
```

پاس‌ها:

1. پاکیزگی/معماری: فایل موقت، Mock/Fake/Demo/TODO، API قدیمی، `!!`، کد بلااستفاده هدف‌گذاری‌شده و الگوهای خطرناک.
2. حسابداری/داده: نوع پول، جمع exact، Transaction، قواعد تسویه، بکاپ/KDF و تست‌های یکپارچگی.
3. Android Runtime: Lifecycle، Main Thread، API 23/35/37، Release R8، Launch، Rotation، Monkey و Crash/ANR gate.
4. UI/فارسی: `fa-IR`، RTL مستقل از زبان گوشی، Jalali snapshot، Rotation state و متن‌های قابل‌مشاهده.
5. CI/Release: SHA pin کامل Actionها، least privilege، Dependency ثابت، Dependabot، Runtime dependency، امضا و Source Manifest.

## GitHub Actions

سه Workflow داخل `.github/workflows` وجود دارد:

- `android-ci.yml` — ممیزی‌ها، Unit Test، Lint، R8 و Build
- `android-runtime-gate.yml` — تست واقعی Debug و Release روی API 23، 35 و 37
- `release-apk.yml` — فقط بعد از Runtime Gate، APK یونیورسال امضاشده و بسته شواهد را تولید می‌کند

برای Release امضاشده چهار Secret لازم است:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Artifact نهایی فقط پس از سبز شدن Gateها با نام `PeykHesab-signed-universal-apk` ساخته می‌شود.

## Source Manifest

`scripts/source_manifest.py` از تمام ورودی‌های واقعی Release SHA-256 می‌سازد. Release Workflow این Manifest را **قبل از Build** ذخیره و همراه Evidence Bundle منتشر می‌کند تا شواهد به همان سورس متصل باشند.

## Build محلی

این بسته Gradle Wrapper جعلی ندارد. Wrapper باید با Gradle رسمی تولید شود. در GitHub Actions نسخه ثابت Gradle 9.5.0 توسط Action رسمی نصب می‌شود. برای Build محلی به JDK 17، Android SDK و Gradle 9.5.0 نیاز است.

## اصل Release

وجود سورس، Static Audit یا ZIP سالم به‌تنهایی مجوز استفاده از عبارت «Production اثبات‌شده» نیست. فقط خروجی GitHub Release Workflow که `PRODUCTION_PROOF.txt`، Runtime Gate و APK verification آن PASS باشند، Release نهایی محسوب می‌شود.
