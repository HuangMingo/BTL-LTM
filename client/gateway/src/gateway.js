/**
 * Gateway Module - Cầu nối WebSocket ↔ TCP Socket
 * 
 * Nhiệm vụ:
 * 1. Khi browser kết nối WebSocket → mở TCP socket đến server
 * 2. Forward WebSocket message → TCP frame (encode)
 * 3. Forward TCP frame → WebSocket message (decode)
 * 4. Đóng đối xứng: một đầu đóng → đóng đầu kia
 * 5. Gửi PING mỗi 30 giây để keep-alive
 * 
 * Reference: architecture.md Section 1, protocol-design.md Section 8
 */

const net = require('net');
const { encodeFrame, FrameReader } = require('./codec');
const config = require('./config');

class Gateway {
  constructor(wsClient) {
    this.wsClient = wsClient;           // WebSocket client (browser)
    this.tcpSocket = null;              // TCP socket (server)
    this.frameReader = new FrameReader();
    this.state = 'DISCONNECTED';        // DISCONNECTED | CONNECTED | READY | AUTHENTICATED | CLOSED
    this.pingTimer = null;
    this.idleTimer = null;
    this.pendingRequests = new Map();   // requestId → {resolve, reject, timer}
  }
  
  /**
   * Kết nối TCP đến server và gửi HELLO handshake
   */
  async connect() {
    return new Promise((resolve, reject) => {
      this.tcpSocket = net.createConnection(
        { host: config.TCP_HOST, port: config.TCP_PORT },
        () => {
          console.log(`[TCP] Connected to ${config.TCP_HOST}:${config.TCP_PORT}`);
          this.state = 'CONNECTED';
          this._resetIdleTimer();
          
          // Gửi HELLO ngay khi kết nối (bắt buộc theo đặc tả)
          this._sendHello()
            .then(() => {
              this.state = 'READY';
              this._startPingTimer();
              resolve();
            })
            .catch(reject);
        }
      );
      
      // Xử lý dữ liệu nhận được từ server
      this.tcpSocket.on('data', (chunk) => {
        this._handleTcpData(chunk);
      });
      
      // Xử lý lỗi TCP
      this.tcpSocket.on('error', (err) => {
        console.error('[TCP] Error:', err.message);
        this._closeWithError('TCP_ERROR');
        reject(err);
      });
      
      // Xử lý đóng TCP (từ server hoặc mạng)
      this.tcpSocket.on('close', () => {
        console.log('[TCP] Connection closed');
        this._closeFromServer();
      });
    });
  }
  
  /**
   * Gửi HELLO handshake đến server
   */
  async _sendHello() {
    const hello = {
      type: 'HELLO',
      requestId: 'hello-init',
      payload: {
        protocolVersion: config.PROTOCOL_VERSION,
        clientName: config.CLIENT_NAME
      }
    };
    
    return this._sendRequest(hello);
  }
  
  /**
   * Xử lý dữ liệu TCP nhận được (có thể chứa nhiều frame)
   */
  _handleTcpData(chunk) {
    this._resetIdleTimer();
    
    try {
      const messages = this.frameReader.append(chunk);
      
      for (const message of messages) {
        this._handleServerMessage(message);
      }
    } catch (err) {
      console.error('[Codec] Error:', err.message);
      if (this.frameReader.malformedStrikes >= config.MAX_MALFORMED_STRIKES) {
        this._closeWithError('TOO_MANY_MALFORMED');
      }
    }
  }
  
  /**
   * Xử lý message từ server (response hoặc event)
   */
  _handleServerMessage(message) {
    // Response: có requestId → ghép với request đang chờ
    if (message.requestId) {
      const pending = this.pendingRequests.get(message.requestId);
      if (pending) {
        clearTimeout(pending.timer);
        this.pendingRequests.delete(message.requestId);
        pending.resolve(message);
      }
      return;
    }
    
    // Event: không có requestId → forward ngay cho browser
    if (message.type && message.type !== 'PONG') {
      this._forwardToBrowser(message);
    }
  }
  
  /**
   * Gửi request đến server và chờ response (theo requestId)
   */
  _sendRequest(request) {
    return new Promise((resolve, reject) => {
      if (!this.tcpSocket || this.tcpSocket.destroyed) {
        return reject(new Error('TCP socket not connected'));
      }
      
      const requestId = request.requestId || this._generateRequestId();
      request.requestId = requestId;
      
      // Set timeout cho request (90 giây theo đặc tả)
      const timer = setTimeout(() => {
        this.pendingRequests.delete(requestId);
        reject(new Error(`Request timeout: ${requestId}`));
      }, config.READ_IDLE_TIMEOUT_MS);
      
      this.pendingRequests.set(requestId, { resolve, reject, timer });
      
      // Encode và gửi frame
      try {
        const frame = encodeFrame(request);
        this.tcpSocket.write(frame);
        this._resetIdleTimer();
      } catch (err) {
        clearTimeout(timer);
        this.pendingRequests.delete(requestId);
        reject(err);
      }
    });
  }
  
  /**
   * Forward message từ browser đến server
   */
  async handleBrowserMessage(wsMessage) {
    try {
      const request = JSON.parse(wsMessage);
      
      // Validate cơ bản
      if (!request.type) {
        throw new Error('Missing "type" field');
      }
      
      // Kiểm tra state machine
      this._validateState(request.type);
      
      // Gửi request và chờ response
      const response = await this._sendRequest(request);
      
      // Forward response về browser
      this._forwardToBrowser(response);
    } catch (err) {
      // Gửi lỗi về browser
      this._forwardToBrowser({
        type: 'ERROR',
        status: 400,
        message: err.message
      });
    }
  }
  
  /**
   * Kiểm tra state machine theo đặc tả
   */
  _validateState(commandType) {
    const validStates = {
      'HELLO': ['CONNECTED'],
      'REGISTER': ['READY'],
      'LOGIN': ['READY'],
      'SEND_MESSAGE': ['AUTHENTICATED'],
      'LIST_USERS': ['AUTHENTICATED'],
      'LOGOUT': ['AUTHENTICATED'],
      'PING': ['CONNECTED', 'READY', 'AUTHENTICATED'],
      'QUIT': ['CONNECTED', 'READY', 'AUTHENTICATED']
    };
    
    const allowed = validStates[commandType];
    if (!allowed) {
      throw new Error(`Unknown command: ${commandType}`);
    }
    if (!allowed.includes(this.state)) {
      throw new Error(`Command ${commandType} not allowed in state ${this.state}`);
    }
  }
  
  /**
   * Forward message đến browser qua WebSocket
   */
  _forwardToBrowser(message) {
    if (this.wsClient && this.wsClient.readyState === 1) { // OPEN
      this.wsClient.send(JSON.stringify(message));
    }
  }
  
  /**
   * Đóng gateway từ phía server (TCP đóng)
   */
  _closeFromServer() {
    console.log('[Gateway] Server closed connection');
    this._cleanup();
    
    // Đóng WebSocket về phía browser
    if (this.wsClient && this.wsClient.readyState === 1) {
      this.wsClient.close(1011, 'Server disconnected');
    }
  }
  
  /**
   * Đóng gateway do lỗi
   */
  _closeWithError(reason) {
    console.log(`[Gateway] Closing with error: ${reason}`);
    this._cleanup();
    
    if (this.wsClient && this.wsClient.readyState === 1) {
      this.wsClient.close(1011, reason);
    }
  }
  
  /**
   * Đóng gateway từ phía browser (WebSocket đóng)
   */
  closeFromBrowser() {
    console.log('[Gateway] Browser closed connection');
    this._cleanup();
    
    // Đóng TCP socket (đóng đối xứng)
    if (this.tcpSocket && !this.tcpSocket.destroyed) {
      // Gửi QUIT trước khi đóng (graceful shutdown)
      const quitFrame = encodeFrame({
        type: 'QUIT',
        requestId: 'quit-browser',
        payload: { reason: 'browser_closed' }
      });
      try {
        this.tcpSocket.write(quitFrame);
      } catch (e) {
        // Ignore write errors during close
      }
      this.tcpSocket.destroy();
    }
  }
  
  /**
   * Dọn dẹp resources (timers, pending requests, socket)
   */
  _cleanup() {
    this.state = 'CLOSED';
    
    // Xóa timers
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
    
    // Reject tất cả pending requests
    for (const [id, pending] of this.pendingRequests) {
      clearTimeout(pending.timer);
      pending.reject(new Error('Connection closed'));
    }
    this.pendingRequests.clear();
    
    // Reset frame reader
    this.frameReader.reset();
  }
  
  /**
   * Bắt đầu timer gửi PING mỗi 30 giây
   */
  _startPingTimer() {
    this.pingTimer = setInterval(() => {
      if (this.state !== 'CLOSED' && this.tcpSocket && !this.tcpSocket.destroyed) {
        const ping = {
          type: 'PING',
          requestId: `ping-${Date.now()}`,
          payload: { clientTime: new Date().toISOString() }
        };
        
        try {
          const frame = encodeFrame(ping);
          this.tcpSocket.write(frame);
        } catch (err) {
          console.error('[Ping] Error:', err.message);
        }
      }
    }, config.PING_INTERVAL_MS);
  }
  
  /**
   * Reset idle timer (90 giây không có dữ liệu → đóng)
   */
  _resetIdleTimer() {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
    }
    this.idleTimer = setTimeout(() => {
      console.log('[Gateway] Idle timeout');
      this._closeWithError('IDLE_TIMEOUT');
    }, config.READ_IDLE_TIMEOUT_MS);
  }
  
  /**
   * Sinh requestId duy nhất
   */
  _generateRequestId() {
    return `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}

module.exports = { Gateway };