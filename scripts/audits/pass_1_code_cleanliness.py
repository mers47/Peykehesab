#!/usr/bin/env python3
"""Audit pass 1: code cleanliness, unfinished/fake/dead/legacy constructs."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
MAIN=ROOT/'app/src/main/java'
files=sorted(MAIN.rglob('*.kt'))
errs=[]
def fail(x): errs.append(x)
if not files: fail('هیچ سورس Kotlin تولیدی پیدا نشد')
for p in ROOT.rglob('*'):
    if any(x in {'.git','.gradle','build'} for x in p.parts): continue
    if p.is_dir() and p.name=='__pycache__': fail(f'پوشه موقت: {p.relative_to(ROOT)}')
    if p.is_file() and p.suffix.lower() in {'.tmp','.bak','.old','.orig','.rej','.swp','.pyc'}: fail(f'فایل موقت/مرده: {p.relative_to(ROOT)}')
for p in files:
    if p.stat().st_size==0: fail(f'فایل سورس خالی: {p.relative_to(ROOT)}')
    s=p.read_text(encoding='utf-8')
    for m in re.finditer(r'\b(?:TODO|FIXME|HACK|MOCK|FAKE|DEMO|STUB|SAMPLE)\b',s,re.I):
        fail(f'نشانگر صوری {m.group(0)} در {p.relative_to(ROOT)}:{s.count(chr(10),0,m.start())+1}')
    patterns={
      r'\bSQLiteOpenHelper\b':'SQLiteOpenHelper',r'\bSharedPreferences\b':'SharedPreferences',r'\bAsyncTask\b':'AsyncTask',
      r'\bGlobalScope\b':'GlobalScope',r'\bLiveData\b':'LiveData',r'androidx\.compose\.material\.icons':'Material Icons قدیمی',
      r'material-icons-extended':'material-icons-extended',r'!!':'!!',r'\brunBlocking\b':'runBlocking در Production',
      r'Thread\.sleep':'Thread.sleep',r'allowMainThreadQueries':'Room main-thread',r'fallbackToDestructiveMigration':'destructive migration',
      r'catch\s*\([^)]*:\s*Throwable':'catch Throwable',r'\.collectAsState\(\)':'collectAsState بدون lifecycle',r'PBKDF2WithHmacSHA1':'SHA1 KDF'
    }
    for pat,name in patterns.items():
        if re.search(pat,s): fail(f'الگوی ممنوع {name}: {p.relative_to(ROOT)}')
# Duplicate parameters, using conservative callable scan.
def close_paren(s,start):
    d=0; quote=False; esc=False
    for i in range(start,len(s)):
        c=s[i]
        if quote:
            if esc: esc=False
            elif c=='\\': esc=True
            elif c=='"': quote=False
            continue
        if c=='"': quote=True
        elif c=='(': d+=1
        elif c==')':
            d-=1
            if d==0:return i
    return None
def split_params(body):
    out=[]; start=0; d={'(':0,'[':0,'{':0,'<':0}; pair={')':'(',']':'[','}':'{','>':'<'}; q=False; esc=False
    for i,c in enumerate(body):
        if q:
            if esc:esc=False
            elif c=='\\':esc=True
            elif c=='"':q=False
            continue
        if c=='"':q=True;continue
        if c in d:d[c]+=1
        elif c in pair and d[pair[c]]:d[pair[c]]-=1
        elif c==',' and not any(d.values()):out.append(body[start:i]);start=i+1
    out.append(body[start:]);return out
call=re.compile(r'\b(?:fun\s+(?:<[^>]+>\s*)?[A-Za-z_][\w.]*|(?:data\s+)?class\s+[A-Za-z_]\w*)\s*\(')
for p in files:
    s=p.read_text(encoding='utf-8')
    for m in call.finditer(s):
        op=s.find('(',m.start(),m.end()); cp=close_paren(s,op)
        if cp is None: fail(f'پرانتز پارامتر باز: {p.relative_to(ROOT)}'); continue
        names=[]
        for raw in split_params(s[op+1:cp]):
            raw=re.sub(r'@[\w.]+(?:\([^)]*\))?','',raw).strip()
            pm=re.match(r'(?:val\s+|var\s+)?([A-Za-z_]\w*)\s*:',raw)
            if pm:names.append(pm.group(1))
        dup=sorted({n for n in names if names.count(n)>1})
        if dup:fail(f'پارامتر تکراری {dup}: {p.relative_to(ROOT)}')
if errs:
    print('AUDIT_PASS_1_FAILED',file=sys.stderr); [print('- '+e,file=sys.stderr) for e in errs]; sys.exit(1)
print(f'AUDIT_PASS_1_OK production_kotlin={len(files)} junk=0 fake_markers=0 legacy_patterns=0')
