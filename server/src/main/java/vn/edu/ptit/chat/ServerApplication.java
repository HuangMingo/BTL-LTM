package vn.edu.ptit.chat;

import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.*;

public final class ServerApplication {
  /** Khởi tạo các thành phần và chạy vòng lặp nhận kết nối của server. */
  public static void main(String[] args) throws Exception {
    // Đọc cấu hình mạng từ , giới hạn client và database từ biến môi trường.
    Config c = Config.fromEnvironment(); // Lấy cấu hình từ file .env
    Database d = new Database(c); // Tạo database từ cấu hình

    // Chờ database sẵn sàng và tạo/cập nhật schema trước khi mở cổng server.
    waitForDatabase(d, Duration.ofSeconds(60));
    SessionManager s = new SessionManager();  

    // Mỗi client chiếm một worker; SynchronousQueue không lưu hàng đợi kết nối chờ.
    ThreadPoolExecutor pool = new ThreadPoolExecutor(
        c.maxClients(),
        c.maxClients(),
        0L,
        TimeUnit.MILLISECONDS,
        new SynchronousQueue<>(),
        new ThreadPoolExecutor.AbortPolicy());

    // Dừng các worker khi JVM nhận tín hiệu tắt, ví dụ docker compose down.
    Runtime.getRuntime().addShutdownHook(new Thread(pool::shutdownNow));

    // try-with-resources bảo đảm ServerSocket được đóng khi server kết thúc.
    try (ServerSocket server = new ServerSocket()) {
      server.setReuseAddress(true);
      server.bind(new InetSocketAddress(c.host(), c.port()));
      System.out.printf(
          "SCP/1.0 server listening on %s:%d (maxClients=%d)%n",
          c.host(), c.port(), c.maxClients());
      while (!pool.isShutdown()) {
        // accept() chờ một client mới rồi giao việc xử lý cho thread pool.
        Socket socket = server.accept();
        try {
          pool.execute(new ClientHandler(socket, c, d, s));
        } catch (RejectedExecutionException e) {
          // Pool đã đủ worker: báo lỗi 503 thay vì để client chờ vô hạn.
          rejectBusy(socket);
        }
      }
    }
  }

  /** Gửi phản hồi SERVER_BUSY và đóng socket của client bị từ chối. */
  private static void rejectBusy(Socket socket) {
    try (socket) {
      Protocol.write(
          new java.io.DataOutputStream(socket.getOutputStream()),
          Protocol.response("ERROR", null, 503, "Server is at connection capacity"));
    } catch (java.io.IOException ignored) {
      // Client có thể đã ngắt kết nối; socket vẫn được try-with-resources đóng lại.
    }
  }

  /**
   * Thử migrate database mỗi giây cho đến khi thành công hoặc hết thời gian chờ.
   */
  private static void waitForDatabase(Database d, Duration timeout)
      throws InterruptedException, SQLException {
    long end = System.nanoTime() + timeout.toNanos();
    SQLException last = null;
    while (System.nanoTime() < end) {
      try {
        d.migrate();
        return;
      } catch (SQLException e) {
        last = e;
        Thread.sleep(1000);
      }
    }

    // Giữ lại lỗi SQL cuối cùng để thông báo đúng nguyên nhân khi khởi động thất
    // bại.
    throw last == null ? new SQLException("Database unavailable") : last;
  }
}