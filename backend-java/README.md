# TecnoChat Backend (Java)

Servidor principal que maneja lógica de chat, grupos, historial y llamadas. Expone servicios RPC mediante ZeroC Ice (incluida señalización WebRTC) y acepta conexiones TCP para clientes nativos.

## Estructura rápida
- `src/main/java`: lógica de chat (`server`) e implementación Ice (`ice`).
- `src/main/ice`: definiciones Slice (`ChatService.ice`, `CallService`).
- `src/main/generated`: stubs Java generados por `slice2java`.
- `build.gradle`: configuración de dependencias (Ice 3.7.9) y tarea `slice`.

## Flujo alto nivel
1) Clientes web llaman al proxy Node por HTTP.
2) El proxy traduce a RPC Ice hacia este backend.
3) El backend procesa: registra usuarios, envía mensajes (privados/grupo), maneja historial, llamadas y notas de audio; responde al proxy.

## Requisitos previos
- JDK 17+ (se probó con 23).
- Gradle wrapper (`./gradlew`).
- ZeroC Ice 3.7 para Java, con `slice2java` en PATH.

## Comandos
- `./gradlew build` — compila y ejecuta tests.
- `./gradlew runServer` — levanta el servidor TCP (6789) e Ice (10000).
- `./gradlew slice` — genera/actualiza stubs desde `src/main/ice` (incluye señalización WebRTC y envío de audio en `ChatService`).

## Cómo correr
```bash
cd backend-java
./gradlew slice     # solo si cambiaste .ice
./gradlew build
./gradlew --no-daemon runServer
```

El servidor queda escuchando en TCP 6789 y Ice 10000 por defecto. Ajusta puertos en `Server.java` o propiedades si lo necesitas.

## Señalización WebRTC vía Ice
- `CallService.sendRtcSignal(from, to, type, payload)`: encola señales (offer/answer/candidate/hangup) para un usuario.
- `CallService.pullRtcSignals(user)`: retorna y limpia las señales pendientes para ese usuario.
Las colas de señalización se mantienen en memoria en `ClientHandler` con límite de 50 eventos por usuario.

## Notas de audio vía Ice
- `ChatService.sendAudio(from, to, isGroup, filename, data)`: recibe bytes de audio desde web/proxy, los guarda en `server_audios` y registra en `audio_history`/historial correspondiente (privado o grupo).
