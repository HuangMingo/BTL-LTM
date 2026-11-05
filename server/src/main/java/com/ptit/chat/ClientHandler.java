package com.ptit.chat;

import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nickname;
    private final Object writeLock = new Object();
    private final Gson gson = new Gson();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public String getNickname() { return nickname; }

    @Override
    public void run() {
        try {
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
            System.out.println("[+] Client kết nối: " + clientSocket.getInetAddress());

            while (true) {
                int length = in.readInt();
                if (length <= 0 || length > 65536) {
                    System.out.println("[-] Frame length không hợp lệ: " + length);
                    break;
                }
                byte[] data = new byte[length];
                in.readFully(data);
                String json = new String(data, "UTF-8");
                Message msg = gson.fromJson(json, Message.class);
                handleCommand(msg);
            }
        } catch (EOFException e) {
            System.out.println("[-] Client ngắt kết nối đột ngột.");
        } catch (IOException e) {
            System.err.println("[-] Lỗi IO: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleCommand(Message msg) throws IOException {
        if (msg.type == null) return;
        ChatState state = ChatState.getInstance();

        switch (msg.type) {
            case "HELLO":
                // Xử lý an toàn: nếu gateway không gửi 'nickname' ở root, dùng tên tạm
                String tempName = (msg.nickname != null) ? msg.nickname : "gateway-client";
                
                if (state.addUser(tempName, this)) {
                    this.nickname = tempName;
                    sendResponse("HELLO_RESULT", "Chào mừng " + this.nickname);
                } else {
                    sendResponse("ERROR", "Client đã tồn tại");
                }
                break;
            case "JOIN_ROOM":
                if (nickname == null) {
                    sendResponse("ERROR", "Chưa đăng ký nickname");
                    break;
                }
                state.joinRoom(nickname, msg.room);
                sendResponse("JOINED", "Đã vào phòng " + msg.room);
                
                Message notify = new Message();
                notify.type = "USER_ONLINE";
                notify.nickname = nickname;
                notify.room = msg.room;
                state.broadcastToRoom(msg.room, notify, nickname);
                break;
            case "ROOM_MESSAGE":
                if (nickname != null && msg.room != null) {
                    msg.from = nickname;
                    state.broadcastToRoom(msg.room, msg, nickname);
                }
                break;
            default:
                sendResponse("ERROR", "Lệnh không hợp lệ: " + msg.type);
        }
    }

    public void sendMessage(Message msg) {
        synchronized (writeLock) {
            try {
                String json = gson.toJson(msg);
                byte[] data = json.getBytes("UTF-8");
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            } catch (IOException e) {
                System.err.println("[-] Lỗi gửi dữ liệu đến " + nickname);
            }
        }
    }

    private void sendResponse(String type, String message) throws IOException {
        Message res = new Message();
        res.type = type;
        res.content = message;
        sendMessage(res);
    }

    private void cleanup() {
        if (nickname != null) {
            ChatState.getInstance().removeUser(nickname);
            System.out.println("[-] User " + nickname + " đã ngắt kết nối.");
        }
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException e) {}
    }
}