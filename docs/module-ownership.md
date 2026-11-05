# Planned Module Ownership & Oral Defense Map

> Đây là kế hoạch phân công, không phải tuyên bố đóng góp đã hoàn thành. Chỉ cập nhật bảng Contribution trong
> README sau khi đối chiếu commit, PR, test và xác nhận của cả ba thành viên.

## Nguyên tắc

- Mỗi người sở hữu code, test và tài liệu tương ứng.
- Mỗi người phải chỉ được entry point, shared state, error path và test của module mình.
- Cả ba đều phải hiểu SCP frame và luồng register → login → chat.
- PR thay đổi interface giữa hai module cần owner phía còn lại review.

## 1. Nguyễn Đức Hải — Protocol & Chat Commands

**Modules dự kiến**

- `server/.../protocol/`: length-prefix reader/writer, JSON codec, schema validator, DTO/status code.
- `server/.../chat/`: `SEND_MESSAGE`, `LIST_USERS`, tạo event và gọi broadcast.
- `tests/protocol/`: split prefix/body, nhiều frame/read, UTF-8 length, oversized/malformed frame.
- Đồng sở hữu `docs/protocol-design.md`.

**Ranh giới:** không sửa account store; dùng API của `SessionManager`, không truy cập map nội bộ; đổi protocol phải
cập nhật protocol doc cùng PR.

**Cần bảo vệ:** TCP không có message boundary; `readFully(4)`/`readFully(N)`; byte UTF-8 khác số ký tự; tránh ghi
xen kẽ trên socket; xử lý prefix sai và slow client.

**Live change phù hợp:** đổi max frame/content, thêm validation hoặc command đơn giản `SERVER_TIME`.

## 2. Ngô Hoàng Minh — Server, Authentication & Session Concurrency

**Modules dự kiến**

- `server/.../server/`: bootstrap, env config, accept loop, bounded executor, shutdown.
- `server/.../auth/`: `REGISTER`, `LOGIN`, password hashing, account repository.
- `server/.../session/`: state machine, unique login, logout/disconnect cleanup, presence.
- `tests/server/`: concurrent registration/login, duplicate login, disconnect, timeout, pool saturation.
- Đồng sở hữu `docs/architecture.md`.

**Ranh giới:** cung cấp API thread-safe, không encode frame; repository là nơi duy nhất đọc/ghi account; plaintext
password không rời auth service hoặc xuất hiện trong log; cleanup dùng key + expected session.

**Cần bảo vệ:** client thứ 101; race khi register/login cùng email; `putIfAbsent`; không khóa khi broadcast;
cleanup cho QUIT, EOF, reset, timeout và shutdown.

**Live change phù hợp:** đổi max clients/timeout qua env, policy login trùng hoặc thêm account field.

## 3. Quách Thành Đạt — JavaFX TCP Client & End-to-End Tests

**Modules dự kiến**

- `client/.../ChatApplication.java`: đăng ký, đăng nhập, chat tổng, online users, error/loading state.
- `client/.../TcpChatClient.java`: TCP lifecycle, SCP codec, response correlation và server events.
- `tests/e2e/`: JavaFX client → server, Docker smoke, reconnect/disconnect, server errors.
- Đồng sở hữu `docs/testing-guide.md` và deployment client.

**Ranh giới:** client không truy cập account store/luật nghiệp vụ; mỗi app có một TCP socket; giữ nguyên
`requestId`; đóng cửa sổ thì đóng socket và server dọn session.

**Cần bảo vệ:** vì sao JavaFX không cần WebSocket; ghép response theo `requestId` trong khi event đến bất kỳ lúc
nào; không block Application Thread; UI xử lý login sai, server busy và TCP reset.

**Live change phù hợp:** thêm form validation, hiển thị status code hoặc reconnect indicator.

## 4. Shared Integration Contracts

| Contract | Primary owner | Required reviewer |
|----------|---------------|-------------------|
| SCP frame/message schema | Nguyễn Đức Hải | Quách Thành Đạt |
| Session/auth state machine | Ngô Hoàng Minh | Nguyễn Đức Hải |
| Client close/error mapping | Quách Thành Đạt | Ngô Hoàng Minh |
| Docker networking/env | Quách Thành Đạt | Ngô Hoàng Minh |
| Register/login/chat E2E | Quách Thành Đạt | Nguyễn Đức Hải và Ngô Hoàng Minh |

## 5. Minimum Knowledge for Everyone

1. JavaFX client → TCP server và chiều ngược lại.
2. Frame `4-byte big-endian length + UTF-8 JSON`.
3. State `CONNECTED → READY → AUTHENTICATED → CLOSED`.
4. Shared state nào cần bảo vệ và vì sao server cần concurrency.
5. Docker server, `localhost:5000`, JavaFX native client và cách xem log.
6. Một happy-path test, một malformed test và một abrupt-disconnect test.
