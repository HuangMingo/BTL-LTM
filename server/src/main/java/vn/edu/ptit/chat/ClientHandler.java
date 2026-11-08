package vn.edu.ptit.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientHandler implements Runnable {
  private enum State {
    CONNECTED,
    READY,
    AUTHENTICATED
  }

  private final Socket socket;
  private final Config config;
  private final Database database;
  private final SessionManager sessions;
  private final Object sendLock = new Object();
  private final AtomicBoolean closed = new AtomicBoolean(); // ghi nhận kết nối của ClientHandler đã đóng chưa, ban đầu
                                                            // có giá trị là false
  private DataInputStream input;
  private DataOutputStream output;
  private State state = State.CONNECTED;
  private int strikes; // số lần client gửi request hoặc dữ liệu không hợp lệ
  private UUID uid;
  private String email;

  public ClientHandler(Socket s, Config c, Database d, SessionManager m) {
    this.socket = s;
    this.config = c;
    this.database = d;
    this.sessions = m;
  }

  public void run() {
    String reason = "disconnect";
    try (socket) {
      socket.setSoTimeout(config.idleTimeoutSeconds() * 1000);
      input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
      while (!closed.get())
        try {
          dispatch(Protocol.read(input));
        } catch (Protocol.ProtocolException e) {
          send(response("ERROR", null, e.status, e.getMessage()));
          if (e.fatal || ++strikes >= 3) {
            reason = "protocol_error";
            break;
          }
        }
    } catch (EOFException e) {
      reason = "eof";
    } catch (SocketTimeoutException e) {
      reason = "timeout";
    } catch (IOException e) {
      reason = "network_error";
    } finally {
      cleanup(reason);
    }
  }

  // đóng vai trò điều hướng
  private void dispatch(JsonNode request) throws IOException {
    String command = text(request, "type");
    String requestId = text(request, "requestId");
    JsonNode payload = request.path("payload");

    boolean invalidRequest = command == null
        || requestId == null
        || requestId.isBlank()
        || requestId.length() > 64
        || !payload.isObject();

    if (invalidRequest) {
      invalid(
          requestId,
          "type, requestId and object payload are required");
      return;
    }

    switch (command) {

      case "HELLO" -> hello(requestId, payload);

      case "REGISTER" -> register(requestId, payload);

      case "LOGIN" -> login(requestId, payload);

      case "SEND_MESSAGE" -> message(requestId, payload);

      case "LIST_USERS" -> listUsers(requestId);

      case "LIST_MESSAGES" -> listMessages(requestId, payload);

      case "LOGOUT" -> logout(requestId, "logout");

      case "PING" -> {
        send(response(
            "PONG",
            requestId,
            200,
            "Connection alive"));
      }

      case "QUIT" -> {
        send(response(
            "QUIT_ACK",
            requestId,
            200,
            "Connection closed"));

        cleanup("quit");
      }

      default -> {
        invalid(requestId, "Unknown command: " + command);
      }
    }
  }

  private void hello(String id, JsonNode p) throws IOException {
    if (state != State.CONNECTED) {
      invalid(id, "HELLO is only valid after connect");
      return;
    }
    if (!"SCP/1.0".equals(text(p, "protocolVersion"))) {
      send(response("HELLO_RESULT", id, 426, "Unsupported protocol version"));
      cleanup("bad_version");
      return;
    }
    state = State.READY;
    send(response("HELLO_RESULT", id, 200, "SCP/1.0 ready"));
  }

  private void register(String id, JsonNode p) throws IOException {
    if (!require(State.READY, id))
      return;
    String mail = normalize(text(p, "email")),
        pass = text(p, "password"),
        error = validate(mail, pass);
    if (error != null) {
      send(response("REGISTER_RESULT", id, 422, error));
      return;
    }
    try {
      var user = database.createUser(mail, PasswordHasher.sha256(pass));
      ObjectNode out = response("REGISTER_RESULT", id, 201, "Account created");
      out.putObject("data").put("uid", user.uid().toString()).put("email", user.email());
      send(out);
    } catch (SQLException e) {
      if ("23505".equals(e.getSQLState()))
        send(response("REGISTER_RESULT", id, 409, "Email already exists"));
      else
        serverError(id, "REGISTER_RESULT", e);
    }
  }

  private void login(String id, JsonNode p) throws IOException {
    if (!require(State.READY, id))
      return;
    String mail = normalize(text(p, "email")), pass = text(p, "password");
    if (mail == null || pass == null) {
      send(response("LOGIN_RESULT", id, 422, "Email and password are required"));
      return;
    }
    try {
      var found = database.findCredentials(mail);
      if (found.isEmpty() || !PasswordHasher.matches(pass, found.get().passwordHash())) {
        send(response("LOGIN_RESULT", id, 401, "Invalid email or password"));
        return;
      }
      var user = found.get();
      if (!sessions.login(user.uid(), this)) {
        send(response("LOGIN_RESULT", id, 409, "Account is already online"));
        return;
      }
      uid = user.uid();
      email = user.email();
      state = State.AUTHENTICATED;
      ObjectNode out = response("LOGIN_RESULT", id, 200, "Login successful");
      out.putObject("data").put("uid", uid.toString()).put("email", email);
      send(out);
      ObjectNode event = Protocol.event("USER_JOINED");
      event.putObject("payload").put("uid", uid.toString()).put("email", email);
      sessions.broadcast(event, this);
    } catch (SQLException e) {
      serverError(id, "LOGIN_RESULT", e);
    }
  }

  private void message(String requestId, JsonNode payload)
      throws IOException {

    if (!require(State.AUTHENTICATED, requestId)) {
      return;
    }

    String content = text(payload, "content");

    if (content != null) {
      content = content.trim();
    }

    boolean invalidContent = content == null
        || content.isEmpty()
        || content.codePointCount(0, content.length()) > 2000;

    if (invalidContent) {
      send(response(
          "SEND_MESSAGE_RESULT",
          requestId,
          422,
          "Content must contain 1-2000 characters"));
      return;
    }

    try {
      var savedMessage = database.saveMessage(
          uid,
          email,
          content);

      ObjectNode result = response(
          "SEND_MESSAGE_RESULT",
          requestId,
          200,
          "Message sent");

      send(result);

      ObjectNode chatEvent = Protocol.event("CHAT_MESSAGE");
      chatEvent.set("payload", messageJson(savedMessage));

      sessions.broadcast(chatEvent, null);

    } catch (SQLException exception) {
      serverError(
          requestId,
          "SEND_MESSAGE_RESULT",
          exception);
    }
  }

  private void listUsers(String id) throws IOException {
    if (!require(State.AUTHENTICATED, id))
      return;
    ObjectNode out = response("LIST_USERS_RESULT", id, 200, "Online users");
    out.putObject("data").set("users", sessions.users());
    send(out);
  }

  private void listMessages(String id, JsonNode p) throws IOException {
    if (!require(State.AUTHENTICATED, id))
      return;
    try {
      ArrayNode items = Protocol.JSON.createArrayNode();
      database.latestMessages(p.path("limit").asInt(50)).forEach(m -> items.add(messageJson(m)));
      ObjectNode out = response("LIST_MESSAGES_RESULT", id, 200, "Message history");
      out.putObject("data").set("messages", items);
      send(out);
    } catch (SQLException e) {
      serverError(id, "LIST_MESSAGES_RESULT", e);
    }
  }

  private ObjectNode messageJson(Database.Message m) {
    return Protocol.JSON
        .createObjectNode()
        .put("messageId", m.messageId().toString())
        .put("senderUid", m.senderUid().toString())
        .put("email", m.email())
        .put("content", m.content())
        .put("sentAt", m.createdAt().toString());
  }

  private void logout(String id, String why) throws IOException {
    if (!require(State.AUTHENTICATED, id))
      return;
    UUID old = uid;
    String mail = email;
    sessions.logout(old, this);
    uid = null;
    email = null;
    state = State.READY;
    send(response("LOGOUT_RESULT", id, 200, "Logged out"));
    left(old, mail, why);
  }

  private boolean require(State expected, String id) throws IOException {
    if (state == expected)
      return true;
    send(
        response(
            "ERROR",
            id,
            state == State.AUTHENTICATED ? 400 : 401,
            "Command is not valid in current state"));
    return false;
  }

  private void invalid(String id, String message) throws IOException {
    strikes++;
    send(response("ERROR", id, 400, message));
    if (strikes >= 3)
      cleanup("protocol_error");
  }

  private ObjectNode response(String type, String id, int status, String message) {
    return Protocol.response(type, id, status, message);
  }

  private void serverError(String id, String type, SQLException e) throws IOException {
    System.err.println("Database operation failed: SQLState=" + e.getSQLState());
    send(response(type, id, 500, "Internal server error"));
  }

  // lấy giá trị của field trong jsons
  private static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v != null && v.isTextual() ? v.asText() : null;
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static String validate(String mail, String pass) {
    if (mail == null || mail.length() > 254 || !mail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
      return "A valid email is required";
    int bytes = pass == null ? 0 : pass.getBytes(StandardCharsets.UTF_8).length;
    return bytes < 8 || bytes > 72 ? "Password must contain 8-72 UTF-8 bytes" : null;
  }

  public void send(ObjectNode m) throws IOException {
    synchronized (sendLock) {
      if (!closed.get())
        Protocol.write(output, m);
    }
  }

  public void trySend(ObjectNode m) {
    try {
      send(m);
    } catch (IOException e) {
      cleanup("write_error");
    }
  }

  public UUID uid() {
    return uid;
  }

  public String email() {
    return email == null ? "" : email;
  }

  private void cleanup(String why) {
    if (!closed.compareAndSet(false, true))
      return;
    UUID old = uid;
    String mail = email;
    if (sessions.logout(old, this))
      left(old, mail, why);
    try {
      socket.close();
    } catch (IOException ignored) {
    }
  }

  private void left(UUID old, String mail, String why) {
    if (old == null)
      return;
    ObjectNode event = Protocol.event("USER_LEFT");
    event.putObject("payload").put("uid", old.toString()).put("email", mail).put("reason", why);
    sessions.broadcast(event, this);
  }
}
