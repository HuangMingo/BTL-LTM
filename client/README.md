# JavaFX Desktop Client

Native JavaFX client connects directly to the Java TCP server using SCP/1.0. There is no HTTP/WebSocket gateway.

## Run

Prerequisites: JDK 21+ and Maven 3.9+.

```powershell
$env:CLIENT_TARGET_HOST = "localhost"
$env:CLIENT_TARGET_PORT = "5000"
mvn javafx:run
```

Start multiple terminals to demonstrate concurrent users. Main modules:

- `ProtocolCodec`: 4-byte big-endian framing and UTF-8 JSON.
- `TcpChatClient`: socket lifecycle, reader thread, request correlation and server events.
- `ChatApplication`: auth/chat JavaFX screens and Application Thread coordination.
- `app.css`: Figma-inspired forest/lime design tokens and components.

The client never connects to PostgreSQL and never hashes passwords. Those responsibilities stay on the server.
