# Final Local Proof — PeykHesab v5 / 1.3.0

## وضعیت

**Source/Domain Proof: PASS**  
**Android Runtime Certification: PENDING REAL GITHUB ACTIONS RUN**

## شواهد روی همین سورس

- Static audit: PASS
- Five independent audit passes: 5/5 PASS
- Production Kotlin files audited: 22
- Android permissions: 0
- Universal config: API 23..37, no ABI/density splits
- RTL/fa-IR gate: PASS
- Exact-money/transaction/backup gate: PASS
- CI immutable-action/least-privilege/dynamic-version gate: PASS
- Core Kotlin proof: 70 accounting combinations + overflow fail-closed + 3 KDF vectors
- Jalali roundtrip: 47,847 days PASS
- Independent Intl Persian calendar comparison: 47,847 days, 0 mismatch
- Workflow/Dependabot YAML parse: PASS
- APK verifier shell syntax: PASS
- Temporary/cache artifact scan: PASS

## Source identity

`SOURCE_MANIFEST.sha256` contains SHA-256 for 79 release-input files.

Manifest file SHA-256:

`6213892774b0b13352c2e7729c1bfa9a72914eef272f22a5b69acb5f923a5acf`

## مرز اثبات

Android SDK، Gradle executable و Emulator در محیط محلی این ممیزی در دسترس نبودند. بنابراین Compose/Room/KSP/R8/APK runtime در این محیط به‌صورت جعلی PASS اعلام نشده‌اند. سه GitHub Workflow برای اجرای همین بخش‌ها Fail-closed هستند و تنها سبز شدن واقعی آن‌ها، گواهی Runtime Production را کامل می‌کند.
