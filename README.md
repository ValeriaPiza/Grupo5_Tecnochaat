# Grupo5_TecnoChat


## Integrantes: 
- Paula Andrea Ferreira A00403846
- Valeria Piza Saavedra A00405037
- Manuela Marin Millan  A00

## Descripción General

En este proyecto creamos un chat grupal en tiempo real que conecta un cliente web con un servidor en Java, utilizando un proxy desarrollado en Node.js como intermediario.

Nuestro objetivo fue lograr cumplir con la rubrica y que diferentes tecnologías se comunicaran entre sí: el navegador (cliente web), el servidor de sockets (Java) y el proxy HTTP (Node).
De esta forma, conseguimos que varios usuarios puedan chatear al mismo tiempo desde sus navegadores, enviar mensajes privados y participar en grupos.

## Componentes del proyecto 

El sistema está formado por tres partes principales que trabajan juntas:

- Backend Java (Servidor Principal)
Es el encargado de manejar toda la lógica del chat.
Se comunica con clientes TCP y expone servicios RPC mediante ZeroC Ice. Se ocupa de distribuir los mensajes, mantener el historial y gestionar los grupos.

- Servidor Proxy HTTP (Node.js + Express)
Este componente funciona como un puente entre el cliente web y el servidor Java.
Recibe las peticiones del navegador y las traduce a llamadas RPC (Ice) hacia el backend Java.
También devuelve las respuestas del servidor al navegador.

- Cliente Web (Interfaz de Usuario)
Es la parte visual del sistema, desarrollada con HTML, CSS y JavaScript.
Desde aquí los usuarios pueden conectarse, escribir mensajes, ver el chat en tiempo real y crear grupos con otros usuarios conectados.

## Cómo Funciona la Comunicación

El cliente web envía un mensaje o una acción al proxy usando HTTP.

El proxy (Node.js) traduce esa información y la envía por RPC (ZeroC Ice) al backend en Java (servicios `ChatService` y `CallService`).

El servidor Java procesa el mensaje y lo distribuye a los usuarios correspondientes (grupal, privado o por grupo), además de exponer historiales, creación de grupos y llamadas.

La respuesta regresa al proxy, que la entrega de nuevo al cliente web.

Finalmente, el navegador actualiza la interfaz del chat en tiempo real.

## Requisitos Previos

- Java JDK 23 o superior
- Gradle instalado (o usa el wrapper `./gradlew` tras generar el wrapper con `gradle wrapper`)
- ZeroC Ice 3.7 para Java, incluyendo la herramienta `slice2java` disponible en el PATH
- ZeroC Ice para Node.js (paquete npm `ice`) y, si necesitas generar stubs adicionales, `slice2js` en el PATH
- Node.js (v18 o superior)
- npm
- Navegador web moderno
- https://www.zeroc.com/ice/downloads/3.7/java 

## Comandos útiles

- `./gradlew slice`  
  Genera/actualiza los stubs Java de Ice (corre `slice2java` sobre `backend-java/src/main/ice` y escribe en `backend-java/src/main/generated/TecnoChat/`). Ejecútalo siempre que modifiques las interfaces `.ice` antes de compilar o levantar el backend.


## Instrucciones para Ejecutar el Sistema

### Arranque rápido con script
Desde la raíz del repo:
1. Da permisos si es necesario: `chmod +x start-local.sh`
2. Ejecuta: `./start-local.sh` (en Windows, usar Git Bash o WSL; en PowerShell/CMD puedes correr `bash start-local.sh` si tienes bash disponible)
   - Levanta backend Java (`runServer`) en puerto TCP 6789 e Ice en 10000.
   - Levanta proxy-node en puerto 3002 usando Ice (host/puerto configurables con `ICE_HOST` y `ICE_PORT`).
   - Levanta web-app (webpack dev server, usualmente en http://localhost:8080).
3. Detén todo con `kill <PIDs>` que el script imprime o `pkill -f "com.tecnochat.server.Server" node webpack`.

### Arranque manual
- Backend Java:
  - `cd backend-java`
  - `gradle wrapper` (si es la primera vez que usaras el proyecto)
  - `./gradlew build`
  - `./gradlew --no-daemon runServer` (expone TCP 6789 e Ice 10000)
- Proxy HTTP (Node):
  - `cd proxy-node`
  - `npm install`
  - `npm install multer` (si no lo tienes, esto es para el envio de audios)
  - `npm run start` (ICE_HOST=localhost ICE_PORT=10000 PORT=3002)
- Cliente Web:
  - `cd web-app`
  - `npm install`
  - `npm run dev` (abre http://localhost:8080)

## Funcionalidades Principales

- Chat en tiempo real: mensajes privados y de grupo distribuidos al instante.
- Grupos: creación y mensajería grupal, validando miembros.
- Historial: consulta de conversaciones privadas y de grupo.
- Lista de usuarios conectados: visibilidad de quién está disponible.
- Desconexión segura: logout sin afectar a otros usuarios.
- Llamadas de audio (WebRTC): señalización vía Ice/HTTP; el navegador establece WebRTC (STUN público configurado en el front) y el backend almacena/entrega la señalización.
- Notas de audio (web): se graban en el navegador y se suben al backend vía proxy/Ice; se guardan en `audio_history` y quedan registradas en el historial.

## Nota sobre audio y backend
- El backend Java/Ice solo transporta señalización de llamadas; el audio de las llamadas sigue siendo P2P WebRTC entre navegadores.
- Las notas de audio sí pasan por el backend: se reciben vía `sendAudio`, se guardan en disco y se registran en el historial.

### Por qué no hay “llamada” por RPC/Ice y se usa WebRTC
- Ice/RPC en este proyecto solo mueve datos discretos (mensajes, señalización). No hay un canal de transporte en tiempo real para audio: `ChatService`/`CallService` no abren sockets de media, ni el proxy HTTP soporta streaming continuo.
- El backend Java no actúa como media server: no recibe, mezcla ni reenvía paquetes de audio. Hacerlo implicaría implementar un SFU/relay o un protocolo de media sobre Ice, que no está presente.
- El navegador no puede abrir sockets arbitrarios UDP/TCP al backend para audio crudo por políticas de seguridad; WebRTC es la vía estándar para media en web.
- WebRTC crea pares `RTCPeerConnection` con control de ICE/SDP/STUN/TURN para atravesar NAT y negociar codecs. En este proyecto se usa solo la señalización (offer/answer/candidates) vía Ice/HTTP, y el audio viaja directamente P2P entre navegadores.
- Con la arquitectura actual, solo las notas de voz pasan por el backend (se suben como archivo vía proxy/Ice). Para llamadas “reales” centralizadas habría que añadir un servidor de medios o un canal de streaming en Ice y modificar clientes/proxy.
