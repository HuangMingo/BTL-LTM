package vn.edu.ptit.chat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
  // Lưu mỗi người dùng đang online cùng ClientHandler quản lý kết nối của họ.
  // ConcurrentHashMap cho phép nhiều thread đăng nhập, đăng xuất và đọc dữ liệu an toàn.
  private final ConcurrentHashMap<UUID, ClientHandler> online = new ConcurrentHashMap<>();
  /** Thêm phiên đăng nhập nếu tài khoản chưa online; trả về false nếu đã đăng nhập nơi khác. */
  public boolean login(UUID uid, ClientHandler handler) {
    // putIfAbsent thêm nguyên tử, tránh hai client cùng đăng nhập một tài khoản.
    return online.putIfAbsent(uid, handler) == null;
  }

  /** Xóa đúng phiên của người dùng khi họ đăng xuất hoặc mất kết nối. */
  public boolean logout(UUID uid, ClientHandler expected) {
    // remove(key, value) tránh một kết nối cũ xóa nhầm phiên mới của cùng tài khoản.
    return uid != null && online.remove(uid, expected);
  }

  /** Tạo danh sách JSON gồm các người dùng hiện đang online, sắp xếp theo email. */
  public ArrayNode users() {
    ArrayNode result = Protocol.JSON.createArrayNode();
    // Duyệt các handler đang online và chuyển thông tin cần thiết thành JSON.
    online.values().stream()
        .sorted(Comparator.comparing(ClientHandler::email))
        .forEach(
            h -> {
              ObjectNode u = result.addObject();
              u.put("uid", h.uid().toString());
              u.put("email", h.email());
            });
    return result;
  }

  /** Gửi một sự kiện tới mọi client online, ngoại trừ client được chỉ định nếu có. */
  public void broadcast(ObjectNode event, ClientHandler except) {
    // Sao chép danh sách trước khi duyệt để broadcast ổn định khi có login/logout đồng thời.
    for (ClientHandler handler : List.copyOf(online.values()))
      // except == null nghĩa là gửi cho tất cả client.
      if (handler != except) handler.trySend(event);
  }
}