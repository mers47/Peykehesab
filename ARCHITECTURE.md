# معماری نهایی PeykHesab v5

## لایه‌ها

`Compose UI → AppViewModel → AppRepository → Room DAO → Bundled SQLite`

قواعد مالی و تقویم در `domain/` UI-free نگهداری می‌شوند تا مستقل تست شوند.

## Data Model

- Neighborhood
- Customer
- Driver
- Order
- MoneyStateHistory
- Settlement
- AuditEvent

سفارش مبلغ، کمیسیون، سهم راننده، وضعیت وجه، وضعیت عملیات و Snapshot کامل زمان/تاریخ شمسی را مستقل ذخیره می‌کند.

## Invariants مالی

- پول با `Long` ریال.
- کمیسیون با basis point و Snapshot داخل سفارش.
- جمع‌ها Exact؛ Overflow ممنوع.
- تسویه نمی‌تواند از قدر مطلق مانده بیشتر باشد.
- جهت تسویه باید با علامت مانده سازگار باشد.
- لغو سفارش دارای وجه DRIVER/OFFICE ممنوع است.
- راننده دارای سفارش باز، وجه حل‌نشده یا مانده غیرصفر قابل بایگانی نیست.

## Consistency

عملیات حساس Room Transaction هستند:
- ایجاد سفارش
- تغییر محل وجه + History + Audit
- تغییر وضعیت سفارش + Audit
- تسویه + Audit
- بایگانی راننده
- Backup snapshot
- Restore کامل

## Offline-first

هسته اپ شبکه نمی‌خواهد و Manifest هیچ Permission شبکه/SMS ندارد. ارسال پیامک از Intent سیستم استفاده می‌کند.

## Backup

Logical snapshot → Validate → JSON payload → PBKDF2-HMAC-SHA256 → AES-256-GCM → verify-readback.

Restore: read → decrypt/authenticate → decode → validate entire snapshot → atomic replacement.

## Universal Android

- minSdk 23
- targetSdk 36
- compileSdk 36
- Core Library Desugaring
- یک APK بدون ABI/density split
- Resizable و بدون Orientation lock
- RTL اجباری در Compose

## Release architecture

`5 audits → Unit/Lint/R8 → Room schema → Debug APK check → Runtime API 23/35/36 → signed universal Release → APK verifier → source manifest/evidence → artifact`

هر شکست قبل از Artifact نهایی Release را متوقف می‌کند.
