# API Specification — Chinese Grammar AI Windows Server

> Tài liệu này mô tả **đúng contract đã triển khai** trong `server/app/schemas.py` và `server/app/main.py` (và được kiểm tra tự động bởi `server/tests/test_contract.py`). Nếu sửa contract, phải sửa code + docs + test cùng lúc.

- **Base URL**: `http://<pc-host>:8730` (mặc định server bind `127.0.0.1:8730`; khi cần cho điện thoại trong LAN kết nối, đặt env `HOST=0.0.0.0`, hoặc đi qua Tailscale). Server tự nó **không** terminate TLS — chỉ dùng trên LAN tin cậy hoặc qua Tailscale (WireGuard).
- **Schema version**: `"1.0"` — trả trong field `schema_version` của các response. **Request không gửi `schema_version`.**
- **Content-Type**: `application/json; charset=utf-8`
- **Auth**:
  - Không cần auth: `GET /health`, `GET /capabilities`.
  - `POST /v1/pair`: không cần Bearer token — bảo vệ bằng **pairing code**.
  - Bearer bắt buộc (khi `REQUIRE_AUTH=1`, là mặc định): `POST /v1/analyze`, `POST /v1/revoke-device` — header `Authorization: Bearer <device_token>`. Đặt `REQUIRE_AUTH=0` thì hai endpoint này không cần token.
- **Offset convention**: `tokens[].start` / `tokens[].end` là **offset ký tự trên chuỗi gốc người dùng gửi** (phía server tính theo Unicode code point), `start` inclusive, `end` exclusive. Xem `ARCHITECTURE.md` mục 4 về chuyển đổi UTF-16 phía Kotlin.
- **Giới hạn**:
  - Request body tối đa **32768 bytes (32 KB)** — vượt → `413`.
  - `text` trong `/v1/analyze`: **1–2000 ký tự** — vượt/rỗng → `422`.
  - Rate limit mặc định **120 request/phút/IP** (`/health` được miễn) — vượt → `429`.

## Định dạng lỗi chung

Mọi lỗi đều có dạng:

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed: body.text: String should have at most 2000 characters"
  }
}
```

`code` là slug chữ thường **suy ra từ HTTP status** (không phải mã ngữ nghĩa riêng theo từng lỗi):

| HTTP | code | Khi nào xảy ra |
|---|---|---|
| 400 | `bad_request` | Header `Content-Length` không hợp lệ |
| 401 | `unauthorized` | Thiếu Bearer token, token sai, hoặc token đã bị revoke |
| 403 | `forbidden` | Pairing code sai khi gọi `/v1/pair` |
| 404 | `not_found` | Sai đường dẫn |
| 413 | `payload_too_large` | Body vượt 32768 bytes |
| 422 | `validation_error` | Body sai schema (thiếu trường, `text` rỗng hoặc quá 2000 ký tự, `hsk_level` ngoài 1–6, …) |
| 429 | `rate_limited` | Vượt 120 request/phút/IP |
| 500 | `internal_error` | Lỗi không lường trước (chi tiết chỉ ở server log đã redact) |

---

## GET /health

Không cần auth. Dùng cho Android kiểm tra PC reachable trước khi gửi câu.

**Response 200:**

```json
{
  "status": "ok",
  "schema_version": "1.0"
}
```

## GET /capabilities

Không cần auth. Cho Android biết PC hiện có những engine nào để bật/tắt tính năng trên UI. Server báo **trung thực**: engine chỉ được ghi là có khi nó thật sự đã cài / đang reachable ngay lúc gọi.

**Response 200** (ví dụ khi chỉ có rule fallback, chưa cài HanLP/LTP và Ollama chưa chạy):

```json
{
  "schema_version": "1.0",
  "engine": "rule_fallback",
  "engines": {
    "rule_fallback": true,
    "hanlp": false,
    "ltp": false,
    "ollama": false
  },
  "parsers_available": [],
  "ollama": {
    "available": false,
    "model": "qwen3:8b",
    "host": "http://127.0.0.1:11434"
  },
  "auth_required": true,
  "max_text_chars": 2000,
  "max_body_bytes": 32768
}
```

- `engine`: nối tên các parser đang có (ví dụ `"hanlp+ltp"`), hoặc `"rule_fallback"` khi không có parser nào.
- `parsers_available`: danh sách con của `["hanlp", "ltp"]`.

## POST /v1/analyze

Phân tích một câu tiếng Trung. **Yêu cầu Bearer token** (khi `REQUIRE_AUTH=1`).

**Request:**

```json
{
  "text": "我把作业做完了。",
  "hsk_level": 3,
  "mode": "auto"
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `text` | string | có | Câu tiếng Trung, 1–2000 ký tự |
| `hsk_level` | int 1–6 \| null | không | Mức HSK mục tiêu để điều chỉnh độ khó giải thích; mặc định `null` |
| `mode` | `"offline"` \| `"advanced"` \| `"auto"` \| null | không | Khai báo chế độ phía client. Server chấp nhận để tương thích contract nhưng hiện **không** đổi hành vi phân tích theo `mode` (việc chọn mode/fallback do client quyết định) |

**Response 200:**

```json
{
  "schema_version": "1.0",
  "tokens": [
    {"text": "我", "start": 0, "end": 1, "pos": "PN", "pinyin": "wǒ", "role": "subject"},
    {"text": "把", "start": 1, "end": 2, "pos": "BA", "pinyin": "bǎ", "role": "other"},
    {"text": "作业", "start": 2, "end": 4, "pos": "NN", "pinyin": "zuòyè", "role": "object"},
    {"text": "做", "start": 4, "end": 5, "pos": "VV", "pinyin": "zuò", "role": "predicate"},
    {"text": "完", "start": 5, "end": 6, "pos": "VV", "pinyin": "wán", "role": "complement"},
    {"text": "了", "start": 6, "end": 7, "pos": "AS", "pinyin": "le", "role": "other"},
    {"text": "。", "start": 7, "end": 8, "pos": "PU", "pinyin": "", "role": "punctuation"}
  ],
  "dependencies": [
    {"head": 3, "dependent": 0, "label": "nsubj", "source": "rule_fallback"},
    {"head": 3, "dependent": 2, "label": "obj", "source": "rule_fallback"}
  ],
  "translation_vi": "Tôi đã làm xong bài tập.",
  "explanation_vi": "Câu dùng cấu trúc 把: tân ngữ 作业 được đưa lên trước động từ 做 để nhấn mạnh việc xử lý/hoàn tất đối tượng. 完 là bổ ngữ kết quả, 了 ở cuối báo hiệu hành động đã hoàn thành.",
  "patterns": [
    {"name": "把字句", "description_vi": "Cấu trúc 把: S + 把 + O + V + bổ ngữ/了, nhấn mạnh xử lý đối tượng.", "confidence": 0.8}
  ],
  "corrections": [],
  "warnings": [
    "Kết quả từ rule_fallback: quan hệ phụ thuộc là heuristic, không phải output của parser thống kê."
  ],
  "provenance": {
    "engine": "rule_fallback",
    "model": null,
    "parsers": []
  },
  "uncertainty": 0.65
}
```

Ghi chú về các trường:

- `tokens[].role`: một trong `subject`, `predicate`, `object`, `adverbial`, `attributive`, `complement`, `other`, `punctuation`.
- `dependencies[].head` / `dependencies[].dependent`: **chỉ số token** trong `tokens[]` (không phải char offset); `head = -1` nghĩa là token đó là root. `dependencies[].source`: `"rule_fallback"`, `"hanlp"`, `"ltp"` hoặc `"hanlp+ltp"` — fallback luôn tự khai là `rule_fallback`, không giả danh parser.
- `patterns[]`: danh sách object `{"name": string, "description_vi": string, "confidence": float 0.0–1.0}` (mặc định `confidence = 0.8`).
- `corrections[]`: mỗi phần tử dạng `{"original": "...", "corrected": "...", "explanation_vi": "..."}`. Rỗng khi không phát hiện lỗi.
- `warnings[]`: danh sách **chuỗi** (string) — bất đồng parser, câu mơ hồ, cảnh báo fallback; không giấu người dùng.
- `provenance.model`: tên model LLM khi thật sự có LLM tham gia (ví dụ `"qwen3:8b"` qua Ollama), ngược lại là `null`.
- `uncertainty`: **một số float** 0.0 (chắc chắn) → 1.0 (rất không chắc). Chỉ dùng rule fallback thì uncertainty cao (ví dụ 0.65 trong ví dụ trên, khớp hành vi implementation).

**Response lỗi (ví dụ thiếu token):**

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Missing Bearer device token"
  }
}
```

**Response lỗi (ví dụ câu quá dài):**

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed: body.text: String should have at most 2000 characters"
  }
}
```

## POST /v1/pair

Ghép nối thiết bị Android mới. Không cần Bearer token — bảo vệ bằng pairing code + rate limit chung.

**Pairing code**: gồm **8 ký tự** (chữ in hoa + số, bỏ ký tự dễ nhầm), được sinh khi server khởi động và **in đúng 1 lần trên console PC** dạng `PAIRING CODE (shown once): XXXXXXXX`, hoặc đặt cố định qua env `PAIRING_CODE`. Server chỉ giữ SHA-256 hash của mã và so sánh constant-time — mã không hết hạn theo thời gian và server không có endpoint nào hiển thị lại mã.

**Request:**

```json
{
  "pairing_code": "AB3K9XYZ",
  "device_name": "Pixel 8"
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `pairing_code` | string (1–128 ký tự) | có | Mã đọc trên console PC |
| `device_name` | string (1–128 ký tự) | không | Tên hiển thị của thiết bị; mặc định `"android-device"` |

Lưu ý: client **không** tự cấp `device_id` — server tự sinh khi pairing thành công.

**Response 200:**

```json
{
  "device_id": "dev_9f2c1e4a8b7d",
  "device_token": "<opaque-token-chi-tra-mot-lan>",
  "schema_version": "1.0"
}
```

> `device_token` chỉ xuất hiện trong response này. Server chỉ lưu SHA-256 hash của token trong SQLite. Android lưu token bằng Android Keystore. Không endpoint nào khác trả lại token, kể cả diagnostics.

**Response 403 (sai mã):**

```json
{
  "error": {
    "code": "forbidden",
    "message": "Invalid pairing code"
  }
}
```

## POST /v1/revoke-device

Thu hồi quyền của một thiết bị. **Yêu cầu Bearer token** (khi `REQUIRE_AUTH=1`).

**Request:**

```json
{
  "device_id": "dev_9f2c1e4a8b7d"
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `device_id` | string \| null | không | Thiết bị cần thu hồi. **Bỏ trống/null thì thiết bị đang trình token tự thu hồi chính nó** |

**Response 200:**

```json
{
  "revoked": true,
  "device_id": "dev_9f2c1e4a8b7d"
}
```

Sau khi revoke, token đó dùng tiếp sẽ nhận `401` với code `unauthorized`. Muốn dùng lại phải pairing lại từ đầu.

---

## Phụ lục — Danh sách field theo schema (`server/app/schemas.py`)

- `AnalyzeRequest`: `text`, `hsk_level`, `mode`
- `AnalyzeResponse`: `schema_version`, `tokens`, `dependencies`, `translation_vi`, `explanation_vi`, `patterns`, `corrections`, `warnings`, `provenance`, `uncertainty`
- `Token`: `text`, `start`, `end`, `pos`, `pinyin`, `role`
- `Dependency`: `head`, `dependent`, `label`, `source`
- `GrammarPattern`: `name`, `description_vi`, `confidence`
- `Correction`: `original`, `corrected`, `explanation_vi`
- `Provenance`: `engine`, `model`, `parsers`
- `PairRequest`: `pairing_code`, `device_name`
- `PairResponse`: `device_id`, `device_token`, `schema_version`
- `RevokeRequest`: `device_id`
- `RevokeResponse`: `revoked`, `device_id`
- `ErrorResponse`: `error` → `ErrorBody`: `code`, `message`
- `GET /capabilities` (dict, không qua Pydantic): `schema_version`, `engine`, `engines`, `parsers_available`, `ollama`, `auth_required`, `max_text_chars`, `max_body_bytes`
