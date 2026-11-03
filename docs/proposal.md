# Project Proposal — Milestone M1

> Các lựa chọn ban đầu sẽ được kiểm chứng trong M2 và cập nhật trong tài liệu thiết kế.

---

## 1. Team & Topic

- **Team:** Nhóm 001 — Nguyễn Đức Hải, Ngô Hoàng Minh, Quách Thành Đạt
- **Topic:** Ứng dụng web chat thời gian thực có tài khoản, sử dụng giao thức tự thiết kế trên Java TCP Socket
- **Thông tin cần bổ sung:** Mã sinh viên đầy đủ và tài khoản GitHub của Nguyễn Đức Hải, Quách Thành Đạt

## 2. Problem

Các nhóm học tập và làm việc nhỏ cần một kênh chat tức thời có thể tự triển khai trong mạng nội bộ hoặc trên máy
chủ riêng. Messenger, Zalo và Discord có nhiều chức năng nhưng phụ thuộc vào tài khoản và dịch vụ bên thứ ba,
không phù hợp khi người quản trị muốn kiểm soát server và giao thức. Dự án cung cấp một kênh chat văn bản chung,
đồng thời thể hiện rõ cách client và server giao tiếp bằng một giao thức tầng ứng dụng độc lập.

## 3. Idea

Một Java TCP server trung tâm cho phép nhiều người dùng đăng ký, đăng nhập và trao đổi tin nhắn trong kênh chat
chung bằng giao diện web. Vì trình duyệt không mở raw TCP socket, web client gồm giao diện chạy trong browser và
một gateway: mỗi kết nối WebSocket của browser được ánh xạ một-một sang một TCP socket tới server. Gateway chỉ
chuyển đổi biên truyền tải; mọi lệnh nghiệp vụ, trạng thái phiên và mã lỗi vẫn do giao thức TCP của nhóm quy định.
Hệ thống kiểm thử TCP bị phân mảnh/gộp dữ liệu, nhiều người gửi đồng thời và ngắt kết nối đột ngột.

## 4. Scope

| Must have (M3) | Nice to have | Out of scope |
|----------------|--------------|--------------|
| Đăng ký tài khoản và đăng nhập bằng tên người dùng/mật khẩu | Nhắn tin riêng | Gọi thoại/video và truyền tệp lớn |
| Gửi/nhận tin nhắn thời gian thực trong một kênh chat tổng | Lưu lịch sử tin nhắn | Nhiều phòng chat và mã hóa đầu cuối |
| Xem người đang online; đăng xuất/thoát đúng quy trình | Trạng thái đang nhập | Đăng nhập OAuth và khôi phục mật khẩu qua email |
| Nhiều client đồng thời, shared state an toàn luồng | Ghi nhớ phiên sau khi tải lại trang | Kiến trúc phân tán nhiều server |
| Chịu được dữ liệu sai, timeout, ngắt đột ngột; chạy bằng Docker Compose | Giao diện responsive nâng cao | Thay thế dịch vụ chat thương mại |

## 5. Key Networking Challenge

Thách thức chính là phát một tin nhắn nhất quán tới nhiều client đã xác thực và đọc đúng ranh giới thông điệp trên
luồng TCP. Mỗi JSON UTF-8 có tiền tố độ dài 4 byte big-endian; bên nhận đọc đủ prefix, kiểm tra giới hạn rồi đọc
đúng số byte của body. Server phải cập nhật nguyên tử kho tài khoản và danh sách phiên online, đồng thời tuần tự
hóa thao tác gửi tới cùng một socket để các frame từ nhiều worker không ghi xen kẽ. Web gateway phải duy trì ánh
xạ một-một giữa WebSocket và TCP socket, truyền nguyên vẹn `requestId` và đóng cả hai đầu khi một đầu bị ngắt.

## 6. Initial Design Sketch

- **Transport:** TCP vì chat cần truyền tin cậy và đúng thứ tự. UDP đòi hỏi nhóm tự làm xác nhận, truyền lại và
  sắp xếp gói, vượt quá phạm vi dự án.
- **Message framing & format:** `4-byte length prefix + UTF-8 JSON`, gồm `type`, `requestId`, `payload`; phản hồi có
  `status` và `message`. Server từ chối độ dài không hợp lệ hoặc vượt giới hạn.
- **Core commands:**

  | Command | Direction | Purpose |
  |---------|-----------|---------|
  | `HELLO` | Client → Server | Thỏa thuận phiên bản giao thức |
  | `REGISTER` | Client → Server | Tạo tài khoản mới |
  | `LOGIN` | Client → Server | Xác thực và bắt đầu phiên người dùng |
  | `SEND_MESSAGE` | Client → Server | Gửi tin nhắn tới kênh chat chung |
  | `LIST_USERS` | Client → Server | Lấy danh sách người dùng đang kết nối |
  | `PING` | Client → Server | Kiểm tra kết nối và duy trì phiên |
  | `LOGOUT` | Client → Server | Kết thúc phiên đăng nhập nhưng giữ kết nối TCP |
  | `QUIT` | Client → Server | Kết thúc phiên một cách chủ động |

  Server phản hồi từng yêu cầu và dùng các sự kiện `CHAT_MESSAGE`, `USER_JOINED`, `USER_LEFT` để cập nhật client.
- **Authentication:** Tên người dùng được chuẩn hóa để kiểm tra trùng; mật khẩu không ghi log và chỉ được lưu dưới
  dạng hash có salt. Một tài khoản chỉ có một phiên online tại một thời điểm trong phiên bản đầu.
- **Concurrency model:** Blocking socket với fixed thread pool và giới hạn số kết nối. Cách này dễ giải thích và
  kiểm thử hơn NIO event loop; mỗi TCP connection dài hạn chiếm một worker nên giới hạn pool cũng là giới hạn client.
- **Client web:** Browser dùng WebSocket tới gateway; gateway mở một TCP socket riêng tới server cho mỗi browser.
  Gateway không chứa luật nghiệp vụ và không truy cập trực tiếp kho tài khoản.
- **Languages:** Java cho TCP server; công nghệ web/gateway sẽ được cố định khi bắt đầu M2 và ghi vào kiến trúc.

## 7. Alternatives Considered

| Option | Chosen? | Why / why not |
|--------|:-------:|---------------|
| TCP | Yes | Tin cậy, đúng thứ tự và phù hợp với chat văn bản; vẫn yêu cầu nhóm tự thiết kế framing |
| UDP | No | Phải bổ sung xác nhận, truyền lại và sắp xếp gói; tăng rủi ro vượt phạm vi dự án |
| JSON có length prefix | Yes | Dễ đọc khi debug nhưng vẫn xử lý đúng dữ liệu bị chia/gộp trên TCP |
| JSON phân cách bằng ký tự xuống dòng | No | Đơn giản hơn nhưng phụ thuộc vào quy tắc escape và khó mở rộng an toàn cho payload phức tạp |
| Fixed thread pool + blocking socket | Yes | Dễ triển khai, giới hạn tài nguyên và phù hợp với quy mô bài tập |
| Java NIO/event loop | No, for now | Có khả năng mở rộng tốt hơn nhưng quản lý trạng thái phức tạp hơn và khó hoàn thành đúng thời hạn |
| WebSocket làm giao thức server chính | No | WebSocket làm framing hộ và làm yếu phần tự thiết kế giao thức TCP |
| Web UI + gateway TCP | Yes | Trình duyệt có giao diện web nhưng server vẫn nhận đúng giao thức TCP tự thiết kế |
| Lưu mật khẩu dạng rõ | No | Không an toàn; server chỉ lưu password hash có salt và không ghi mật khẩu vào log |

## 8. Ownership Plan

Phân công dưới đây là đề xuất ban đầu và phải được cả nhóm xác nhận trước khi đưa vào kế hoạch chính thức:

| Member | Will own (modules / documents) |
|--------|--------------------------------|
| Nguyễn Đức Hải | Giao thức, framing/parser phía server và client; đồng sở hữu `docs/protocol-design.md` |
| Ngô Hoàng Minh | Server, quản lý phiên/người dùng và an toàn luồng; đồng sở hữu `docs/architecture.md` |
| Quách Thành Đạt | Web client/gateway, kiểm thử tích hợp và các tình huống lỗi; đồng sở hữu `docs/testing-guide.md` |

Mỗi thành viên chịu trách nhiệm mã nguồn, kiểm thử và tài liệu liên quan đến component của mình.
Ranh giới module, integration contract và bộ câu hỏi vấn đáp được ghi tại
[`docs/module-ownership.md`](module-ownership.md).

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Race condition khi nhiều client kết nối, rời đi hoặc gửi tin cùng lúc | Xác định toàn bộ shared state trong M2, dùng cấu trúc concurrent/khóa có phạm vi nhỏ và viết kiểm thử nhiều client |
| Đọc sai thông điệp do giả định một lần `read()` nhận đủ dữ liệu | Dùng length-prefix, vòng lặp đọc đủ số byte và kiểm thử dữ liệu bị chia/gộp |
| Client ngắt đột ngột làm còn phiên hoặc socket rác | Dọn phiên trong `finally`, đặt timeout phù hợp và cập nhật danh sách online sau khi mất kết nối |
| Cạn thread hoặc client chậm làm server bị chặn | Đo tải, giới hạn pool/kích thước thông điệp và nghiên cứu hàng đợi gửi có giới hạn trong M2 |
| Nhóm chưa quen Docker hoặc lập trình mạng Java | Làm walking skeleton sớm: Docker chạy được, nhiều client kết nối và `HELLO` hoạt động end-to-end |
| Gateway che mất lỗi giao thức TCP | Viết test codec/socket trực tiếp vào port 5000, không chỉ kiểm thử qua trình duyệt |
| Sai lệch trạng thái giữa WebSocket và TCP khi một đầu đóng | Ánh xạ 1–1, đóng đối xứng hai đầu và dọn phiên server trong `finally` |
| Lộ mật khẩu trong log hoặc kho dữ liệu | Không log payload của `REGISTER`/`LOGIN`; chỉ lưu hash có salt; dùng tài khoản giả khi demo |
| Phạm vi tăng do làm UI quá sớm | Hoàn thành codec, `REGISTER`, `LOGIN` và `SEND_MESSAGE` end-to-end trước khi tinh chỉnh giao diện |

## 10. Instructor Feedback

*(Điền sau khi M1 được giảng viên nhận xét.)*
