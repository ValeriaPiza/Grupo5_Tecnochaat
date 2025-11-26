
import http from 'http';
import { WebSocketServer } from 'ws';
import url from 'url';

const PORT = 9098;

// Servidor HTTP base
const server = http.createServer((req, res) => {
  res.writeHead(200);
  res.end('Audio WebSocket Server\n');
});

// AQUÍ está el WebSocket server
const wss = new WebSocketServer({ server });

const clients = new Map(); // username -> ws

wss.on('connection', (ws, req) => {
  // Sacar el username de la URL

  const parsed = url.parse(req.url, true);
  const pathParts = (parsed.pathname || '').split('/').filter(Boolean);

  let username = 'anon';
  if (pathParts.length >= 3 && pathParts[0] === 'ws' && pathParts[1] === 'audio') {
    username = pathParts[2];
  } else {
    
    username = `user-${Date.now()}`;
  }

  console.log(` Cliente de audio conectado: ${username}`);
  clients.set(username, ws);

  ws.on('message', (data) => {
    // data = ArrayBuffer PCM Int16
    // reenviar a todos menos al emisor
    for (const [otherName, otherWs] of clients.entries()) {
      if (otherName === username) continue;
      if (otherWs.readyState === otherWs.OPEN) {
        otherWs.send(data);
      }
    }
  });

  ws.on('close', () => {
    console.log(` Cliente de audio desconectado: ${username}`);
    clients.delete(username);
  });

  ws.on('error', (err) => {
    console.error(`Error en audio WS (${username}):`, err);
    clients.delete(username);
  });
});

server.listen(PORT, () => {
  console.log(` Audio WS escuchando en ws://localhost:${PORT}/ws/audio/{username}`);
});
