package vn.edu.ptit.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
  @Test
  void sha256IsDeterministic() {
    assertEquals(
        "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b",
        PasswordHasher.sha256("secret"));
    assertTrue(PasswordHasher.matches("secret", PasswordHasher.sha256("secret")));
    assertFalse(PasswordHasher.matches("wrong", PasswordHasher.sha256("secret")));
  }
}
