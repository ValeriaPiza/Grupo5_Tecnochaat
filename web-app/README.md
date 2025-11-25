# TecnoChat Web App

Cliente web (HTML/CSS/JS) que consume el proxy HTTP para interactuar con el backend de chat y realizar llamadas de audio WebRTC usando señalización vía Ice.

## Estructura rápida
- `src/index.js`: lógica UI, llamadas HTTP al proxy, manejo de usuarios/grupos/historial.
- `index.html`: layout principal.
- `webpack.config.js`: build/dev server.
- `package.json`: scripts y dependencias.
- `ring.mp3`: sonido de timbre para llamadas entrantes (colócalo en la raíz de `web-app` o en `public/` y se referencia como `./ring.mp3`).

## Flujo alto nivel
1) El usuario se conecta (login vía proxy).
2) Envía mensajes privados/grupales, consulta historial y lista de usuarios/grupos.
3) Para llamadas: la UI construye conexiones WebRTC (captura micrófono, crea RTCPeerConnection con STUN público configurado en `src/index.js`). La señalización (offer/answer/candidates/hangup) se envía/polling a través del proxy (`/api/webrtc/signal` y `/api/webrtc/signals`) y el backend Ice la distribuye.
4) Notas de audio: se graban en el navegador (MediaRecorder) y se suben a `/api/audio/upload` (base64); el backend las recibe vía Ice `sendAudio`, las guarda en `audio_history` y registra en el historial correspondiente.

## Requisitos previos
- Node.js 18+ y npm.
- Proxy Node corriendo y accesible (variables `PROXY_URL` en `src/index.js` si necesitas cambiar host/puerto).

## Comandos
- `npm install` — instala dependencias.
- `npm run dev` — levanta webpack dev server (default http://localhost:8080).
- `npm run build` — genera build de producción en `dist/`.

## Cómo correr
```bash
cd web-app
npm install
npm run dev
```

Abre el dev server en el navegador. Asegúrate de que el proxy y backend estén levantados para que las peticiones funcionen.
