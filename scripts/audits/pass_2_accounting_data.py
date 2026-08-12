#!/usr/bin/env python3
"""Audit pass 2: accounting invariants, persistence, transactions, backup cryptography/tests."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
errs=[]
def fail(x):errs.append(x)
def read(rel):
    p=ROOT/rel
    if not p.is_file():fail(f'فایل لازم وجود ندارد: {rel}');return ''
    return p.read_text(encoding='utf-8')
acc=read('app/src/main/java/ir/peykhesab/app/domain/AccountingEngine.kt')
models=read('app/src/main/java/ir/peykhesab/app/domain/Models.kt')
db=read('app/src/main/java/ir/peykhesab/app/data/AppDatabase.kt')
repo=read('app/src/main/java/ir/peykhesab/app/data/AppRepository.kt')
backup=read('app/src/main/java/ir/peykhesab/app/data/BackupService.kt')
kdf=read('app/src/main/java/ir/peykhesab/app/data/BackupKdf.kt')
domain_test=read('app/src/test/java/ir/peykhesab/app/domain/DomainInvariantTest.kt')
int_test=read('app/src/androidTest/java/ir/peykhesab/app/RepositoryIntegrationTest.kt')
kdf_test=read('app/src/test/java/ir/peykhesab/app/data/BackupKdfTest.kt')
for needle in ['DEFAULT_COMMISSION_BPS = 2000','MAX_ORDER_AMOUNT_RIAL','BASIS_POINTS = 10_000L','Math.addExact','sumExact','Long.MIN_VALUE -> throw']:
    if needle not in acc:fail(f'حفاظ حسابداری ناقص: {needle}')
for needle in ['commissionRial: Long','driverShareRial: Long','amountRial: Long','createdJalaliDateKey: Int','createdLocalSecondOfDay: Int']:
    if needle not in models:fail(f'مدل مالی/زمان ناقص: {needle}')
# Money fields must not be Float/Double in production domain/data.
all_fin='\n'.join([acc,models,db,repo])
if re.search(r'(?:amount|commission|share|net)[A-Za-z_]*\s*:\s*(?:Float|Double)\b',all_fin,re.I):fail('فیلد پولی Float/Double پیدا شد')
if '.sumOf' in all_fin:fail('sumOf خام در حسابداری مجاز نیست؛ جمع دقیق لازم است')
for needle in ['@Database(','exportSchema = true','BundledSQLiteDriver','setQueryCoroutineContext(Dispatchers.IO)']:
    if needle not in db:fail(f'پایداری Room ناقص: {needle}')
# Critical DB operations must be transactional.
for fn in ['createBackupSnapshot','replaceFromBackup','saveCustomer','saveDriver','saveNeighborhood','archiveDriver','createOrder','changeMoneyHolder','changeOrderStatus','addSettlement']:
    if not re.search(r'@Transaction\s+(?:open\s+)?suspend\s+fun\s+'+re.escape(fn)+r'\b',db):fail(f'عملیات بحرانی بدون Transaction: {fn}')
for needle in ['currentNet != 0L','safeAbsolute(currentNet)','ACTIVE_ORDER_STATUSES','UNRESOLVED_MONEY_HOLDERS','OrderStatusRules.canTransition']:
    if needle not in db:fail(f'Invariant دیتابیس ناقص: {needle}')
for needle in ['AES/GCM/NoPadding','FILE_VERSION = 2','0x50, 0x59, 0x4B, 0x48, 0x42, 0x41, 0x4B, 0x32','replaceFromBackup','verifiedSnapshot == snapshot','MAX_BACKUP_BYTES','MAX_BACKUP_PAYLOAD_BYTES']:
    if needle not in backup:fail(f'پشتیبان ناقص: {needle}')
for needle in ['HmacSHA256','OUTPUT_BYTES = 32']:
    if needle not in kdf:fail(f'KDF ناقص: {needle}')
if 'SHA1' in backup+kdf:fail('SHA-1 در مسیر بکاپ وجود دارد')
for needle in ['جمع حسابداری روی overflow بی صدا نمی‌چرخد','تاریخ نامعتبر اسفند','گزارش مالی جمع سفارش','safeAbsolute(Long.MIN_VALUE)']:
    if needle not in domain_test:fail(f'تست دامنه لازم حذف شده: {needle}')
for needle in ['realRoomAccountingFlowRemainsConsistent','concurrentSettlementsCannotOverpayDriverBalance','encryptedBackupRestoresAllDataAtomicallyAndRejectsWrongPassword']:
    if needle not in int_test:fail(f'تست یکپارچگی لازم حذف شده: {needle}')
for vector in ['e8440af370a6181f4a74f7c4894c5d6ad213897a0b12d1248bb6a195fab392e9','b56772e66f462adca1f1c3a6cf859f8d6149c492ba2c3d5ddeb70a0894742739']:
    if vector not in kdf_test:fail('بردار مستقل KDF حذف شده')
if errs:
    print('AUDIT_PASS_2_FAILED',file=sys.stderr);[print('- '+e,file=sys.stderr) for e in errs];sys.exit(1)
print('AUDIT_PASS_2_OK money_long=1 exact_sum=1 transactions=1 backup_aes_gcm=1 kdf_sha256=1 integration_tests=1')
