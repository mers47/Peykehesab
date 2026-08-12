#!/usr/bin/env python3
"""Audit pass 4: Persian-only user experience, RTL, Jalali snapshots and adaptive UI."""
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
errs=[]
def fail(x):errs.append(x)
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
theme=(ROOT/'app/src/main/java/ir/peykhesab/app/ui/theme/Theme.kt').read_text(encoding='utf-8')
jalali=(ROOT/'app/src/main/java/ir/peykhesab/app/domain/JalaliDate.kt').read_text(encoding='utf-8')
models=(ROOT/'app/src/main/java/ir/peykhesab/app/domain/Models.kt').read_text(encoding='utf-8')
reports=(ROOT/'app/src/main/java/ir/peykhesab/app/ui/screens/ReportsScreen.kt').read_text(encoding='utf-8')
new_order=(ROOT/'app/src/main/java/ir/peykhesab/app/ui/screens/NewOrderScreen.kt').read_text(encoding='utf-8')
for needle in ['android:supportsRtl="true"','android:resizeableActivity="true"','android:localeConfig="@xml/locales_config"']:
    if needle not in manifest:fail(f'Manifest UX ناقص: {needle}')
if 'android:screenOrientation' in manifest:fail('قفل Orientation')
if 'LocalLayoutDirection provides LayoutDirection.Rtl' not in theme:fail('RTL صریح Compose حذف شده')
locale=(ROOT/'app/src/main/res/xml/locales_config.xml').read_text(encoding='utf-8')
if 'fa-IR' not in locale:fail('fa-IR حذف شده')
for needle in ['createdZoneId','createdOffsetSeconds','createdJalaliDateKey','createdLocalSecondOfDay']:
    if needle not in models:fail(f'Snapshot زمان سفارش ناقص: {needle}')
for needle in ['object DeviceTime','fun now','JalaliDate.fromGregorian','isLeapYear','daysInMonth']:
    if needle not in jalali:fail(f'تقویم ایرانی ناقص: {needle}')
if 'createdJalaliDateKey' not in (ROOT/'app/src/main/java/ir/peykhesab/app/domain/ReportEngine.kt').read_text(encoding='utf-8'):fail('گزارش بر اساس کلید شمسی فیلتر نمی‌شود')
for needle in ['rememberSaveable','selectedCustomerId','selectedDriverId','selectedNeighborhoodId']:
    if needle not in new_order:fail(f'فرم سفارش در Rotation پایدار نیست: {needle}')
# Reject obviously English-only UI literals in Text/Toast/contentDescription-like Kotlin call sites.
persian=re.compile(r'[\u0600-\u06FF]')
ascii_letters=re.compile(r'[A-Za-z]')
for p in (ROOT/'app/src/main/java').rglob('*.kt'):
    s=p.read_text(encoding='utf-8')
    for m in re.finditer(r'(?:(?:Text|AssistChip|FilterChip)\s*\(\s*"([^"]+)"|Toast\.makeText\([^\n]*?"([^"]+)"|contentDescription\s*=\s*"([^"]+)")',s):
        lit=next((g for g in m.groups() if g is not None),'')
        visible=re.sub(r'\$\{[^}]*\}|\$[A-Za-z_]\w*','',lit)
        visible=re.sub(r'\\[nrt\"\\]','',visible)
        if ascii_letters.search(visible) and not persian.search(visible):fail(f'متن قابل مشاهده غیر فارسی: {p.relative_to(ROOT)} -> {lit}')
# Resource app name must be Persian.
strings=ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
app_name=next((x.text or '' for x in strings.findall('string') if x.attrib.get('name')=='app_name'),'')
if not persian.search(app_name):fail('نام اپ فارسی نیست')
if errs:
    print('AUDIT_PASS_4_FAILED',file=sys.stderr);[print('- '+e,file=sys.stderr) for e in errs];sys.exit(1)
print('AUDIT_PASS_4_OK fa_IR=1 rtl=1 jalali_snapshot=1 adaptive=1 rotation_state=1 obvious_english_ui=0')
