# TecnoChat Proxy (Node.js)

Servidor HTTP/Express que actúa como puente entre el cliente web y el backend Java vía ZeroC Ice. También distribuye señalización WebRTC para las llamadas de audio.

## Estructura rápida
- `src/index.js`: endpoints HTTP (chat, grupos, historial, llamadas, health).
- `src/IceClient.js`: cliente Ice reusable para `ChatService` y `CallService`.
- `package.json`: scripts y dependencias (`express`, `cors`, `ice`).

## Flujo alto nivel
1) El navegador llama a endpoints `/api/*`.
2) El proxy invoca operaciones Ice hacia el backend (`ChatService`, `CallService`).
3) Responde al cliente web con JSON. Para llamadas WebRTC usa `/api/webrtc/signal` y `/api/webrtc/signals` para intercambiar oferta/respuesta/candidatos entre usuarios. Para notas de audio usa `/api/audio/upload` (recibe base64, envía bytes vía Ice y el backend las guarda en `audio_history`).

## Requisitos previos
- Node.js 18+ y npm.
- Backend Java levantado y accesible por Ice (host/puerto configurados en env).

## Comandos
- `npm install` — instala dependencias.
- `npm start` — ejecuta el proxy (`node src/index.js`).
- `npm run dev` — modo desarrollo con `nodemon`.

Variables de entorno principales:
- `ICE_HOST` (default `localhost`)
- `ICE_PORT` (default `10000`)
- `PORT` para el HTTP proxy (default `3002`)

## Cómo correr
```bash
cd proxy-node
npm install
ICE_HOST=localhost ICE_PORT=10000 PORT=3002 npm start
```

El proxy expone HTTP en `PORT` y requiere que el backend Ice esté disponible.
