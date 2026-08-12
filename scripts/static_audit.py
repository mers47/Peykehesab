#!/usr/bin/env python3
"""Fail-fast source audit for PeykHesab CI.

This intentionally checks only invariants that can be proven statically. Runtime,
Room, Compose, R8 and APK behavior are covered by Gradle/emulator jobs.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main"
KOTLIN = list((MAIN / "java").rglob("*.kt"))
XMLS = list((MAIN / "res").rglob("*.xml")) + [MAIN / "AndroidManifest.xml"]

errors: list[str] = []

def fail(msg: str) -> None:
    errors.append(msg)

# 0) Generated/editor/temp artifacts are never allowed in the production repository.
junk_suffixes = {".tmp", ".bak", ".old", ".orig", ".rej", ".swp", ".pyc"}
for path in ROOT.rglob("*"):
    if any(part in {"build", ".gradle", ".git"} for part in path.parts):
        continue
    if path.is_dir() and path.name == "__pycache__":
        fail(f"پوشه موقت Python در سورس: {path.relative_to(ROOT)}")
    if path.is_file() and path.suffix.lower() in junk_suffixes:
        fail(f"فایل موقت/مرده در سورس: {path.relative_to(ROOT)}")

# 1) No unfinished/fake implementation markers in production code.
forbidden_words = re.compile(r"\b(?:TODO|FIXME|HACK|MOCK|FAKE|DEMO|STUB|SAMPLE)\b", re.IGNORECASE)
for path in KOTLIN:
    text = path.read_text(encoding="utf-8")
    for match in forbidden_words.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        fail(f"نشانگر کد موقت/صوری در {path.relative_to(ROOT)}:{line}: {match.group(0)}")

# 1b) Catch duplicate named parameters before Gradle compilation.
def find_matching_paren(text: str, start: int) -> int | None:
    depth = 0
    in_string = False
    escape = False
    for index in range(start, len(text)):
        ch = text[index]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return index
    return None

def split_top_level_params(body: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depths = {"(": 0, "[": 0, "{": 0, "<": 0}
    closing = {")": "(", "]": "[", "}": "{", ">": "<"}
    in_string = False
    escape = False
    for index, ch in enumerate(body):
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
            continue
        if ch in depths:
            depths[ch] += 1
        elif ch in closing and depths[closing[ch]] > 0:
            depths[closing[ch]] -= 1
        elif ch == ',' and not any(depths.values()):
            parts.append(body[start:index])
            start = index + 1
    parts.append(body[start:])
    return parts

callable_re = re.compile(r"\b(?:fun\s+(?:<[^>]+>\s*)?[A-Za-z_][A-Za-z0-9_.]*|(?:data\s+)?class\s+[A-Za-z_][A-Za-z0-9_]*)\s*\(")
for path in KOTLIN:
    text = path.read_text(encoding="utf-8")
    for match in callable_re.finditer(text):
        open_paren = text.find("(", match.start(), match.end())
        close_paren = find_matching_paren(text, open_paren)
        if close_paren is None:
            fail(f"پرانتز پارامترها بسته نشده است: {path.relative_to(ROOT)}")
            continue
        names: list[str] = []
        for raw in split_top_level_params(text[open_paren + 1:close_paren]):
            cleaned = re.sub(r"@[A-Za-z0-9_.]+(?:\([^)]*\))?", "", raw).strip()
            param = re.match(r"(?:val\s+|var\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*:", cleaned)
            if param:
                names.append(param.group(1))
        duplicates = sorted({name for name in names if names.count(name) > 1})
        if duplicates:
            line = text.count("\n", 0, match.start()) + 1
            fail(f"پارامتر تکراری در {path.relative_to(ROOT)}:{line}: {', '.join(duplicates)}")

# 2) APIs/constructs explicitly excluded from this production architecture.
legacy_patterns = {
    r"\bSQLiteOpenHelper\b": "SQLiteOpenHelper",
    r"\bSharedPreferences\b": "SharedPreferences",
    r"\bAsyncTask\b": "AsyncTask",
    r"\bGlobalScope\b": "GlobalScope",
    r"\bLiveData\b": "LiveData",
    r"androidx\.compose\.material\.icons": "material-icons قدیمی",
    r"material-icons-extended": "material-icons-extended",
    r"!!": "non-null assertion (!!)",
    r"\brunBlocking\b": "runBlocking در Production",
    r"Thread\.sleep": "Thread.sleep در Production",
    r"allowMainThreadQueries": "Room main-thread queries",
    r"fallbackToDestructiveMigration": "destructive Room migration",
    r"catch\s*\([^)]*:\s*Throwable": "catch Throwable در Production",
    r"\.collectAsState\(\)": "collectAsState بدون Lifecycle",
    r"PBKDF2WithHmacSHA1": "PBKDF2-HMAC-SHA1 قدیمی",
}
for path in KOTLIN + [ROOT / "app" / "build.gradle.kts", ROOT / "build.gradle.kts"]:
    text = path.read_text(encoding="utf-8")
    for pattern, label in legacy_patterns.items():
        if re.search(pattern, text):
            fail(f"الگوی ممنوع/قدیمی {label} در {path.relative_to(ROOT)}")

# 3) Manifest: zero permissions, RTL/resizable, no forced orientation.
manifest_path = MAIN / "AndroidManifest.xml"
manifest_text = manifest_path.read_text(encoding="utf-8")
if re.search(r"<uses-permission\b", manifest_text):
    fail("AndroidManifest نباید Permission داشته باشد")
if "android:screenOrientation" in manifest_text:
    fail("قفل جهت صفحه برای نسخه یونیورسال مجاز نیست")
if 'android:resizeableActivity="true"' not in manifest_text:
    fail("resizeableActivity=true باید فعال باشد")
if 'android:supportsRtl="true"' not in manifest_text:
    fail("supportsRtl=true باید فعال باشد")
if 'android:localeConfig="@xml/locales_config"' not in manifest_text:
    fail("localeConfig فارسی باید در Manifest فعال باشد")
if 'android:allowBackup="false"' not in manifest_text:
    fail("بکاپ خودکار سیستم باید خاموش و بازیابی فقط از مسیر رمزگذاری‌شده داخلی باشد")

# Compose layout direction must be explicitly RTL even when the phone language is not Persian.
theme_text = (MAIN / "java" / "ir" / "peykhesab" / "app" / "ui" / "theme" / "Theme.kt").read_text(encoding="utf-8")
if "LocalLayoutDirection provides LayoutDirection.Rtl" not in theme_text:
    fail("RTL باید در Compose مستقل از زبان خود گوشی اجبار شود")

# 4) XML must parse.
for path in XMLS:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        fail(f"XML خراب: {path.relative_to(ROOT)}: {exc}")

# 5) Build invariants for one universal APK, API 23..37 and desugaring.
app_gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
root_gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
required_build_snippets = [
    "compileSdk = 37",
    "minSdk = 23",
    "targetSdk = 37",
    "isCoreLibraryDesugaringEnabled = true",
    'coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")',
    'androidTestImplementation("androidx.test:runner:1.7.0")',
    'implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")',
    "isMinifyEnabled = true",
    "isShrinkResources = true",
]
for snippet in required_build_snippets:
    if snippet not in app_gradle:
        fail(f"تنظیم Build لازم وجود ندارد: {snippet}")
for forbidden_split in ("splits {", "abi {", "density {"):
    if forbidden_split in app_gradle:
        fail(f"برای APK یونیورسال Split مجاز نیست: {forbidden_split.strip()}")
for snippet in (
    'id("com.android.application") version "9.3.1"',
    'id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"',
    'id("androidx.room3") version "3.0.1"',
    'classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")',
):
    if snippet not in root_gradle:
        fail(f"Toolchain قفل نشده یا تغییر کرده است: {snippet}")

# 6) Room release keep rule must exist when R8 is enabled.
proguard = (ROOT / "app" / "proguard-rules.pro").read_text(encoding="utf-8")
if "extends androidx.room3.RoomDatabase" not in proguard:
    fail("Keep rule مربوط به Room3 در R8 پیدا نشد")

# 7) Resource references in production Kotlin/XML must resolve for local drawable/mipmap/xml/string refs.
resource_dirs = {
    "drawable": MAIN / "res" / "drawable",
    "mipmap": MAIN / "res" / "mipmap-anydpi-v26",
    "xml": MAIN / "res" / "xml",
}
existing: dict[str, set[str]] = {}
for kind, directory in resource_dirs.items():
    existing[kind] = {p.stem for p in directory.glob("*.xml")} if directory.exists() else set()
existing["string"] = set()
strings = MAIN / "res" / "values" / "strings.xml"
if strings.exists():
    root = ET.parse(strings).getroot()
    existing["string"] = {node.attrib.get("name", "") for node in root.findall("string")}

texts: list[tuple[Path, str]] = []
texts.extend((p, p.read_text(encoding="utf-8")) for p in KOTLIN)
texts.extend((p, p.read_text(encoding="utf-8")) for p in XMLS)
ref_re = re.compile(r"@(?P<kind>drawable|mipmap|xml|string)/(?P<name>[A-Za-z0-9_]+)")
for path, text in texts:
    for m in ref_re.finditer(text):
        kind, name = m.group("kind"), m.group("name")
        if name not in existing.get(kind, set()):
            fail(f"Resource گمشده @{kind}/{name} در {path.relative_to(ROOT)}")

# R.drawable references from Kotlin.
for path in KOTLIN:
    text = path.read_text(encoding="utf-8")
    for name in re.findall(r"R\.drawable\.([A-Za-z0-9_]+)", text):
        if name not in existing["drawable"]:
            fail(f"Drawable گمشده R.drawable.{name} در {path.relative_to(ROOT)}")

# 7b) No dead local drawable resources.
all_text = "\n".join(text for _, text in texts)
referenced_drawables = set(re.findall(r"@drawable/([A-Za-z0-9_]+)", all_text))
for path in KOTLIN:
    referenced_drawables.update(re.findall(r"R\.drawable\.([A-Za-z0-9_]+)", path.read_text(encoding="utf-8")))
unused_drawables = sorted(existing["drawable"] - referenced_drawables)
for name in unused_drawables:
    fail(f"Drawable بلااستفاده در Production: {name}")

# 8) Locale config must declare fa-IR only for user-visible app locale.
locale_path = MAIN / "res" / "xml" / "locales_config.xml"
locale_text = locale_path.read_text(encoding="utf-8")
if 'android:name="fa-IR"' not in locale_text:
    fail("fa-IR در locales_config تعریف نشده است")

# 9) No accidental network/cleartext dependency surface in this offline personal build.
if "android.permission.INTERNET" in manifest_text:
    fail("مجوز INTERNET برای این نسخه آفلاین نباید وجود داشته باشد")
if "usesCleartextTraffic" in manifest_text:
    fail("cleartext traffic نباید فعال شود")

# 10) GitHub release gates must remain wired.
ci = (ROOT / ".github" / "workflows" / "android-ci.yml").read_text(encoding="utf-8")
runtime = (ROOT / ".github" / "workflows" / "android-runtime-gate.yml").read_text(encoding="utf-8")
release = (ROOT / ".github" / "workflows" / "release-apk.yml").read_text(encoding="utf-8")
for needle in (
    "api-level: 37",
    "connectedDebugAndroidTest",
    "assembleRelease",
    "monkey",
    "uiautomator",
    "timeout 30s",
    "wm size 1600x1200",
    "font_scale 2.0",
    "cmd uimode night yes",
    "-memory 4096",
    "PRODUCTION_RUNTIME_GATE=PASS",
):
    if needle not in runtime:
        fail(f"Runtime Gate ناقص شده است: {needle}")

backup_source = MAIN / "java" / "ir" / "peykhesab" / "app" / "data" / "BackupService.kt"
backup_screen = MAIN / "java" / "ir" / "peykhesab" / "app" / "ui" / "screens" / "BackupScreen.kt"
if not backup_source.is_file() or not backup_screen.is_file():
    fail("پشتیبان‌گیری/بازیابی رمزگذاری‌شده حذف شده است")
else:
    backup_text = backup_source.read_text(encoding="utf-8")
    kdf_path = MAIN / "java" / "ir" / "peykhesab" / "app" / "data" / "BackupKdf.kt"
    kdf_text = kdf_path.read_text(encoding="utf-8") if kdf_path.is_file() else ""
    for required in ("AES/GCM/NoPadding", "replaceFromBackup", "verifiedSnapshot == snapshot"):
        if required not in backup_text:
            fail(f"گیت پشتیبان‌گیری ناقص شده است: {required}")
    for required in ("HmacSHA256", "OUTPUT_BYTES = 32"):
        if required not in kdf_text:
            fail(f"KDF بکاپ ناقص شده است: {required}")
    if "PBKDF2WithHmacSHA1" in backup_text or "PBKDF2WithHmacSHA1" in kdf_text:
        fail("KDF قدیمی SHA-1 نباید در نسخه جدید وجود داشته باشد")

for relative in (
    "app/src/androidTest/java/ir/peykhesab/app/ReleaseSmokeTest.kt",
    "app/src/androidTest/java/ir/peykhesab/app/RepositoryIntegrationTest.kt",
    "app/src/test/java/ir/peykhesab/app/domain/DomainInvariantTest.kt",
    "app/src/test/java/ir/peykhesab/app/data/BackupKdfTest.kt",
):
    if not (ROOT / relative).is_file():
        fail(f"تست Release لازم حذف شده است: {relative}")
integration_test_text = (ROOT / "app/src/androidTest/java/ir/peykhesab/app/RepositoryIntegrationTest.kt").read_text(encoding="utf-8")
if "encryptedBackupRestoresAllDataAtomicallyAndRejectsWrongPassword" not in integration_test_text:
    fail("تست واقعی بکاپ/بازیابی رمزگذاری‌شده حذف شده است")
if "concurrentSettlementsCannotOverpayDriverBalance" not in integration_test_text:
    fail("تست هم‌زمانی تسویه حذف شده است")

for workflow_name, workflow_text in (("Android CI", ci), ("Runtime Gate", runtime), ("Release", release)):
    if "python3 scripts/five_pass_audit.py" not in workflow_text:
        fail(f"پنج ممیزی مستقل از {workflow_name} حذف شده است")
if "needs: build-and-static-checks" not in ci or "android-runtime-gate.yml" not in ci:
    fail("Android CI باید Runtime Gate را بعد از Build اجرا کند")
if "ROOM_SCHEMA_OK" not in ci or "find app/schemas" not in ci:
    fail("Android CI باید تولید واقعی Room Schema را کنترل کند")
if "needs: runtime-gate" not in release:
    fail("Release نهایی باید به Runtime Gate وابسته باشد")
for needle in ("PEYKHESAB_PRODUCTION_RELEASE=PASS", "FIVE_PASS_AUDIT=PASS", "PRODUCTION_PROOF.txt", "PeykHesab-production-evidence", "ROOM_SCHEMA_GENERATED=YES", "ENCRYPTED_BACKUP_RUNTIME_TEST=PASS"):
    if needle not in release:
        fail(f"بسته اثبات Production ناقص شده است: {needle}")
if "actions/attest@" not in release or "ENABLE_GITHUB_ATTESTATION" not in release:
    fail("Artifact Attestation اختیاری GitHub از Release حذف شده است")

# External GitHub Actions must be immutable full-length SHAs. Local reusable workflows are exempt.
workflow_files = list((ROOT / ".github" / "workflows").glob("*.yml"))
uses_re = re.compile(r"^\s*uses:\s*([^#\s]+)", re.MULTILINE)
for workflow in workflow_files:
    text = workflow.read_text(encoding="utf-8")
    for ref in uses_re.findall(text):
        if ref.startswith("./"):
            continue
        if "@" not in ref:
            fail(f"Action بدون ref در {workflow.relative_to(ROOT)}: {ref}")
            continue
        _, version = ref.rsplit("@", 1)
        if not re.fullmatch(r"[0-9a-f]{40}", version):
            fail(f"Action باید به SHA کامل ۴۰ کاراکتری pin شود: {workflow.relative_to(ROOT)} -> {ref}")

if not (ROOT / ".github" / "dependabot.yml").is_file():
    fail("Dependabot برای نگهداری Dependencyهای Gradle/GitHub Actions تعریف نشده است")

if errors:
    print("STATIC_AUDIT_FAILED", file=sys.stderr)
    for item in errors:
        print(f"- {item}", file=sys.stderr)
    sys.exit(1)

print(
    "STATIC_AUDIT_OK "
    f"kotlin={len(KOTLIN)} xml={len(XMLS)} "
    "permissions=0 api=23..37 universal=1 rtl=1"
)
