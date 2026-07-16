# TEMP secret-scan helper — DO NOT COMMIT TO GIT.
# All live secret strings below were REDACTED to placeholders after the
# X-box P0/O1 credential-hygiene cleanup (2026-07-14/16).
# Delete this file from the working tree before any commit.
import os
SECRETS = [
 '<REDACTED-live-prod>',  # MYSQL_ROOT_PASSWORD
 '<REDACTED-live-prod>',  # PRJ_DB_PWD / SPRING_DATASOURCE_PASSWORD
 '<REDACTED-live-prod>',  # CLASS_DB_PWD
 '<REDACTED-live-prod>',  # REDIS_PASSWORD
 '<REDACTED-live-prod>',  # JWT_SECRET
 '<REDACTED-live-prod>',  # DRUID_PASSWORD
 '<REDACTED-live-prod>',  # AI_API_TOKEN
]
ROOT = 'D:/crh123dexiaohao/X-box'
SKIP_DIRS = {'.git','node_modules','target','dist','.idea'}
REDACT_SKIP = {'D:/crh123dexiaohao/X-box/.env.prod'}
hits = {}
for dp, _, fns in os.walk(ROOT):
    if any(s in dp.split(os.sep) for s in SKIP_DIRS):
        continue
    for fn in fns:
        p = os.path.join(dp, fn)
        if p in REDACT_SKIP:
            continue
        try:
            data = open(p, 'rb').read()
        except Exception:
            continue
        try:
            text = data.decode('utf-8')
        except UnicodeDecodeError:
            try:
                text = data.decode('gbk')
            except UnicodeDecodeError:
                text = data.decode('latin-1')
        for s in SECRETS:
            if s in text:
                hits.setdefault(p, []).append(s[:14])
for p in sorted(hits):
    print(p)
    for s in hits[p]:
        print('   -', repr(s))
if not hits:
    print('(no committed files contain live prod secrets)')
