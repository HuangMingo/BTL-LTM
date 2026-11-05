/**
 * WebSocket Server Entry Point
 * 
 * Lắng nghe kết nối từ browser trên cổng WEB_PORT (8080)
 * Mỗi WebSocket connection → tạo một Gateway (TCP bridge)
 */

const WebSocket = require('ws');
const { Gateway } = require('./gateway');
const config = require('./config');

// Tạo WebSocket server
const wss = new WebSocket.Server({ port: config.WEB_PORT });

console.log(`
╔══════════════════════════════════════════════════════════╗
║         SCP/1.0 Web Gateway - Starting...                ║
╠══════════════════════════════════════════════════════════╣
║  WebSocket: ws://0.0.0.0:${config.WEB_PORT}                        ║
║  TCP Target: ${config.TCP_HOST}:${config.TCP_PORT}                        ║
║  Protocol:   ${config.PROTOCOL_VERSION}                            ║
╚══════════════════════════════════════════════════════════╝
`);

// Xử lý khi browser kết nối WebSocket
wss.on('connection', (ws, req) => {
  const clientId = req.socket.remoteAddress;
  console.log(`[WS] New browser connection from ${clientId}`);
  
  // Tạo gateway mới cho connection này
  const gateway = new Gateway(ws);
  
  // Kết nối TCP đến server
  gateway.connect()
    .then(() => {
      console.log(`[WS] Gateway ready for ${clientId}`);
    })
    .catch((err) => {
      console.error(`[WS] Gateway connection failed for ${clientId}:`, err.message);
      ws.close(1011, 'Cannot connect to chat server');
    });
  
  // Xử lý message từ browser
  ws.on('message', (data) => {
    const message = data.toString();
    console.log(`[WS←] Browser → Gateway: ${message.substring(0, 100)}...`);
    gateway.handleBrowserMessage(message);
  });
  
  // Xử lý khi browser đóng WebSocket
  ws.on('close', (code, reason) => {
    console.log(`[WS] Browser disconnected: ${code} ${reason}`);
    gateway.closeFromBrowser();
  });
  
  // Xử lý lỗi WebSocket
  ws.on('error', (err) => {
    console.error(`[WS] Error for ${clientId}:`, err.message);
    gateway._closeWithError('WS_ERROR');
  });
});

// Xử lý lỗi server
wss.on('error', (err) => {
  console.error('[WS] Server error:', err.message);
});

console.log(`Gateway listening on port ${config.WEB_PORT}...`);