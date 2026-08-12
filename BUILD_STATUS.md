# وضعیت Build — PeykHesab v5

## PASS در محیط فعلی

- `STATIC_AUDIT_OK`
- `FIVE_PASS_AUDIT_OK 5/5`
- Source Manifest generation
- Python audit syntax
- YAML parsing برای هر ۳ Workflow و Dependabot
- shell syntax برای APK verifier
- Pure Kotlin compile/run برای Accounting + Jalali + BackupKdf
- ۷۰ حالت مبلغ/کمیسیون
- Overflow fail-closed
- ۳ KDF known vector
- ۴۷٬۸۴۷ روز Jalali roundtrip از ۱۹۷۰ تا پایان ۲۱۰۰
- ۴۷٬۸۴۷ روز مقایسه مستقل با Intl Persian Calendar، اختلاف صفر

## Build کامل Android در این محیط

اجرا نشده، چون این محیط در زمان ممیزی موارد زیر را ندارد:

- Gradle executable
- Android SDK / `sdkmanager`
- `adb`
- `ANDROID_HOME`
- دسترسی DNS برای دریافت ابزار رسمی

بنابراین در این بسته هیچ ادعای ساخت APK محلی یا Runtime واقعی جعل نشده است.

## گواهی Android نهایی

GitHub Actions برای همین منظور Release-blocking است. تا زمانی که `android-ci`, `android-runtime-gate` و `release-apk` روی Repository واقعی سبز نشوند، وضعیت این بسته **Production Candidate با Source Proof** است؛ نه Production Runtime-certified.

پس از PASS، Release Workflow باید موارد زیر را تولید کند:

- APK یونیورسال امضاشده
- SHA-256 APK
- `PRODUCTION_PROOF.txt`
- `SOURCE_MANIFEST.sha256`
- پنج ممیزی
- Lint report
- Unit test results
- R8 mapping
- Room schema
- Evidence zip
