package vn.edu.ptit.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import org.junit.jupiter.api.Test;

class ProtocolTest {
  @Test
  void roundTripsLengthPrefixedUtf8Json() throws Exception {
    ObjectNode request = Protocol.JSON.createObjectNode();
    request
        .put("type", "PING")
        .put("requestId", "r-1")
        .putObject("payload")
        .put("text", "Xin chào");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Protocol.write(new DataOutputStream(bytes), request);
    var decoded = Protocol.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    assertEquals("PING", decoded.path("type").asText());
    assertEquals("Xin chào", decoded.path("payload").path("text").asText());
  }

  @Test
  void rejectsOversizedPrefixBeforeAllocatingBody() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new DataOutputStream(bytes).writeInt(Protocol.MAX_FRAME_BYTES + 1);
    var error =
        assertThrows(
            Protocol.ProtocolException.class,
            () ->
                Protocol.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    assertEquals(413, error.status);
    assertTrue(error.fatal);
  }

  @Test
  void rejectsMalformedJsonWithoutKillingConnectionImmediately() throws Exception {
    byte[] body = "{not-json}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(bytes);
    output.writeInt(body.length);
    output.write(body);
    var error =
        assertThrows(
            Protocol.ProtocolException.class,
            () ->
                Protocol.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    assertEquals(400, error.status);
    assertFalse(error.fatal);
  }
}
