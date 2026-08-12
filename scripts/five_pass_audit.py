#!/usr/bin/env python3
"""Run five independent production-readiness source gates. Any single failure blocks release."""
from pathlib import Path
import subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
passes=[
 ('۱/۵ پاکیزگی و معماری','pass_1_code_cleanliness.py'),
 ('۲/۵ حسابداری و داده','pass_2_accounting_data.py'),
 ('۳/۵ Android Runtime','pass_3_android_runtime.py'),
 ('۴/۵ فارسی، RTL و تقویم','pass_4_ui_locale.py'),
 ('۵/۵ CI و Release','pass_5_ci_release.py'),
]
outputs=[]
for title,name in passes:
    path=ROOT/'scripts/audits'/name
    result=subprocess.run([sys.executable,str(path)],cwd=ROOT,text=True,capture_output=True)
    text=(result.stdout+result.stderr).strip()
    print(f'=== {title} ===')
    print(text)
    outputs.append((title,result.returncode,text))
failed=[x for x in outputs if x[1]!=0]
if failed:
    print(f'FIVE_PASS_AUDIT_FAILED passed={len(outputs)-len(failed)}/5',file=sys.stderr)
    sys.exit(1)
print('FIVE_PASS_AUDIT_OK passed=5/5 same_source=1 release_blocking=1')
