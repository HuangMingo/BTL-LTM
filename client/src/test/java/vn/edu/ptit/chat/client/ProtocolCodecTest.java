package vn.edu.ptit.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
  @Test
  void roundTripsUtf8Frame() throws Exception {
    var source = ProtocolCodec.JSON.createObjectNode().put("content", "Xin chào JavaFX");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ProtocolCodec.write(new DataOutputStream(bytes), source);
    var decoded =
        ProtocolCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    assertEquals("Xin chào JavaFX", decoded.path("content").asText());
  }
}
