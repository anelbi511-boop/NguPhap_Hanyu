# Chinese Grammar AI — Hybrid (Android + Windows)

Ứng dụng học và phân tích ngữ pháp tiếng Trung (Mandarin) dùng cá nhân:

- **Android Offline** — Qwen3 0.6B GGUF chạy trên máy qua llama.cpp + từ điển local + rule-based grammar. Không cần mạng.
- **Windows Advanced** — FastAPI server chạy Qwen3 8B (Ollama) + HanLP 2.1 + LTP, giải thích chi tiết bằng tiếng Việt, cây phụ thuộc, đối chiếu 2 parser.
- **Hybrid** — tự chọn Offline/Advanced/Auto. Auto chỉ gửi câu lên PC của chính người dùng sau khi có đồng ý tường minh một lần; mất PC thì tự rơi về Offline. **Không gửi dữ liệu tới bất kỳ nhà cung cấp AI bên ngoài nào.**

## Tài liệu

| File | Nội dung |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Kiến trúc, NLP pipeline, quy tắc Unicode offset, bảng chuẩn hoá nhãn HanLP/LTP, luồng consent |
| [docs/API_SPEC.md](docs/API_SPEC.md) | Đặc tả JSON API v1: `/health`, `/capabilities`, `/v1/analyze`, `/v1/pair`, `/v1/revoke-device` |
| [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) | Threat model STRIDE, biện pháp bảo mật, rủi ro còn tồn tại |
| [docs/INSTALL_GUIDE.md](docs/INSTALL_GUIDE.md) | Hướng dẫn cài Windows server + Android APK + pairing + xử lý sự cố |
| [docs/LICENSES.md](docs/LICENSES.md) | Kiểm kê license model/thư viện/dữ liệu |
| [eval/dataset.jsonl](eval/dataset.jsonl) | Bộ dữ liệu đánh giá có nhãn (câu đúng / sai / mơ hồ / mẫu HSK) |

## Trạng thái kiểm chứng — nói thẳng

> Cập nhật 23/10/2026. Không mục nào dưới đây được coi là "đã test" nếu chưa có bằng chứng chạy thật.

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Tài liệu kiến trúc / API / threat model / cài đặt / license | ✅ Đã viết | Trong `docs/` |
| Bộ dữ liệu đánh giá `eval/dataset.jsonl` | ✅ Đã tạo | 48 câu có nhãn, kiểm tra JSON hợp lệ bằng script |
| Windows backend (FastAPI) | 🟡 Test được trên Linux | Server viết bằng Python/FastAPI — logic API, schema, pipeline chuẩn hoá test bằng `pytest` trên Linux. HanLP/LTP/Qwen3 8B **chưa** chạy live trong môi trường phát triển này; test live để trên máy Windows của người dùng |
| Android build | 🔴 Cần Android SDK | Môi trường phát triển hiện tại (VM Linux) **không có Java/Android SDK/Gradle Android toolchain** nên chưa build được APK tại đây |
| Test trên thiết bị thật | 🔴 Cần thiết bị ADB | **Không có điện thoại Android kết nối ADB** trong môi trường này — chưa có bất kỳ kết quả test vật lý nào, không khẳng định ngược lại |
| Pairing Android ↔ Windows live | 🔴 Chưa test | Phụ thuộc hai mục trên |
| Tailscale | ⚪ Ngoài phạm vi đợt này | Theo quyết định của chủ dự án (23/10/2026): bỏ qua Tailscale trong dự án này; kết nối mặc định là LAN cùng mạng |

## Quickstart — Windows/Linux server

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
ollama pull qwen3:8b             # cần Ollama đang chạy, chỉ bind 127.0.0.1
python -m app.main               # mặc định bind 127.0.0.1:8730; LAN: đặt HOST=0.0.0.0
curl http://127.0.0.1:8730/health
```

Chạy test backend:

```bash
cd server
pytest
```

## Cấu trúc repo

```
ChineseGrammarAI/
├── README.md
├── android/          # App Kotlin / Jetpack Compose (cần Android SDK để build)
├── server/           # FastAPI backend
│   ├── app/          # API, orchestration, HanLP/LTP adapters, Ollama client
│   └── tests/        # pytest: API contract, pipeline, bảo mật đầu vào
├── docs/             # Tài liệu kiến trúc, API, bảo mật, cài đặt, license
├── eval/             # Bộ dữ liệu đánh giá ngữ pháp có nhãn (JSONL)
└── scripts/          # Script chạy server / tiện ích phát hành
```

## Nguyên tắc của dự án

- Kết quả parser (HanLP/LTP) và kết quả model sinh ra luôn được phân biệt bằng `provenance` + `uncertainty` trong mọi response.
- Không bịa quan hệ cú pháp khi offline không có parser thật.
- Không tự động tải model nhiều GB — người dùng luôn chủ động bấm tải và thấy dung lượng trước.
- Không log nội dung câu của người dùng; token pairing lưu bằng Android Keystore, thu hồi được.
