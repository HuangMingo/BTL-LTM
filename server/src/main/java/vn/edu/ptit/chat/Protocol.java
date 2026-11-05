package vn.edu.ptit.chat;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Protocol {
  public static final int MAX_FRAME_BYTES = 65_536;
  public static final ObjectMapper JSON = new ObjectMapper();

  private Protocol() {}

  public static JsonNode read(DataInputStream input) throws IOException, ProtocolException {
    int length = input.readInt();
    if (length < 1 || length > MAX_FRAME_BYTES)
      throw new ProtocolException(
          413, "Frame length must be between 1 and " + MAX_FRAME_BYTES, true);
    byte[] body = new byte[length];
    input.readFully(body);
    try {
      JsonNode node = JSON.readTree(new String(body, StandardCharsets.UTF_8));
      if (!node.isObject()) throw new IOException();
      return node;
    } catch (Exception exception) {
      throw new ProtocolException(400, "Malformed JSON message", false);
    }
  }

  public static void write(DataOutputStream output, JsonNode message) throws IOException {
    byte[] body = JSON.writeValueAsBytes(message);
    if (body.length > MAX_FRAME_BYTES) throw new IOException("Outgoing frame too large");
    output.writeInt(body.length);
    output.write(body);
    output.flush();
  }

  public static ObjectNode response(String type, String requestId, int status, String message) {
    ObjectNode result = JSON.createObjectNode();
    result.put("type", type);
    if (requestId != null) result.put("requestId", requestId);
    result.put("status", status);
    result.put("message", message);
    return result;
  }

  public static ObjectNode event(String type) {
    ObjectNode result = JSON.createObjectNode();
    result.put("type", type);
    result.put("eventId", UUID.randomUUID().toString());
    return result;
  }

  public static final class ProtocolException extends Exception {
    public final int status;
    public final boolean fatal;

    public ProtocolException(int status, String message, boolean fatal) {
      super(message);
      this.status = status;
      this.fatal = fatal;
    }
  }
}
