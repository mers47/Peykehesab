# GitHub Actions — راهنمای Release نهایی

## ۱. Repository

کل محتوای پوشه `PeykHesab` شامل پوشه مخفی `.github` را در Root Repository قرار دهید.

## ۲. Secrets امضا

در Repository Settings → Secrets and variables → Actions این چهار Secret را تعریف کنید:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keystore باید متعلق به خود شما باشد. فایل keystore داخل Repository قرار نگیرد.

## ۳. Workflowها

### Android CI

روی Push/PR:
- Static audit
- Five-pass audit
- Unit tests
- Lint Release
- R8 config analysis
- Debug/Release build
- Room schema verification
- APK verification
- Runtime Gate dependency

### Android Runtime Gate

روی API 23، 35 و 37:
- `connectedDebugAndroidTest`
- ساخت Release مینیمایز شده با کلید موقت CI
- نصب و Launch Release
- UI dump و Screenshot
- Rotation/recreation
- API 37: صفحه بزرگ + فونت ۲۰۰٪ + Dark Mode
- Monkey 1000 events
- Crash/ANR log scan

### Build Signed Universal APK

فقط بعد از Runtime Gate:
- پنج ممیزی
- Source Manifest قبل از Build
- Secret validation
- Unit/Lint/R8/Release
- Room schema
- APK signature/universal verification
- SHA-256
- Evidence bundle
- Artifact نهایی

## ۴. Artifact

نام Artifact نهایی:

`PeykHesab-signed-universal-apk`

فایل `PRODUCTION_PROOF.txt` باید `PEYKHESAB_PRODUCTION_RELEASE=PASS` و `FIVE_PASS_AUDIT=PASS` داشته باشد.

## ۵. Attestation

در صورت پشتیبانی پلن/Repository، Variable زیر را `true` کنید:

`ENABLE_GITHUB_ATTESTATION`

در غیر این صورت Build و Release متوقف نمی‌شود؛ SHA-256 و Evidence Bundle همچنان تولید می‌شوند.

## ۶. Gradle Wrapper

Wrapper جعلی یا jar دستی داخل بسته قرار داده نشده است. Workflow نسخه دقیق Gradle 9.5.0 را نصب می‌کند. اگر Build محلی می‌خواهید، با Gradle رسمی دستور Wrapper را تولید و سپس Gateها را دوباره اجرا کنید.
