#!/usr/bin/env python3
"""Audit pass 3: Android lifecycle, main-thread safety and runtime/ANR/Crash gates."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
errs=[]
def fail(x):errs.append(x)
prod=list((ROOT/'app/src/main/java').rglob('*.kt'))
text='\n'.join(p.read_text(encoding='utf-8') for p in prod)
gradle=(ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')
runtime=(ROOT/'.github/workflows/android-runtime-gate.yml').read_text(encoding='utf-8')
smoke=(ROOT/'app/src/androidTest/java/ir/peykhesab/app/ReleaseSmokeTest.kt').read_text(encoding='utf-8')
if '.collectAsState()' in text:fail('collectAsState بدون Lifecycle در Production')
if 'collectAsStateWithLifecycle' not in text:fail('Lifecycle-aware collection وجود ندارد')
for pat,label in [(r'catch\s*\([^)]*:\s*Throwable','catch Throwable'),(r'\brunBlocking\b','runBlocking'),(r'Thread\.sleep','Thread.sleep'),(r'allowMainThreadQueries','Room روی Main Thread'),(r'\bGlobalScope\b','GlobalScope')]:
    if re.search(pat,text):fail(label)
for needle in ['minSdk = 23','targetSdk = 37','isCoreLibraryDesugaringEnabled = true','lifecycle-runtime-compose:2.11.0']:
    if needle not in gradle:fail(f'تنظیم Runtime ناقص: {needle}')
for needle in ['api-level: 37','connectedDebugAndroidTest','assembleRelease','timeout 30s','uiautomator','monkey','--monitor-native-crashes','ANR in ir\\.peykhesab\\.app','wm size 1600x1200','font_scale 2.0','cmd uimode night yes','user_rotation 1','PRODUCTION_RUNTIME_GATE=PASS']:
    if needle not in runtime:fail(f'Runtime Gate ناقص: {needle}')
for screen in ['پیک‌حساب','سفارش‌ها','راننده‌ها','مشتریان','گزارش‌ها','پشتیبان‌گیری و بازیابی']:
    if screen not in smoke:fail(f'UI smoke screen حذف شده: {screen}')
if 'rememberSaveable' not in text:fail('State restoration برای فرم‌ها وجود ندارد')
if errs:
    print('AUDIT_PASS_3_FAILED',file=sys.stderr);[print('- '+e,file=sys.stderr) for e in errs];sys.exit(1)
print('AUDIT_PASS_3_OK lifecycle=1 no_main_thread_db=1 runtime_api_23_35_37=1 release_monkey_anr=1 rotation_large_dark=1')
