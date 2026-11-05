package com.ptit.chat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatState {
    private static final ChatState INSTANCE = new ChatState();
    private final Map<String, ClientHandler> users = new ConcurrentHashMap<>();
    private final Map<String, Set<ClientHandler>> rooms = new ConcurrentHashMap<>();

    private ChatState() {}
    public static ChatState getInstance() { return INSTANCE; }

    public boolean addUser(String nickname, ClientHandler handler) {
        return users.putIfAbsent(nickname, handler) == null;
    }

    public void removeUser(String nickname) {
        ClientHandler handler = users.remove(nickname);
        if (handler != null) {
            for (Set<ClientHandler> roomMembers : rooms.values()) {
                roomMembers.remove(handler);
            }
        }
    }

    public void joinRoom(String nickname, String roomName) {
        ClientHandler handler = users.get(nickname);
        if (handler != null) {
            rooms.computeIfAbsent(roomName, k -> new CopyOnWriteArraySet<>()).add(handler);
        }
    }

    public void broadcastToRoom(String roomName, Message msg, String senderNickname) {
        Set<ClientHandler> members = rooms.get(roomName);
        if (members != null) {
            for (ClientHandler member : members) {
                if (!member.getNickname().equals(senderNickname)) {
                    member.sendMessage(msg);
                }
            }
        }
    }
}