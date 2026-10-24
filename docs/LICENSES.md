# License Inventory — Chinese Grammar AI

> ⚠️ **Verify-at-release disclaimer**: bảng dưới đây phản ánh hiểu biết tại thời điểm viết tài liệu (10/2026). Trước mỗi lần phát hành (kể cả phát hành cá nhân có chia sẻ file APK / mã nguồn cho người khác), **phải kiểm tra lại license trực tiếp trên trang/repo chính thức của từng thành phần**, vì điều khoản có thể thay đổi giữa các phiên bản model và thư viện. Đặc biệt chú ý CC-CEDICT (share-alike) và điều khoản model của HanLP.

## Models & Data

| Thành phần | Dùng để | License | Ghi chú |
|---|---|---|---|
| Qwen3 0.6B / 1.7B (GGUF) | Offline LLM trên Android | Apache-2.0 | Kiểm tra lại license trên repo model chính thức của Qwen tại thời điểm tải bản cụ thể |
| Qwen3 8B (qua Ollama) | Advanced LLM trên Windows | Apache-2.0 | Như trên; bản phân phối qua Ollama không đổi license gốc |
| HanLP 2.1 models & code | NLP tiếng Trung: segmentation, POS, dependency | Apache-2.0 | **Lưu ý**: HanLP có một số model/dữ liệu đi kèm điều khoản riêng cho dùng thương mại — dự án này là personal use; nếu sau này thương mại hoá phải rà soát lại từng model |
| CC-CEDICT | Từ điển Trung–Anh nền tảng cho từ điển offline (nghĩa tiếng Việt do dự án bổ sung/gắn nhãn riêng where licensed) | **CC BY-SA 4.0** | **Bắt buộc ghi công (attribution) CC-CEDICT/MDBG** trong app (màn hình Giới thiệu) và tài liệu; phần dữ liệu dẫn xuất từ CC-CEDICT phải chia sẻ theo **cùng điều khoản share-alike** — không được đóng kín phần dữ liệu này |

## Libraries — Windows Server

| Thành phần | License |
|---|---|
| FastAPI | MIT |
| Pydantic | MIT |
| Uvicorn | BSD-3-Clause |
| HanLP 2.1 (library) | Apache-2.0 |
| LTP | MIT |
| pypinyin | MIT |
| Ollama (client/server binary) | MIT |

## Libraries — Android & Native

| Thành phần | License |
|---|---|
| llama.cpp | MIT |
| AndroidX (Compose, Room, Lifecycle…) | Apache-2.0 |
| Kotlin stdlib / Coroutines | Apache-2.0 |
| Ktor Client / Retrofit + OkHttp | Apache-2.0 |
| Material 3 (Material Components) | Apache-2.0 |

## Công cụ phát triển (không phân phối kèm sản phẩm)

| Thành phần | License |
|---|---|
| pytest | MIT |
| Gradle | Apache-2.0 |
| GitHub Actions runners | Theo điều khoản GitHub |

## Nghĩa vụ khi phân phối

1. Giữ nguyên các file LICENSE/NOTICE của thành phần Apache-2.0 và MIT trong bản phân phối.
2. Hiển thị attribution CC-CEDICT trong app và giữ điều khoản CC BY-SA 4.0 cho dữ liệu dẫn xuất.
3. Sinh SBOM (CycloneDX) kèm mỗi release và đối chiếu lại với bảng này.
4. Không dùng tên/logo của Qwen, HanLP, LTP theo cách ám chỉ được các dự án đó chứng thực (endorsement).
