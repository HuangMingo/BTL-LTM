package vn.edu.ptit.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class ChatApplication extends Application {
  private static final ObjectNode EMPTY = ProtocolCodec.JSON.createObjectNode();
  private final TcpChatClient client = new TcpChatClient();
  private final DateTimeFormatter clock =
      DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
  private Stage stage;
  private boolean registerMode;
  private JsonNode currentUser;
  private VBox peopleBox;
  private VBox messagesBox;
  private ScrollPane messageScroll;
  private Label connectionLabel;

  @Override
  public void start(Stage primaryStage) {
    stage = primaryStage;
    stage.setTitle("Loop — TCP Chat");
    stage.setMinWidth(980);
    stage.setMinHeight(650);
    stage.setOnCloseRequest(event -> client.close());
    client.addEventListener(message -> Platform.runLater(() -> handleEvent(message)));
    showAuth("Đang kết nối TCP…", true);
    String host = environment("CLIENT_TARGET_HOST", "localhost");
    int port = Integer.parseInt(environment("CLIENT_TARGET_PORT", "5000"));
    client
        .connect(host, port)
        .whenComplete(
            (ignored, error) ->
                Platform.runLater(
                    () -> {
                      if (error == null) showAuth("Đã kết nối tới " + host + ":" + port, false);
                      else showAuth("Không thể kết nối: " + rootMessage(error), false);
                    }));
    stage.show();
  }

  private void showAuth(String status, boolean connecting) {
    HBox root = new HBox();
    root.getStyleClass().add("root");
    VBox brand = new VBox();
    brand.getStyleClass().add("brand");
    brand.setPrefWidth(520);
    HBox.setHgrow(brand, Priority.ALWAYS);
    Label logo = label("∞  loop", "logo");
    VBox story =
        new VBox(
            18,
            label("SCP / 1.0  •  JAVA TCP SOCKET", "eyebrow"),
            label("Những cuộc trò chuyện\nthật sự thuộc về bạn.", "hero"),
            label(
                "Ứng dụng desktop trò chuyện thời gian thực\n"
                    + "qua giao thức TCP do chính nhóm thiết kế.",
                "brand-copy"));
    VBox.setVgrow(story, Priority.ALWAYS);
    story.setAlignment(Pos.CENTER_LEFT);
    Label protocolStatus = label("●  " + status, "brand-copy");
    brand.getChildren().addAll(logo, story, protocolStatus);

    VBox pane = new VBox(14);
    pane.getStyleClass().add("auth-pane");
    pane.setPrefWidth(500);
    pane.setAlignment(Pos.CENTER_LEFT);
    Label title = label(registerMode ? "Tạo tài khoản mới" : "Chào mừng trở lại", "title");
    Label subtitle =
        label(
            registerMode
                ? "Chỉ cần email và mật khẩu để bắt đầu."
                : "Đăng nhập để tiếp tục cuộc trò chuyện.",
            "muted");
    Label emailLabel = label("Email", "field-label");
    TextField email = new TextField();
    email.setPromptText("ban@example.com");
    email.setMaxWidth(Double.MAX_VALUE);
    Label passwordLabel = label("Mật khẩu", "field-label");
    PasswordField password = new PasswordField();
    password.setPromptText("Tối thiểu 8 ký tự");
    password.setMaxWidth(Double.MAX_VALUE);
    Label error = label("", "error");
    error.setVisible(false);
    error.setManaged(false);
    error.setWrapText(true);
    Button submit = new Button(registerMode ? "Đăng ký & đăng nhập  →" : "Đăng nhập  →");
    submit.getStyleClass().add("primary");
    submit.setMaxWidth(Double.MAX_VALUE);
    submit.setDisable(connecting || !client.isOpen());
    Button toggle =
        new Button(registerMode ? "Đã có tài khoản?  Đăng nhập" : "Chưa có tài khoản?  Đăng ký");
    toggle.getStyleClass().add("link");
    toggle.setOnAction(
        event -> {
          registerMode = !registerMode;
          showAuth(status, false);
        });
    Runnable authenticate = () -> authenticate(email.getText(), password.getText(), submit, error);
    submit.setOnAction(event -> authenticate.run());
    password.setOnAction(event -> authenticate.run());
    Region gap = new Region();
    gap.setMinHeight(10);
    pane.getChildren()
        .addAll(
            title,
            subtitle,
            gap,
            emailLabel,
            email,
            passwordLabel,
            password,
            error,
            submit,
            toggle);
    root.getChildren().addAll(brand, pane);
    setScene(root);
  }

  private void authenticate(String email, String password, Button submit, Label error) {
    if (email == null || email.isBlank() || password == null || password.length() < 8) {
      showError(error, "Email hợp lệ và mật khẩu tối thiểu 8 ký tự là bắt buộc.");
      return;
    }
    submit.setDisable(true);
    error.setVisible(false);
    error.setManaged(false);
    ObjectNode credentials =
        ProtocolCodec.JSON.createObjectNode().put("email", email).put("password", password);
    CompletableFuture<JsonNode> flow =
        registerMode
            ? expectOk("REGISTER", credentials.deepCopy())
                .thenCompose(ignored -> expectOk("LOGIN", credentials))
            : expectOk("LOGIN", credentials);
    flow.whenComplete(
        (response, failure) ->
            Platform.runLater(
                () -> {
                  submit.setDisable(false);
                  if (failure != null) showError(error, rootMessage(failure));
                  else {
                    currentUser = response.path("data");
                    registerMode = false;
                    showChat();
                    refreshUsers();
                    refreshMessages();
                  }
                }));
  }

  private void showChat() {
    BorderPane root = new BorderPane();
    root.getStyleClass().add("root");
    VBox sidebar = new VBox(10);
    sidebar.getStyleClass().add("sidebar");
    Label logo = label("∞  loop", "logo");
    HBox profile = person(currentUser.path("email").asText(), true);
    profile.getStyleClass().add("profile");
    HBox onlineHeading = new HBox(label("ĐANG TRỰC TUYẾN", "side-heading"));
    onlineHeading.setPadding(new Insets(10, 4, 0, 4));
    peopleBox = new VBox(2);
    ScrollPane peopleScroll = new ScrollPane(peopleBox);
    peopleScroll.setFitToWidth(true);
    peopleScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
    VBox.setVgrow(peopleScroll, Priority.ALWAYS);
    Button logout = new Button("↪  Đăng xuất");
    logout.getStyleClass().add("logout");
    logout.setMaxWidth(Double.MAX_VALUE);
    logout.setOnAction(
        event ->
            expectOk("LOGOUT", EMPTY.deepCopy())
                .whenComplete(
                    (ignored, failure) ->
                        Platform.runLater(
                            () -> {
                              currentUser = null;
                              showAuth(
                                  failure == null ? "Đã kết nối TCP" : rootMessage(failure), false);
                            })));
    sidebar.getChildren().addAll(logo, profile, onlineHeading, peopleScroll, logout);

    BorderPane conversation = new BorderPane();
    HBox header = new HBox();
    header.getStyleClass().add("chat-header");
    header.setAlignment(Pos.CENTER_LEFT);
    VBox heading =
        new VBox(2, label("KÊNH CHUNG", "eyebrow"), label("Phòng trò chuyện", "chat-title"));
    Region headerGap = new Region();
    HBox.setHgrow(headerGap, Priority.ALWAYS);
    connectionLabel = label("●  Đã kết nối TCP", "online");
    header.getChildren().addAll(heading, headerGap, connectionLabel);
    messagesBox = new VBox(18);
    messagesBox.getStyleClass().add("message-list");
    messageScroll = new ScrollPane(messagesBox);
    messageScroll.getStyleClass().add("message-scroll");
    messageScroll.setFitToWidth(true);
    messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    HBox composer = new HBox(10);
    composer.getStyleClass().add("composer");
    TextField message = new TextField();
    message.setPromptText("Nhập tin nhắn...");
    HBox.setHgrow(message, Priority.ALWAYS);
    Button send = new Button("GỬI  ➤");
    send.getStyleClass().add("send");
    Runnable sendMessage =
        () -> {
          String content = message.getText().trim();
          if (content.isEmpty()) return;
          message.clear();
          ObjectNode payload = ProtocolCodec.JSON.createObjectNode().put("content", content);
          expectOk("SEND_MESSAGE", payload)
              .exceptionally(
                  error -> {
                    Platform.runLater(
                        () -> {
                          message.setText(content);
                          connectionLabel.setText(rootMessage(error));
                        });
                    return null;
                  });
        };
    send.setOnAction(event -> sendMessage.run());
    message.setOnAction(event -> sendMessage.run());
    composer.getChildren().addAll(message, send);
    conversation.setTop(header);
    conversation.setCenter(messageScroll);
    conversation.setBottom(composer);
    root.setLeft(sidebar);
    root.setCenter(conversation);
    setScene(root);
  }

  private void refreshUsers() {
    if (currentUser == null) return;
    expectOk("LIST_USERS", EMPTY.deepCopy())
        .thenAccept(
            response ->
                Platform.runLater(
                    () -> {
                      peopleBox.getChildren().clear();
                      response
                          .path("data")
                          .path("users")
                          .forEach(
                              user ->
                                  peopleBox
                                      .getChildren()
                                      .add(person(user.path("email").asText(), false)));
                    }));
  }

  private void refreshMessages() {
    ObjectNode payload = ProtocolCodec.JSON.createObjectNode().put("limit", 50);
    expectOk("LIST_MESSAGES", payload)
        .thenAccept(
            response ->
                Platform.runLater(
                    () -> {
                      messagesBox.getChildren().clear();
                      response.path("data").path("messages").forEach(this::appendMessage);
                      scrollToBottom();
                    }));
  }

  private void handleEvent(JsonNode message) {
    switch (message.path("type").asText()) {
      case "CHAT_MESSAGE" -> {
        if (currentUser != null) {
          appendMessage(message.path("payload"));
          scrollToBottom();
        }
      }
      case "USER_JOINED", "USER_LEFT" -> refreshUsers();
      case "CONNECTION_ERROR" -> {
        if (connectionLabel != null) connectionLabel.setText("●  Mất kết nối TCP");
      }
      default -> {}
    }
  }

  private void appendMessage(JsonNode message) {
    boolean mine =
        currentUser != null
            && message.path("senderUid").asText().equals(currentUser.path("uid").asText());
    String email = message.path("email").asText();
    String sentAt = message.path("sentAt").asText();
    String at;
    try {
      at = clock.format(Instant.parse(sentAt));
    } catch (Exception ignored) {
      at = "";
    }
    Label meta =
        label(
            (mine ? "Bạn" : email.substring(0, email.indexOf('@'))) + "  •  " + at, "message-meta");
    Label content =
        label(message.path("content").asText(), mine ? "bubble-text-mine" : "bubble-text");
    content.setWrapText(true);
    content.setMaxWidth(540);
    VBox bubble = new VBox(5, meta, content);
    bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble");
    HBox row = new HBox(bubble);
    row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    row.setPadding(new Insets(0, mine ? 0 : 40, 0, mine ? 40 : 0));
    messagesBox.getChildren().add(row);
  }

  private HBox person(String email, boolean current) {
    String initials = email.substring(0, Math.min(2, email.length())).toUpperCase();
    Label avatar = label(initials, "avatar");
    Label name =
        label(email.substring(0, email.indexOf('@')), current ? "profile-name" : "person-name");
    Label detail = label(email, current ? "profile-email" : "person-email");
    VBox info = new VBox(2, name, detail);
    HBox row = new HBox(10, avatar, info);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("person");
    return row;
  }

  private CompletableFuture<JsonNode> expectOk(String type, ObjectNode payload) {
    return client
        .request(type, payload)
        .thenCompose(
            response ->
                response.path("status").asInt(500) < 400
                    ? CompletableFuture.completedFuture(response)
                    : CompletableFuture.failedFuture(
                        new IOException(
                            response.path("message").asText("Server rejected request"))));
  }

  private void setScene(Pane root) {
    Scene scene = new Scene(root, 1180, 760);
    scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
    stage.setScene(scene);
  }

  private void scrollToBottom() {
    Platform.runLater(() -> messageScroll.setVvalue(1.0));
  }

  private static Label label(String text, String style) {
    Label label = new Label(text);
    label.getStyleClass().add(style);
    return label;
  }

  private static void showError(Label label, String message) {
    label.setText(message);
    label.setVisible(true);
    label.setManaged(true);
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String rootMessage(Throwable throwable) {
    while (throwable.getCause() != null) throwable = throwable.getCause();
    return throwable.getMessage() == null
        ? throwable.getClass().getSimpleName()
        : throwable.getMessage();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
