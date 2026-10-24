# Threat Model — Chinese Grammar AI

> Phạm vi: ứng dụng cá nhân (personal use), 1 người dùng, PC Windows và điện thoại Android của chính người dùng, kết nối qua LAN nhà hoặc Tailscale. Không phải hệ thống multi-tenant. Tài liệu này nói thẳng cả những rủi ro còn tồn tại — xem mục 6.

## 1. Tài sản cần bảo vệ (Assets)

| Tài sản | Nhạy cảm vì |
|---|---|
| Câu tiếng Trung người dùng nhập | Có thể chứa nội dung cá nhân, bài tập, tin nhắn riêng |
| Device token pairing | Ai có token là giả danh được điện thoại đã pairing |
| Offline model + dữ liệu từ điển | Tính toàn vẹn: model bị thay thế có thể trả kết quả sai lệch |
| Lịch sử học tập / từ vựng đã lưu | Riêng tư, nằm trên điện thoại |
| Signing key của APK | Mất key = kẻ khác ký được bản cập nhật giả |
| PC chạy server | Ollama/FastAPI bị lộ ra Internet = bị lạm dụng tài nguyên GPU/CPU |

## 2. Đối thủ & giả định (Adversaries & Assumptions)

- Kẻ tấn công trên cùng mạng LAN (Wi-Fi công cộng, mạng chung cư bị xâm nhập).
- Thiết bị Android bị mất / bị cầm bởi người khác trong thời gian ngắn.
- File import (dữ liệu backup, file model) bị sửa đổi / chứa nội dung độc hại.
- Nội dung câu đầu vào chứa prompt injection (cố ý hoặc vô tình khi paste văn bản lạ).
- **Ngoài phạm vi**: kẻ tấn công đã có quyền admin/root lâu dài trên PC hoặc điện thoại — khi đó mọi bảo vệ ở tầng ứng dụng đều vô hiệu, tài liệu không giả vờ khác đi.

## 3. Phân tích theo STRIDE

### 3.1 Android

| STRIDE | Mối đe doạ | Biện pháp |
|---|---|---|
| **S**poofing | App khác giả danh gửi request lên PC | Device token riêng từng máy, lưu bằng **Android Keystore** (EncryptedSharedPreferences), không lưu plaintext |
| **T**ampering | File model GGUF bị thay thế; file import backup bị sửa | Verify **SHA-256** model trước khi load, sai checksum là từ chối; import validate schema + giới hạn kích thước, sai là huỷ toàn bộ giao dịch import |
| **R**epudiation | — (app cá nhân, không có yêu cầu audit) | Không áp dụng; history chỉ phục vụ người dùng |
| **I**nfo disclosure | Token/câu lọt qua log; traffic plaintext bị nghe lén trên LAN | **Disable cleartext traffic** ở release build (`networkSecurityConfig`, `usesCleartextTraffic=false`); log redaction: không log nội dung câu, không log token; câu chỉ gửi khi có consent (Auto) hoặc người dùng chủ động chọn Advanced |
| **D**oS | Model quá lớn làm hết RAM/storage | Kiểm tra storage trống trước khi tải; kiểm tra RAM khả dụng, cảnh báo rõ trên máy yếu; giới hạn độ dài câu phân tích |
| **E**levation | Path traversal khi giải nén / import file (`../`, absolute path) — **zip-slip** | Giải nén/import: chuẩn hoá path, từ chối mọi entry thoát khỏi thư mục đích; không dùng WebView với nội dung không tin cậy; tắt JavaScript bridge không cần thiết |

Thêm:

- Quyền Android tối thiểu: `INTERNET`, `ACCESS_NETWORK_STATE`. Không xin quyền danh bạ, vị trí, micro trừ khi tính năng tương lai thật sự cần và được giải thích.
- Release build: tắt `debuggable`, ký bằng keystore cá nhân giữ ngoài repo.

### 3.2 Windows Server

| STRIDE | Mối đe doạ | Biện pháp |
|---|---|---|
| **S**poofing | Máy lạ trong LAN gọi API | `/v1/analyze` và `/v1/revoke-device` bắt buộc Bearer device token (`/health`, `/capabilities` là public và không lộ dữ liệu nhạy cảm; `/v1/pair` bảo vệ bằng pairing code). Pairing code gồm 8 ký tự, in 1 lần trên console PC lúc server khởi động (hoặc đặt qua env `PAIRING_CODE`), server chỉ giữ SHA-256 hash, so sánh constant-time; rate limit chung 120 req/phút/IP cũng áp cho các lần thử pairing |
| **T**ampering | Request bị sửa giữa đường | **TLS** (self-signed có pinning phía Android) hoặc đi qua **Tailscale** (WireGuard); không chấp nhận HTTP plaintext ngoài loopback |
| **R**epudiation | Không truy được device nào đã gọi | Log server ghi `device_id` + thời gian (không ghi nội dung câu) |
| **I**nfo disclosure | Diagnostics lộ token/secret | Endpoint diagnostics **không bao giờ** trả token, pairing code, hay hash đầy đủ; Ollama chỉ bind `127.0.0.1:11434`, **không expose ra LAN/Internet** — Android chỉ nói chuyện với FastAPI gateway đã auth |
| **D**oS | Request khổng lồ / flood làm treo Ollama | **Request size limit 32 KB (32768 bytes)**, text tối đa 2000 ký tự, **rate limit 120 request/phút/IP** (`/health` được miễn), timeout cứng cho từng request gọi Ollama |
| **E**levation | Model output bị dùng để chạy lệnh | Server **không bao giờ thực thi** nội dung do model sinh ra; không có đường nào từ response của Qwen tới shell/subprocess với input không kiểm soát |

Thêm:

- **Device revocation**: thu hồi token bất cứ lúc nào từ PC console hoặc từ chính thiết bị (`POST /v1/revoke-device`); token lưu dạng hash phía server.
- Server chạy bằng tài khoản Windows thường (least privilege), không cần admin.
- Firewall: chỉ mở port gateway cho LAN/Tailscale interface; hướng dẫn cài đặt nhắc rõ không port-forward ra Internet.

### 3.3 AI / LLM

| Mối đe doạ | Biện pháp |
|---|---|
| **Prompt injection** trong câu người dùng paste vào (ví dụ câu chứa "ignore previous instructions…") | Coi mọi input là **untrusted data**: câu được đưa vào prompt dưới dạng dữ liệu có delimit rõ ràng + structured evidence, kèm chỉ thị hệ thống cố định; model không có quyền truy cập tool/shell/network |
| Model bịa quan hệ cú pháp / giải thích sai | Quan hệ phụ thuộc lấy từ HanLP/LTP, model chỉ diễn giải; output validate bằng **strict JSON schema** (Pydantic), sai schema thì loại bỏ/retry giới hạn; gắn `provenance` + `uncertainty` |
| Model output chứa nội dung HTML/script nguy hiểm | Android render bằng Compose text thuần, không qua WebView/`innerHTML`; escape mặc định |
| Output quá dài / loop | Giới hạn `max_tokens`, timeout, cắt + gắn warning khi bị truncate |
| Rò rỉ dữ liệu qua model | Không gửi gì ra ngoài: Qwen chạy local qua Ollama loopback; HanLP/LTP chạy local. **Không có external AI provider nào trong hệ thống** |

Nói thẳng: **không có kỹ thuật nào ngăn prompt injection tuyệt đối**. Thiết kế này giảm thiểu bằng cách tước quyền của model (không tool, không shell, output bị schema hoá) nên kể cả khi injection "thành công", thiệt hại tối đa là một phần giải thích sai — được giảm tiếp bằng provenance/uncertainty và parser ground truth.

### 3.4 Supply Chain

| Mối đe doạ | Biện pháp |
|---|---|
| Dependency bị compromise / typosquatting | Pin phiên bản chính xác (Gradle version catalog, `requirements.txt` có hash/`==`), không dùng `latest`/`+` |
| Model GGUF / model Ollama tải về bị thay thế | Verify **SHA-256** trước khi dùng; ghi checksum kỳ vọng trong tài liệu phát hành; tải model **không bao giờ tự động im lặng** — người dùng chủ động bấm tải, thấy rõ dung lượng |
| Không biết app gồm những gì | Sinh **SBOM** khi release (CycloneDX cho cả Gradle và pip) kèm release checksums |
| License không tương thích | Kiểm kê ở `LICENSES.md`; kiểm tra lại trước mỗi release |

## 4. Luồng dữ liệu nhạy cảm — tóm tắt

```
Câu của người dùng:
  Android ──(chỉ khi Advanced/Auto + consent)──TLS/Tailscale──> PC của chính người dùng
  Không đi qua: internet công cộng plaintext, bên thứ ba, external AI API — không có ngoại lệ.
```

## 5. Kiểm thử bảo mật đã thiết kế

- Test input dị dạng: câu cực dài, ký tự điều khiển, emoji/surrogate pairs, JSON lồng nhau.
- Test prompt injection: bộ câu chứa chỉ thị injection trong `eval/` mở rộng — kỳ vọng: output vẫn đúng schema, không có hành vi ngoài phân tích ngữ pháp.
- Test zip-slip / path traversal cho import & giải nén model.
- Test revoke: token cũ bị từ chối ngay sau revoke.
- Test rate limit & payload limit trả đúng `429` / `413`.

Trạng thái kiểm chứng thực tế của từng mục được ghi ở `README.md` (bảng Verified vs Unverified) — tài liệu này mô tả thiết kế, không tự nhận là đã test.

## 6. Rủi ro còn tồn tại (Residual Risks) — nói thẳng

1. **Self-signed TLS + pinning thủ công**: nếu người dùng bỏ qua bước verify fingerprint khi pairing lần đầu trên LAN đã bị xâm nhập, MITM lần đầu (TOFU) là có thể. Tailscale giảm rủi ro này và là lựa chọn khuyến nghị khi dùng ngoài LAN nhà.
2. **Prompt injection không thể triệt tiêu 100%** — chỉ giới hạn được thiệt hại (mục 3.3).
3. **Điện thoại bị mở khoá và bị cầm lâu dài**: Keystore bảo vệ token khi máy khoá; khi máy đang mở khoá, người cầm máy dùng được app như chủ máy.
4. **HanLP/LTP/Qwen đều có thể phân tích sai** — đây là rủi ro đúng đắn chuyên môn, không phải bảo mật; hệ thống giảm bằng disagreement detection + uncertainty, nhưng người học không nên coi output là chân lý tuyệt đối.
5. **Metadata**: server log (đã redact nội dung) vẫn cho thấy thời điểm và tần suất sử dụng của từng device.
6. **Chưa có kiểm toán bảo mật độc lập** — đây là dự án cá nhân, threat model này do nhóm phát triển tự viết và tự kiểm thử.
