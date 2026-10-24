#!/bin/bash
# Kiểm chứng độc lập toàn dự án — chạy từ repo root
set -u
cd "$(dirname "$0")/.."
echo "== 1. Cấu trúc =="
find . -path ./.git -prune -o -path ./server/.venv -prune -o -type f -print | sort
echo; echo "== 2. Dataset hợp lệ =="
python3 -c "
import json
rows=[json.loads(l) for l in open('eval/dataset.jsonl') if l.strip()]
from collections import Counter
print(len(rows),'câu:',dict(Counter(r['type'] for r in rows)))"
echo; echo "== 3. Backend pytest =="
if [ -x server/.venv/bin/pytest ]; then (cd server && .venv/bin/python -m pytest -q); else echo 'CHƯA có venv — backend chưa sẵn sàng'; fi
echo; echo "== 4. API contract: docs vs schemas =="
grep -o '/v1/[a-z-]*\|/health\|/capabilities' docs/API_SPEC.md | sort -u
grep -rho '\"/\(health\|capabilities\|v1/[a-z-]*\)\"' server/app/ 2>/dev/null | sort -u || true
echo; echo "== 5. Android source stats =="
find android -name '*.kt' | wc -l; find android -name '*.kt' -exec cat {} + 2>/dev/null | wc -l
