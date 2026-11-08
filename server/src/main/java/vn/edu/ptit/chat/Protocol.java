
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

    public static JsonNode read(DataInputStream input)
            throws IOException, ProtocolException {

        int frameLength = input.readInt();

        if (frameLength < 1 || frameLength > MAX_FRAME_BYTES) {
            throw new ProtocolException(
                    413,
                    "Frame length must be between 1 and " + MAX_FRAME_BYTES,
                    true
            );
        }

        byte[] frameBody = new byte[frameLength];
        input.readFully(frameBody);

        try {
            String jsonText = new String(
                    frameBody,
                    StandardCharsets.UTF_8
            );

            JsonNode message = JSON.readTree(jsonText);

            if (!message.isObject()) {
                throw new IOException();
            }

            return message;

        } catch (Exception exception) {
            throw new ProtocolException(
                    400,
                    "Malformed JSON message",
                    false
            );
        }
    }

    public static void write(
            DataOutputStream output,
            JsonNode message
    ) throws IOException {

        byte[] encodedMessage = JSON.writeValueAsBytes(message);
        int messageLength = encodedMessage.length;

        if (messageLength > MAX_FRAME_BYTES) {
            throw new IOException("Outgoing frame too large");
        }

        output.writeInt(messageLength);
        output.write(encodedMessage);
        output.flush();
    }

    public static ObjectNode response(
            String type,
            String requestId,
            int status,
            String message
    ) {

        ObjectNode responseNode = JSON.createObjectNode();

        responseNode.put("type", type);

        if (requestId != null) {
            responseNode.put("requestId", requestId);
        }

        responseNode.put("status", status);
        responseNode.put("message", message);

        return responseNode;
    }

    public static ObjectNode event(String type) {

        ObjectNode eventNode = JSON.createObjectNode();

        String eventId = UUID.randomUUID().toString();

        eventNode.put("type", type);
        eventNode.put("eventId", eventId);

        return eventNode;
    }

    public static final class ProtocolException extends Exception {

        public final int status;
        public final boolean fatal;

        public ProtocolException(
                int status,
                String message,
                boolean fatal
        ) {
            super(message);

            this.status = status;
            this.fatal = fatal;
        }
    }
}
