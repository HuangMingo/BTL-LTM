/**
 * SCP/1.0 Codec - Xử lý framing 4-byte length-prefix
 * 
 * Cấu trúc frame:
 * +--------------------------+---------------------------------------+
 * | Length N (4 bytes, BE)   | N bytes UTF-8 JSON payload            |
 * +--------------------------+---------------------------------------+
 * 
 * Reference: protocol-design.md Section 2
 */

const { MAX_FRAME_SIZE } = require('./config');

/**
 * Encode một object JSON thành frame SCP/1.0 (Buffer)
 * @param {Object} message - Object cần gửi (request hoặc response)
 * @returns {Buffer} Frame hoàn chỉnh (4 bytes length + JSON payload)
 */
function encodeFrame(message) {
  const json = JSON.stringify(message);
  const payload = Buffer.from(json, 'utf-8');
  const length = payload.length;
  
  // Kiểm tra giới hạn theo đặc tả
  if (length <= 0 || length > MAX_FRAME_SIZE) {
    throw new Error(`Frame size ${length} out of range [1, ${MAX_FRAME_SIZE}]`);
  }
  
  // Tạo buffer: 4 bytes (big-endian) + payload
  const frame = Buffer.alloc(4 + length);
  frame.writeUInt32BE(length, 0);  // 4 bytes độ dài, big-endian
  payload.copy(frame, 4);          // Copy payload vào sau 4 bytes đầu
  
  return frame;
}

/**
 * Class đọc frame từ TCP stream (xử lý TCP split/coalescing)
 * TCP là byte stream nên một read() có thể chứa:
 * - Một phần của frame
 * - Nhiều frame liên tiếp
 * - Một frame rưỡi + đầu frame tiếp theo
 */
class FrameReader {
  constructor() {
    this.buffer = Buffer.alloc(0);  // Buffer tích lũy dữ liệu chưa xử lý
    this.malformedStrikes = 0;      // Đếm lỗi malformed (max 3)
  }
  
  /**
   * Thêm dữ liệu mới nhận được từ TCP socket vào buffer
   * @param {Buffer} chunk - Dữ liệu mới nhận được
   * @returns {Array<Object>} Danh sách các message đã decode được (có thể rỗng)
   */
  append(chunk) {
    // Nối chunk mới vào buffer cũ
    this.buffer = Buffer.concat([this.buffer, chunk]);
    
    const messages = [];
    
    // Lặp lại cho đến khi buffer không đủ dữ liệu cho frame tiếp theo
    while (this.buffer.length >= 4) {
      // Đọc 4 bytes đầu để lấy độ dài payload
      const length = this.buffer.readUInt32BE(0);
      
      // Validate độ dài theo đặc tả SCP/1.0
      if (length <= 0 || length > MAX_FRAME_SIZE) {
        this.malformedStrikes++;
        throw new Error(
          `Invalid frame length: ${length} (strikes: ${this.malformedStrikes}/${3})`
        );
      }
      
      // Kiểm tra xem đã nhận đủ payload chưa
      if (this.buffer.length < 4 + length) {
        // Chưa đủ dữ liệu, chờ chunk tiếp theo (TCP split)
        break;
      }
      
      // Đã đủ dữ liệu: tách frame ra khỏi buffer
      const payload = this.buffer.subarray(4, 4 + length);
      this.buffer = this.buffer.subarray(4 + length);
      
      // Parse JSON
      const json = payload.toString('utf-8');
      let message;
      try {
        message = JSON.parse(json);
      } catch (err) {
        this.malformedStrikes++;
        throw new Error(`Malformed JSON: ${err.message}`);
      }
      
      this.malformedStrikes = 0;  // Reset counter khi thành công
      messages.push(message);
    }
    
    return messages;
  }
  
  /**
   * Reset buffer (dùng khi đóng connection)
   */
  reset() {
    this.buffer = Buffer.alloc(0);
    this.malformedStrikes = 0;
  }
}

module.exports = {
  encodeFrame,
  FrameReader
};