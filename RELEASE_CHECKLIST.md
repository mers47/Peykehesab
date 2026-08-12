# چک‌لیست Release — Fail Closed

- [ ] `python3 scripts/static_audit.py` → PASS
- [ ] `python3 scripts/five_pass_audit.py` → 5/5 PASS
- [ ] Source Manifest تولید شده
- [ ] Unit tests PASS
- [ ] Lint Release PASS
- [ ] R8 config analysis PASS
- [ ] Room schema تولید شده
- [ ] Debug instrumentation PASS
- [ ] Room accounting integration PASS
- [ ] Encrypted backup/restore integration PASS
- [ ] Activity recreation draft test PASS
- [ ] Release APK ساخته و امضا شده
- [ ] Runtime API 23 PASS
- [ ] Runtime API 35 PASS
- [ ] Runtime API 36 PASS
- [ ] Rotation PASS
- [ ] Large screen/font 200%/dark mode PASS
- [ ] Monkey 1000 events PASS
- [ ] Logcat Crash/ANR صفر
- [ ] APK verifier PASS
- [ ] دقیقاً یک APK یونیورسال
- [ ] SHA-256 ساخته شده
- [ ] `PRODUCTION_PROOF.txt` ساخته شده
- [ ] Evidence zip ساخته شده

اگر حتی یک مورد Fail باشد، خروجی نباید Production اعلام یا منتشر شود.
