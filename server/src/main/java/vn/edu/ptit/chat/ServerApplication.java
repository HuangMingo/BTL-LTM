package vn.edu.ptit.chat;

import java.net.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.*;

public final class ServerApplication {
  public static void main(String[] args) throws Exception {
    Config c = Config.fromEnvironment();
    Database d = new Database(c);
    waitForDatabase(d, Duration.ofSeconds(60));
    SessionManager s = new SessionManager();
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            c.maxClients(),
            c.maxClients(),
            0L,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.AbortPolicy());
    Runtime.getRuntime().addShutdownHook(new Thread(pool::shutdownNow));
    try (ServerSocket server = new ServerSocket()) {
      server.setReuseAddress(true);
      server.bind(new InetSocketAddress(c.host(), c.port()));
      System.out.printf(
          "SCP/1.0 server listening on %s:%d (maxClients=%d)%n",
          c.host(), c.port(), c.maxClients());
      while (!pool.isShutdown()) {
        Socket socket = server.accept();
        try {
          pool.execute(new ClientHandler(socket, c, d, s));
        } catch (RejectedExecutionException e) {
          rejectBusy(socket);
        }
      }
    }
  }

  private static void rejectBusy(Socket socket) {
    try (socket) {
      Protocol.write(
          new java.io.DataOutputStream(socket.getOutputStream()),
          Protocol.response("ERROR", null, 503, "Server is at connection capacity"));
    } catch (java.io.IOException ignored) {
    }
  }

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
    throw last == null ? new SQLException("Database unavailable") : last;
  }
}
