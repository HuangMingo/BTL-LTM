package com.ptit.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    private final int port;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=== SIMPLECHAT SERVER (TCP PURE + 4-BYTE FRAMING) ===");
            System.out.println("Server đang lắng nghe trên cổng " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.start();
                System.out.println("[+] Tạo thread mới. Tổng threads: " + Thread.activeCount());
            }
        } catch (IOException e) {
            System.err.println("Lỗi server: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "5000"));
        new ChatServer(port).start();
    }
}