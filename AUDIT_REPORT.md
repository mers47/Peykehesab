# Audit Report — PeykHesab v5 / 1.3.0

## جمع‌بندی

نسخه v5 روی همان سورسی که برای بسته نهایی استفاده می‌شود، پنج ممیزی مستقل Release-blocking و ممیزی ایستا را پاس کرده است. Markerهای Mock/Fake/Demo/TODO در Production صفر، Permissionها صفر و نسخه Android به‌صورت Universal API 23..36 تنظیم شده است.

## نقص‌های واقعی کشف‌شده در مسیر v3→v5

- Compile blocker ناشی از پارامتر تکراری در settlement
- فایل موقت `.tmp` در androidTest
- `collectAsState` غیر lifecycle-aware
- از دست رفتن state فرم‌ها در Activity recreation
- KDF قدیمی‌تر بکاپ
- Actionهای GitHub با ref متحرک
- Scope بیش از حد مجوز Attestation
- نبود Source Manifest قابل تکرار
- ریسک Overflow در جمع‌های مالی
- `Long.MIN_VALUE` در absolute balance
- stale neighborhood هنگام تعویض مشتری
- normalization search ناسازگار
- dependency/test/tooling بلااستفاده در نسخه‌های قبلی

همه موارد بالا در v5 اصلاح یا به Release Gate تبدیل شده‌اند.

## شواهد محلی

- `STATIC_AUDIT_OK kotlin=22 xml=35 permissions=0 api=23..36 universal=1 rtl=1`
- `FIVE_PASS_AUDIT_OK passed=5/5 same_source=1 release_blocking=1`
- `V5_CORE_PROOF_OK accounting=70 jalali_days=47847 kdf_vectors=3 overflow=blocked`
- `JALALI_INTL_PROOF_OK days=47847 mismatches=0 range=1970-01-01..2100-12-31`

## چیزی که هنوز نباید ادعا شود

در محیط فعلی Android SDK/Gradle/Emulator در دسترس نیست، بنابراین Build کامل Compose/Room/KSP/R8 و Runtime واقعی APK در این محیط اجرا نشده است. این بخش به‌صورت Fail-closed در GitHub Actions تعریف شده و تنها PASS واقعی آن Workflow مجوز عبارت Runtime-certified را می‌دهد.
