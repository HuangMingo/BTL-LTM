package vn.edu.ptit.chat;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
  private final ConcurrentHashMap<UUID, ClientHandler> online = new ConcurrentHashMap<>();

  public boolean login(UUID uid, ClientHandler handler) {
    return online.putIfAbsent(uid, handler) == null;
  }

  public boolean logout(UUID uid, ClientHandler expected) {
    return uid != null && online.remove(uid, expected);
  }

  public ArrayNode users() {
    ArrayNode result = Protocol.JSON.createArrayNode();
    online.values().stream()
        .sorted(Comparator.comparing(ClientHandler::email))
        .forEach(
            h -> {
              ObjectNode u = result.addObject();
              u.put("uid", h.uid().toString());
              u.put("email", h.email());
            });
    return result;
  }

  public void broadcast(ObjectNode event, ClientHandler except) {
    for (ClientHandler handler : List.copyOf(online.values()))
      if (handler != except) handler.trySend(event);
  }
}
