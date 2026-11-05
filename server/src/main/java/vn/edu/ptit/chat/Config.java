package vn.edu.ptit.chat;

public record Config(
    String host,
    int port,
    int maxClients,
    int idleTimeoutSeconds,
    String databaseUrl,
    String databaseUser,
    String databasePassword) {
  public static Config fromEnvironment() {
    return new Config(
        env("SERVER_HOST", "0.0.0.0"),
        integer("SERVER_PORT", 5000),
        integer("MAX_CLIENTS", 100),
        integer("IDLE_TIMEOUT_SECONDS", 90),
        env("DATABASE_URL", "jdbc:postgresql://localhost:5432/LTM_db"),
        env("DATABASE_USER", "tcp_chat"),
        env("DATABASE_PASSWORD", "tcp_chat_dev"));
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static int integer(String name, int fallback) {
    try {
      return Integer.parseInt(env(name, String.valueOf(fallback)));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
