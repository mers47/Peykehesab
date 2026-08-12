#!/usr/bin/env python3
"""Audit pass 5: CI/release fail-closed behavior and GitHub Actions supply-chain hardening."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
errs=[]
def fail(x):errs.append(x)
wfdir=ROOT/'.github/workflows'; wfs=sorted(wfdir.glob('*.yml'))
if len(wfs)!=3:fail(f'تعداد Workflow مورد انتظار ۳ است؛ فعلی: {len(wfs)}')
uses=re.compile(r'^\s*uses:\s*([^#\s]+)',re.M)
for p in wfs:
    s=p.read_text(encoding='utf-8')
    if 'permissions:' not in s or 'contents: read' not in s:fail(f'least-privilege contents read ناقص: {p.name}')
    for ref in uses.findall(s):
        if ref.startswith('./'):continue
        if '@' not in ref or not re.fullmatch(r'[0-9a-f]{40}',ref.rsplit('@',1)[1]):fail(f'Action mutable: {p.name}: {ref}')
    if 'uses: actions/checkout@' in s and 'persist-credentials: false' not in s:fail(f'checkout credential persistence خاموش نیست: {p.name}')
    if 'uses: gradle/actions/setup-gradle@' in s and 'cache-disabled: true' not in s:fail(f'Gradle action cache surface خاموش نیست: {p.name}')
ci=(wfdir/'android-ci.yml').read_text(encoding='utf-8'); rt=(wfdir/'android-runtime-gate.yml').read_text(encoding='utf-8'); rel=(wfdir/'release-apk.yml').read_text(encoding='utf-8')
# Release workflow must keep privileged attestation permissions scoped to the signing job only.
release_prefix = rel.split('jobs:', 1)[0]
if 'id-token: write' in release_prefix or 'attestations: write' in release_prefix:
    fail('مجوز Attestation نباید در سطح کل Release Workflow باشد')
if not re.search(r'(?ms)^  signed-release:.*?^    permissions:\n      contents: read\n      id-token: write\n      attestations: write', rel):
    fail('مجوزهای Attestation باید فقط روی signed-release تعریف شوند')

# No dynamic/unbounded Gradle dependency or plugin versions.
gradle_files = [ROOT/'build.gradle.kts', ROOT/'app/build.gradle.kts', ROOT/'settings.gradle.kts']
dynamic_version = re.compile(r'["\'](?:[^"\']*[:@])(?:\+|latest(?:\.[A-Za-z0-9_-]+)?|[^"\']*SNAPSHOT)["\']', re.IGNORECASE)
for gp in gradle_files:
    if gp.is_file():
        for line_no, line in enumerate(gp.read_text(encoding='utf-8').splitlines(), 1):
            if dynamic_version.search(line):
                fail(f'نسخه Dependency/Plugin پویا در {gp.relative_to(ROOT)}:{line_no}')

if not (ROOT/'scripts/source_manifest.py').is_file():
    fail('source_manifest.py حذف شده است')
for needle in ['python3 scripts/five_pass_audit.py','testDebugUnitTest','lintRelease','analyzeReleaseR8Config','assembleRelease','ROOM_SCHEMA_OK','verify_apk.sh','needs: build-and-static-checks','android-runtime-gate.yml']:
    if needle not in ci:fail(f'CI ناقص: {needle}')
for needle in ['api-level: 37','connectedDebugAndroidTest','PRODUCTION_RUNTIME_GATE=PASS']:
    if needle not in rt:fail(f'Runtime ناقص: {needle}')
for needle in ['needs: runtime-gate','python3 scripts/five_pass_audit.py','python3 scripts/source_manifest.py','ANDROID_KEYSTORE_BASE64','analyzeReleaseR8Config','verify_apk.sh','PeykHesab-signed-universal-apk','PRODUCTION_PROOF.txt','FIVE_PASS_AUDIT=PASS','SOURCE_MANIFEST=YES']:
    if needle not in rel:fail(f'Release fail-closed ناقص: {needle}')
if 'splits {' in (ROOT/'app/build.gradle.kts').read_text(encoding='utf-8'):fail('APK split فعال است؛ یونیورسال نیست')
if not (ROOT/'.github/dependabot.yml').is_file():fail('Dependabot حذف شده')
dep=(ROOT/'.github/dependabot.yml').read_text(encoding='utf-8') if (ROOT/'.github/dependabot.yml').is_file() else ''
for eco in ['github-actions','gradle']:
    if eco not in dep:fail(f'Dependabot ecosystem حذف شده: {eco}')
if errs:
    print('AUDIT_PASS_5_FAILED',file=sys.stderr);[print('- '+e,file=sys.stderr) for e in errs];sys.exit(1)
print('AUDIT_PASS_5_OK immutable_actions=1 least_privilege=1 checkout_credentials=0 gradle_cache=0 dynamic_versions=0 source_manifest=1 runtime_dependency=1 signed_release=1 dependabot=1')
