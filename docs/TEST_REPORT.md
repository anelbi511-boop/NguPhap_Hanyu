# TEST REPORT — 23/10/2026 (kiểm chứng độc lập bởi Muse, không chép báo cáo của agent con)

## Đã chạy thật trên VM Linux của Muse
- Backend pytest: **41 passed, 1 warning** (warning = deprecation starlette/anyio, không liên quan). Gồm: normalize offset (5), fallback parser (12), alignment (5), API + auth (11), contract docs↔code (8).
- Live smoke test: uvicorn thật trên 127.0.0.1:8730 — /health ok, /capabilities trung thực (chỉ rule_fallback), pair → token → /v1/analyze đúng, **401 khi thiếu token**.
- Eval trên dataset 48 câu (eval/dataset.jsonl) qua /v1/analyze: **48/48 trả 200**, nhận diện patterns ở 26/48 câu.
- Sửa lỗi mẫu đúng: 他认真的学习汉语。→ 他认真地学习汉语。(kèm giải thích tiếng Việt).

## Giới hạn đã đo được (không giấu)
- Corrections ở chế độ fallback chỉ bắt được **1/18** câu incorrect — lớp lỗi 的/地/得. Các lỗi trật tự từ, 把/被, lượng từ cần HanLP/LTP/Qwen3 8B thật trên Windows mới xử lý được. Fallback ghi rõ uncertainty 0.65 và warnings nói thẳng đây là phân tích theo luật + dịch từng từ.
- /capabilities khi không có engine nâng cao: hanlp=false, ltp=false, ollama=false, model=null. Không có output nào bị bịa.

## Chưa verify được ở môi trường này
- Android: source đầy đủ + models đã khớp contract server + có unit test contract (chạy ở CI). **Chưa compile** — VM không có Java/Android SDK; GitHub Actions (.github/workflows/android.yml) là nơi build thật đầu tiên.
- HanLP / LTP / Qwen3 8B live trên Windows: chưa test (không cài trên VM này). Adapter lazy-load, lỗi thì degrade về fallback kèm warning.
- Thiết bị Android thật / ADB / benchmark on-device: chưa có thiết bị.
- llama.cpp GGUF trên Android: mới có interface + ModelManager (verify SHA-256 thật); chưa bundle .so, chưa nạp model thật.

## Lỗi đã phát hiện & sửa trong quá trình
1. Docs API_SPEC lệch implementation ở 12 điểm (giới hạn, auth, shape của uncertainty/patterns/warnings, mã lỗi, pairing) → đã viết lại docs theo code canonical + thêm contract test chống tái phát.
2. Kotlin ApiModels lệch server (patterns List<String> vs object; capabilities/pair/revoke shapes) → đã căn chỉnh lại toàn bộ DTO + thêm ApiModelsContractTest dùng payload mẫu từ API_SPEC.
3. Bug proxy env của VM làm httpx crash khi tạo Ollama client → client đặt trust_env=False (đúng bản chất service local).
