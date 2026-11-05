package vn.edu.ptit.chat;

import java.nio.charset.StandardCharsets;
import java.security.*;

public final class PasswordHasher {
  private PasswordHasher() {}

  public static String sha256(String password) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(password.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static boolean matches(String password, String expectedHex) {
    return MessageDigest.isEqual(
        sha256(password).getBytes(StandardCharsets.US_ASCII),
        expectedHex.getBytes(StandardCharsets.US_ASCII));
  }
}
