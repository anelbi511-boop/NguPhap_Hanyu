# AGENTS.md — Chinese Grammar AI Hybrid

Hướng dẫn cho agent tiếp quản (Codex / Muse / bất kỳ AI coding agent nào) khi clone repo này về chạy tiếp.

## Dự án là gì
App học & phân tích ngữ pháp tiếng Trung: Android offline (Qwen3 GGUF + llama.cpp) + Windows server (FastAPI + Qwen3 8B qua Ollama + HanLP + LTP) + Hybrid pairing bảo mật. Đặc tả đầy đủ: `docs/ARCHITECTURE.md`, `docs/API_SPEC.md`, `docs/THREAT_MODEL.md`.

## Cấu trúc
- `android/` — Kotlin + Jetpack Compose (MVVM, Room, Hilt, Ktor). Package `com.gia.chinesegrammar`.
- `server/` — FastAPI (Python). Entry `server/app/main.py`, chạy mặc định `127.0.0.1:8730`.
- `docs/` — kiến trúc, API spec, threat model, hướng dẫn cài, licenses.
- `eval/dataset.jsonl` — 48 câu có nhãn (valid/incorrect/ambiguous/hsk) để đánh giá grammar analysis.
- `scripts/verify_all.sh` — kiểm chứng độc lập: cấu trúc, dataset, pytest backend, khớp contract docs↔code, thống kê Android source.

## Cách chạy & test
```bash
# Backend (Linux/Windows đều được)
cd server && python -m venv .venv && .venv/bin/pip install -r requirements.txt pytest httpx
.venv/bin/python -m pytest -q

# Kiểm chứng tổng
bash scripts/verify_all.sh

# Android (cần JDK 17 + Android SDK)
cd android && ./gradlew test assembleDebug
# Hoặc dùng GitHub Actions: .github/workflows/android.yml
```

## Quy tắc không được phá
1. **Không bịa kết quả parser/model.** Engine nào không có mặt thì `/capabilities` phải báo không có; fallback rule-based phải gắn nhãn `source="rule_fallback"` trong dependencies và provenance.
2. **API contract là bất biến** theo `docs/API_SPEC.md` (schema_version, tokens có start/end theo ký tự, provenance, uncertainty). Đổi contract thì phải sửa cả docs + Kotlin models + contract tests cùng lúc.
3. **Offset:** Python tính theo code point, Kotlin theo UTF-16 — khi map offset qua API phải convert (xem mục Unicode trong ARCHITECTURE.md).
4. **Bảo mật:** Ollama chỉ bind loopback; chỉ gateway FastAPI có auth mới được lộ ra LAN; token thiết bị lưu hashed (SQLite), hỗ trợ revoke; không log nguyên câu người dùng ở INFO; không exec bất kỳ output nào của model.
5. **AUTO mode** trên Android bắt buộc có consent rõ ràng của người dùng mới được gửi câu lên PC; mặc định tắt.
6. Không claim test pass khi chưa chạy thật. Phần chưa verify được (thiết bị thật, HanLP/LTP live, Qwen3 8B live) phải ghi rõ "chưa verify".

## Trạng thái bàn giao (23/10/2026)
- Docs + eval dataset: xong, đã kiểm chứng.
- Backend: test được trên Linux bằng pytest (HanLP/LTP/Ollama là optional, vắng thì fallback).
- Android: source đầy đủ; build APK qua CI hoặc máy có SDK. VM của Muse không có Java/Android SDK, không có thiết bị ADB.
- Việc còn lại khi có máy Windows + điện thoại: cài server theo `docs/INSTALL_GUIDE.md`, `ollama pull qwen3:8b`, cài optional `server/requirements-advanced.txt` (HanLP/LTP), build APK, pairing thật, test offline GGUF trên máy thật.
