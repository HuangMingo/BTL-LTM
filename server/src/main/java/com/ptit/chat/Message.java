package com.ptit.chat;

public class Message {
    public String type;
    public String requestId;
    public String nickname; // Dùng cho HELLO
    public String content;  // Dùng cho tin nhắn hoặc thông báo lỗi
    public String room;     // Dùng cho JOIN_ROOM, ROOM_MESSAGE
    public String to;       // Dùng cho PRIVATE_MESSAGE
    public String from;     // Server sẽ điền vào khi broadcast
}