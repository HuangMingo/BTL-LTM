package vn.edu.ptit.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ProtocolCodec {
  public static final int MAX_FRAME_BYTES = 65_536;
  public static final ObjectMapper JSON = new ObjectMapper();

  private ProtocolCodec() {}

  public static JsonNode read(DataInputStream input) throws IOException {
    int length = input.readInt();
    if (length < 1 || length > MAX_FRAME_BYTES)
      throw new IOException("Invalid frame length: " + length);
    byte[] body = new byte[length];
    input.readFully(body);
    return JSON.readTree(body);
  }

  public static void write(DataOutputStream output, JsonNode message) throws IOException {
    byte[] body = JSON.writeValueAsBytes(message);
    if (body.length > MAX_FRAME_BYTES) throw new IOException("Message exceeds protocol limit");
    output.writeInt(body.length);
    output.write(body);
    output.flush();
  }
}
