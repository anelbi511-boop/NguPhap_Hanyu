# Hướng dẫn cài đặt — Chinese Grammar AI

> Dành cho người dùng cuối (chính chủ máy). Hai phần độc lập: chỉ dùng Offline trên điện thoại thì **không cần** cài phần Windows.

## Phần A — Windows Server (chế độ Advanced)

### Yêu cầu

- Windows 10/11, RAM ≥ 16 GB (Qwen3 8B qua Ollama ngốn khoảng 6–8 GB khi chạy), ổ trống ≥ 15 GB.
- Python **3.11** (kiểm tra: `python --version`).
- [Ollama for Windows](https://ollama.com) đã cài.
- Điện thoại và PC cùng mạng LAN, hoặc cả hai cùng mạng Tailscale.

### Bước 1 — Lấy mã nguồn & tạo môi trường Python

```bat
cd ChineseGrammarAI\server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

> Lần cài đầu gồm HanLP và LTP nên sẽ lâu và tải thêm model NLP (vài GB). Đây là tải do bạn chủ động chạy lệnh này — app không tự tải ngầm.

### Bước 2 — Tải model Qwen3 8B cho Ollama

```bat
ollama pull qwen3:8b
ollama list
```

Kiểm tra Ollama chỉ nghe ở máy local (mặc định đúng như vậy — **đừng** đặt `OLLAMA_HOST=0.0.0.0`):

```bat
curl http://127.0.0.1:11434/api/tags
```

### Bước 3 — Chạy server

Cách dễ nhất — dùng script có sẵn:

```bat
cd ChineseGrammarAI\server
run_windows.bat
```

Hoặc chạy tay:

```bat
cd server
.venv\Scripts\activate
set HOST=0.0.0.0
set PORT=8730
python -m app.main
```

> `HOST=0.0.0.0` ở đây là của **FastAPI gateway đã có xác thực device token** để điện thoại trong LAN kết nối được (mặc định server chỉ bind `127.0.0.1`). Ollama (port 11434) vẫn chỉ ở `127.0.0.1`. **Không** mở port 8730 ra Internet bằng port forwarding trên router — muốn dùng ngoài nhà thì dùng Tailscale (Bước 5).

Kiểm tra server sống:

```bat
curl http://127.0.0.1:8730/health
```

Kết quả mong đợi: `{"status":"ok","schema_version":"1.0"}`

### Bước 4 — Lấy mã ghép nối (pairing)

Không có trang web pairing. Khi server khởi động, **console in ra đúng 1 dòng** dạng `PAIRING CODE (shown once): XXXXXXXX` — mã gồm **8 ký tự** chữ/số. (Có thể tự đặt mã cố định bằng biến môi trường `PAIRING_CODE` trước khi chạy.) Ghi lại mã đó rồi sang điện thoại làm Phần C; khởi động lại server sẽ sinh mã mới (trừ khi đã đặt `PAIRING_CODE`).

### Bước 5 (tuỳ chọn) — Dùng qua Tailscale

1. Cài Tailscale trên cả PC và điện thoại, đăng nhập cùng tài khoản.
2. Trên điện thoại, khi pairing, nhập địa chỉ dạng `http://<ten-may>.tailnet.ts.net:8730` thay cho IP LAN.
3. Cách này mã hoá đầu-cuối bằng WireGuard và dùng được ngoài mạng nhà.

## Phần B — Cài app Android

1. Copy file APK (bản release đã ký) vào điện thoại.
2. Mở file APK → cho phép "Cài ứng dụng từ nguồn này" khi Android hỏi → Cài đặt.
3. Mở app lần đầu: app ở **chế độ Offline mặc định**, chưa gửi gì đi đâu cả.

### Tải model Offline (Qwen3 0.6B GGUF)

1. Trong app: **Cài đặt → Quản lý model → Tải model Offline**.
2. App hiện rõ dung lượng (khoảng 400 MB) và kiểm tra ổ trống trước khi tải — bấm **Tải** mới bắt đầu, app không tự tải ngầm.
3. Tải xong app tự kiểm tra **SHA-256**; hiện "Đã xác thực" mới dùng được. Tải dở dang thì bấm Tải tiếp để resume.
4. Máy yếu / thiếu RAM: app sẽ cảnh báo và vẫn cho dùng phần từ điển + rule grammar mà không cần model.

## Phần C — Ghép nối điện thoại với PC (Pairing)

1. PC: xem dòng `PAIRING CODE (shown once): XXXXXXXX` trên console server (Phần A, Bước 4) để lấy mã 8 ký tự.
2. Điện thoại: **Cài đặt → Kết nối PC → Ghép nối thiết bị mới**.
3. Nhập địa chỉ server (IP LAN của PC, ví dụ `192.168.1.20:8730`, hoặc tên Tailscale) → nhập mã 8 ký tự → **Ghép nối**.
4. Thành công: trạng thái chuyển "Đã kết nối — Windows Advanced". Token được lưu an toàn trong máy (Android Keystore), bạn không cần nhập lại.
5. Kiểm tra ngay: nhập thử một câu ở màn hình Phân tích, chọn chế độ **Advanced** — kết quả trả về có ghi nguồn "windows-advanced".

### Bật chế độ Auto

**Cài đặt → Quyền riêng tư → Chế độ Auto**: lần đầu bật, app hiện hộp thoại giải thích rõ câu sẽ được gửi tới PC của chính bạn; bấm **Đồng ý** mới bật. Tắt công tắc này bất cứ lúc nào để thu hồi — app quay về Offline ngay.

### Huỷ ghép nối

- Trên điện thoại: **Cài đặt → Kết nối PC → Huỷ ghép nối** — app gọi `POST /v1/revoke-device` bằng chính token của nó rồi xoá token local.
- Trên PC: không có console quản lý thiết bị; để thu hồi toàn bộ, dừng server rồi xoá file `server/data/devices.db` (mọi điện thoại phải ghép nối lại).

## Xử lý sự cố (Troubleshooting)

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| App báo "Không thấy PC" | Khác mạng Wi-Fi; firewall Windows chặn port 8730; PC ngủ; server vẫn bind `127.0.0.1` | Kiểm tra cùng LAN; cho phép `python.exe`/port 8730 qua Windows Firewall (mạng Private); chạy server với `HOST=0.0.0.0`; chạy `curl http://127.0.0.1:8730/health` trên PC |
| `/health` trên PC cũng không trả | Server chưa chạy / crash khi nạp HanLP-LTP | Xem cửa sổ chạy server; kiểm tra đã `activate` đúng `.venv` và Python 3.11 |
| Ollama lỗi `connection refused` | Ollama chưa chạy | Mở app Ollama trên Windows, chạy lại `ollama list` |
| Phân tích Advanced rất chậm lần đầu | Model 8B nạp lần đầu vào RAM/VRAM | Bình thường ở câu đầu tiên; các câu sau nhanh hơn. RAM < 16 GB sẽ chậm rõ rệt |
| Mã pairing báo sai (HTTP 403 `forbidden`) | Gõ sai mã, hoặc server đã restart nên mã cũ không còn đúng (trừ khi đặt `PAIRING_CODE` cố định) | Xem lại dòng `PAIRING CODE` trên console của lần chạy server hiện tại, nhập lại đúng mã 8 ký tự |
| App báo lỗi `401 unauthorized` khi phân tích | Token đã bị thu hồi (hoặc file `devices.db` bị xoá) | Ghép nối lại từ đầu (Phần C) |
| Tải model Offline báo sai checksum | File tải hỏng / mạng cắt giữa chừng | Xoá model trong Quản lý model rồi tải lại; không dùng model chưa qua xác thực |
| App tự rơi về Offline khi đang Auto | PC mất kết nối / hết timeout | Đây là hành vi đúng thiết kế (fallback an toàn). Kiểm tra lại PC rồi phân tích lại |
| Câu dài bị từ chối (HTTP 422 `validation_error`) | Giới hạn 2000 ký tự/câu | Chia câu dài thành nhiều câu ngắn |

## Gỡ cài đặt

- Android: gỡ app như bình thường; muốn xoá sạch dữ liệu học tập trước khi gỡ: **Cài đặt → Quyền riêng tư → Xoá toàn bộ lịch sử**.
- Windows: dừng server, xoá thư mục dự án; gỡ model Ollama bằng `ollama rm qwen3:8b`.
