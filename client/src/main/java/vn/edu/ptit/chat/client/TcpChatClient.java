package vn.edu.ptit.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class TcpChatClient implements AutoCloseable {
  private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<Consumer<JsonNode>> eventListeners =
      new CopyOnWriteArrayList<>();
  private final Object sendLock = new Object();
  private ScheduledExecutorService keepAlive;
  private Socket socket;
  private DataInputStream input;
  private DataOutputStream output;
  private volatile boolean open;

  public CompletableFuture<Void> connect(String host, int port) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            open = true;
            Thread.ofPlatform().name("scp-reader").daemon(true).start(this::readLoop);
            ObjectNode hello = ProtocolCodec.JSON.createObjectNode();
            hello.put("protocolVersion", "SCP/1.0").put("clientName", "javafx-desktop");
            JsonNode response = request("HELLO", hello).orTimeout(8, TimeUnit.SECONDS).join();
            if (response.path("status").asInt() != 200)
              throw new IOException(response.path("message").asText());
            keepAlive =
                Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                      Thread thread = new Thread(runnable, "scp-keepalive");
                      thread.setDaemon(true);
                      return thread;
                    });
            keepAlive.scheduleAtFixedRate(
                () ->
                    request("PING", ProtocolCodec.JSON.createObjectNode())
                        .exceptionally(ignored -> null),
                30,
                30,
                TimeUnit.SECONDS);
          } catch (Exception exception) {
            close();
            throw new CompletionException(rootMessage(exception), exception);
          }
        });
  }

  public CompletableFuture<JsonNode> request(String type, ObjectNode payload) {
    if (!open) return CompletableFuture.failedFuture(new IOException("TCP connection is not open"));
    String requestId = UUID.randomUUID().toString();
    ObjectNode request = ProtocolCodec.JSON.createObjectNode();
    request.put("type", type).put("requestId", requestId).set("payload", payload);
    CompletableFuture<JsonNode> future = new CompletableFuture<>();
    pending.put(requestId, future);
    try {
      synchronized (sendLock) {
        ProtocolCodec.write(output, request);
      }
    } catch (IOException exception) {
      pending.remove(requestId);
      future.completeExceptionally(exception);
      close();
    }
    return future.orTimeout(8, TimeUnit.SECONDS);
  }

  public void addEventListener(Consumer<JsonNode> listener) {
    eventListeners.add(listener);
  }

  public boolean isOpen() {
    return open;
  }

  private void readLoop() {
    try {
      while (open) {
        JsonNode message = ProtocolCodec.read(input);
        String requestId = message.path("requestId").asText(null);
        CompletableFuture<JsonNode> future = requestId == null ? null : pending.remove(requestId);
        if (future != null) future.complete(message);
        else eventListeners.forEach(listener -> listener.accept(message));
      }
    } catch (IOException exception) {
      if (open) eventListeners.forEach(listener -> listener.accept(connectionError(exception)));
    } finally {
      close();
    }
  }

  private ObjectNode connectionError(Exception exception) {
    ObjectNode event = ProtocolCodec.JSON.createObjectNode();
    return event.put("type", "CONNECTION_ERROR").put("message", rootMessage(exception));
  }

  private static String rootMessage(Throwable throwable) {
    while (throwable.getCause() != null) throwable = throwable.getCause();
    return throwable.getMessage() == null
        ? throwable.getClass().getSimpleName()
        : throwable.getMessage();
  }

  @Override
  public void close() {
    if (!open && socket == null) return;
    open = false;
    if (keepAlive != null) keepAlive.shutdownNow();
    try {
      if (socket != null) socket.close();
    } catch (IOException ignored) {
    }
    IOException closed = new IOException("TCP connection closed");
    pending.values().forEach(future -> future.completeExceptionally(closed));
    pending.clear();
    socket = null;
  }
}
