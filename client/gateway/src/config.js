// Đọc cấu hình từ biến môi trường (Docker) hoặc giá trị mặc định
module.exports = {
  // Cổng WebSocket server lắng nghe (browser kết nối vào đây)
  WEB_PORT: parseInt(process.env.WEB_PORT || '8080', 10),
  
  // Địa chỉ TCP server (trong Docker là "server", chạy local là "localhost")
  TCP_HOST: process.env.CLIENT_TARGET_HOST || 'localhost',
  
  // Cổng TCP server
  TCP_PORT: parseInt(process.env.CLIENT_TARGET_PORT || '5000', 10),
  
  // Giới hạn theo đặc tả SCP/1.0
  MAX_FRAME_SIZE: 65536,        // 64 KB
  MAX_CHAT_CONTENT: 2000,       // ký tự Unicode
  PING_INTERVAL_MS: 30000,      // 30 giây
  READ_IDLE_TIMEOUT_MS: 90000,  // 90 giây
  MAX_MALFORMED_STRIKES: 3,     // Đóng connection sau 3 lỗi
  
  // Phiên bản giao thức
  PROTOCOL_VERSION: 'SCP/1.0',
  CLIENT_NAME: 'web-gateway/1.0'
};