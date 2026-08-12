# گزارش پنج ممیزی مستقل — PeykHesab v5

## نتیجه محلی روی سورس نهایی

```text
AUDIT_PASS_1_OK production_kotlin=22 junk=0 fake_markers=0 legacy_patterns=0
AUDIT_PASS_2_OK money_long=1 exact_sum=1 transactions=1 backup_aes_gcm=1 kdf_sha256=1 integration_tests=1
AUDIT_PASS_3_OK lifecycle=1 no_main_thread_db=1 runtime_api_23_35_37=1 release_monkey_anr=1 rotation_large_dark=1
AUDIT_PASS_4_OK fa_IR=1 rtl=1 jalali_snapshot=1 adaptive=1 rotation_state=1 obvious_english_ui=0
AUDIT_PASS_5_OK immutable_actions=1 least_privilege=1 checkout_credentials=0 gradle_cache=0 dynamic_versions=0 source_manifest=1 runtime_dependency=1 signed_release=1 dependabot=1
FIVE_PASS_AUDIT_OK passed=5/5 same_source=1 release_blocking=1
```

## پاس ۱ — پاکیزگی و معماری

موارد بررسی‌شده: فایل موقت/backup/editor، `__pycache__`، Markerهای TODO/FIXME/HACK/MOCK/FAKE/DEMO/STUB/SAMPLE، APIهای قدیمی هدف‌گذاری‌شده، پارامتر تکراری، `!!`، `runBlocking` و `Thread.sleep` در Production.

یافته‌های واقعی و اصلاح‌شده در Hardening نهایی:
- حذف `RepositoryIntegrationTest.kt.tmp`
- حذف فایل‌های cache موقت تولیدشده توسط ابزار تست
- جلوگیری از ورود `.tmp/.bak/.old/.orig/.rej/.swp/.pyc`

## پاس ۲ — حسابداری و داده

موارد بررسی‌شده: مبلغ `Long`، محاسبه exact، سقف امن مبلغ، Transactionهای حساس، جهت تسویه، منع Over-settlement، بکاپ AES-GCM، KDF SHA-256، Restore اتمیک و تست‌های Room.

یافته‌های واقعی و اصلاح‌شده:
- Overflow جمع‌ها به Fail-closed تبدیل شد.
- `Long.MIN_VALUE` دیگر به مقدار جعلی تبدیل نمی‌شود.
- KDF بکاپ به PBKDF2-HMAC-SHA256 ارتقا یافت.
- Backup/Restore کل داده و Audit را اعتبارسنجی می‌کند.

## پاس ۳ — Android Runtime / Crash / ANR

موارد بررسی‌شده: Lifecycle-aware collection، عدم Room روی Main Thread، Desugaring API 23، Runtime matrix، UI/Room instrumentation، Release R8، Launch timeout، Rotation، large-display، dark mode، Monkey و Crash/ANR log scan.

یافته‌های واقعی و اصلاح‌شده:
- `collectAsState()` به `collectAsStateWithLifecycle()` تبدیل شد.
- پردازش گزارش و مانده از Main Thread خارج شد.
- Stateهای فرم‌های عملیاتی در Activity recreation حفظ می‌شوند.

## پاس ۴ — فارسی، RTL، تقویم و UX

موارد بررسی‌شده: `fa-IR`، RTL صریح مستقل از زبان گوشی، عدم قفل Orientation، Jalali snapshot، تاریخ گزارش بر مبنای کلید شمسی، ورودی‌های حفظ‌شونده در Rotation و نبود متن انگلیسی آشکار در UI.

یافته‌های واقعی و اصلاح‌شده:
- `rememberSaveable` برای Stateهای عملیاتی مهم اضافه شد.
- رمز بکاپ عمداً Saveable نیست.
- تقویم شمسی مستقیم و Parse روی اسفند نامعتبر Fail می‌شود.

## پاس ۵ — CI / Release / Supply Chain

موارد بررسی‌شده: سه Workflow، SHA کامل ۴۰ کاراکتری برای Actionهای خارجی، خاموش بودن checkout credentials، خاموش بودن setup-gradle cache، Dependabot، Dependencyهای بدون `+ / latest / SNAPSHOT`، least privilege، Source Manifest، Runtime dependency و Release امضاشده.

یافته‌های واقعی و اصلاح‌شده:
- Actionها از tag متحرک به commit SHA کامل pin شدند.
- Attestation permission از سطح Workflow به `signed-release` محدود شد.
- Source Manifest قبل از Build به Evidence اضافه شد.
- Dependencyهای پویا Release blocker شدند.

## نکته اعتبار

این گزارش پنج «عامل ممیزی مستقل» را ثبت می‌کند؛ این عوامل اسکریپت‌های اجرایی مستقل هستند، نه پنج مدل هوش مصنوعی مجزا. دلیل این طراحی این است که خروجی قابل تکرار، CI-blocking و قابل بررسی باشد.
