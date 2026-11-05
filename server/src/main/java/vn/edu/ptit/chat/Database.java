package vn.edu.ptit.chat;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class Database {
  private final Config config;

  public Database(Config config) {
    this.config = config;
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(
        config.databaseUrl(), config.databaseUser(), config.databasePassword());
  }

  public void migrate() throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE IF NOT EXISTS users (uid UUID PRIMARY KEY, email VARCHAR(254) NOT NULL"
              + " UNIQUE, password_hash CHAR(64) NOT NULL)");
      statement.executeUpdate(
          "CREATE TABLE IF NOT EXISTS messages (message_id UUID PRIMARY KEY, sender_uid UUID NOT"
              + " NULL REFERENCES users(uid) ON DELETE CASCADE, content VARCHAR(2000) NOT NULL,"
              + " created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS messages_created_at_idx ON messages(created_at DESC)");
    }
  }

  public User createUser(String email, String hash) throws SQLException {
    UUID uid = UUID.randomUUID();
    try (Connection c = connect();
        PreparedStatement q = c.prepareStatement("INSERT INTO users(uid,email,password_hash) VALUES (?,?,?)")) {
      q.setObject(1, uid);
      q.setString(2, email);
      q.setString(3, hash);
      q.executeUpdate();
    }
    return new User(uid, email);
  }

  public Optional<Credentials> findCredentials(String email) throws SQLException {
    Connection c = connect();
    PreparedStatement q = c.prepareStatement("SELECT uid,email,password_hash FROM users WHERE email=?");
    q.setString(1, email);
    ResultSet r = q.executeQuery();
    return r.next()
        ? Optional.of(
            new Credentials(
                r.getObject("uid", UUID.class),
                r.getString("email"),
                r.getString("password_hash")))
        : Optional.empty();
  }

  public Message saveMessage(UUID senderUid, String email, String content) throws SQLException {
    UUID id = UUID.randomUUID();
    Instant created = Instant.now();
    try (Connection c = connect();
        PreparedStatement q = c.prepareStatement(
            "INSERT INTO messages(message_id,sender_uid,content,created_at) VALUES"
                + " (?,?,?,?)")) {
      q.setObject(1, id);
      q.setObject(2, senderUid);
      q.setString(3, content);
      q.setTimestamp(4, Timestamp.from(created));
      q.executeUpdate();
    }
    return new Message(id, senderUid, email, content, created);
  }

  public List<Message> latestMessages(int limit) throws SQLException {
    List<Message> result = new ArrayList<>();
    try (Connection c = connect();
        PreparedStatement q = c.prepareStatement(
            "SELECT m.message_id,m.sender_uid,u.email,m.content,m.created_at FROM messages m"
                + " JOIN users u ON u.uid=m.sender_uid ORDER BY m.created_at DESC LIMIT ?")) {
      q.setInt(1, Math.min(Math.max(limit, 1), 100));
      try (ResultSet r = q.executeQuery()) {
        while (r.next())
          result.add(
              new Message(
                  r.getObject("message_id", UUID.class),
                  r.getObject("sender_uid", UUID.class),
                  r.getString("email"),
                  r.getString("content"),
                  r.getTimestamp("created_at").toInstant()));
      }
    }
    Collections.reverse(result);
    return result;
  }

  public record User(UUID uid, String email) {
  }

  public record Credentials(UUID uid, String email, String passwordHash) {
  }

  public record Message(
      UUID messageId, UUID senderUid, String email, String content, Instant createdAt) {
  }
}
