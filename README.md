# Grupo5_TecnoChat


## Integrantes: 
- Paula Andrea Ferreira A00403846
- Valeria Piza Saavedra A00405037
- Manuela Marin Millan  A00

## Descripción General

En este proyecto construimos un sistema de chat completo en tiempo real combinando diferentes tecnologías que trabajan juntas en armonía. Nuestro objetivo principal fue cumplir con la rúbrica del proyecto final, aplicando RPC con ZeroC Ice para la comunicación con el backend Java y WebSockets para reflejar comportamientos interactivos y dinámicos dentro del cliente web.

La arquitectura que implementamos permite que los usuarios puedan conectarse desde sus navegadores, enviar mensajes privados, participar en grupos, consultar historiales, enviar notas de voz y realizar llamadas de audio en tiempo real. Todo esto fue logrado integrando múltiples capas de software: un servidor Java como núcleo del sistema, un proxy Node.js como puente lógico y un cliente web moderno que gestiona la interacción del usuario.

## Componentes del proyecto 

El sistema está formado por tres partes principales que trabajan juntas:

- Backend Java (Servidor Principal)
Es el encargado de manejar toda la lógica del chat.
Se comunica con clientes TCP y expone servicios RPC mediante ZeroC Ice. Se ocupa de distribuir los mensajes, mantener el historial, gestionar los grupos, gestionar usuarios activos y señalizacion para llamadas.

El backend funciona como un servidor Ice que expone los servicios ChatService y CallService. Estos servicios reciben las peticiones del proxy Node.js y devuelven la información necesaria para que el cliente web se mantenga sincronizado.

Este backend no transporta audio, ya que Ice solo se utiliza para mover datos discretos. La señalización de llamadas sí pasa por Ice (como quién llama a quién, quién acepta o cuelga), pero el audio se maneja en otro componente.

- Servidor Proxy HTTP (Node.js + Express)
Este componente funciona como un puente entre el cliente web y el servidor Java.
Este módulo cumple tres funciones clave:
  -Recibe solicitudes del navegador (login, mensajes, grupos, notas de voz)    y las redirige al backend utilizando Ice.
  -Se encarga de notificar al cliente web en tiempo real cuando llega un       mensaje, llega un mensaje privado, se envía un mensaje de grupo, se sube    una nota de audio y cuando hay una señal de llamada (call-offer, accept,    reject, hangup)
  -Manejador de notas de voz se reciben desde el navegador, se convierten a    Base64 y se envían al backend Java para guardarlas y registrarlas en el     historial.
  
-Servidor WebSocket de Audio (Node.js independiente)
Una de las partes más importantes y novedosas del proyecto es la implementación de un servidor dedicado para transportar audio en las llamadas, completamente separado del proxy principal.
Lo que hace es que recibe frames de audio PCM crudo enviados por un usuario
y los retransmite inmediatamente a los demás usuarios conectados, no guarda nada, solo actúa como “bridge” pero permite llamadas de audio fluidas.

- Cliente Web (Interfaz de Usuario)
Es la parte visual del sistema, desarrollada con HTML, CSS y JavaScript Webpack.
Desde aquí los usuarios pueden conectarse, escribir mensajes individuales y grupales, ver el chat en tiempo real, crear grupos con otros usuarios conectados observar el historial, enviar notas de voz y hacer llamadas.

## Cómo Funciona la Comunicación

El cliente web envía un mensaje o una acción al proxy usando HTTP.

El cliente envía acciones al proxy vía HTTP (login, mensajes, grupos).

El proxy traduce estas solicitudes a llamadas RPC Ice para el backend Java.

El backend procesa los mensajes, actualiza el historial o gestiona la señalización.

El proxy notifica al usuario (y a otros usuarios conectados) mediante WebSocket.

Para llamadas:

La señalización pasa por Ice/WS

El audio fluye por el servidor WS de audio

Así la interfaz del navegador se actualiza en tiempo real sin recargas y sin latencias perceptibles.

## Requisitos Previos

- Java JDK 23 o superior
- Gradle instalado (o usa el wrapper `./gradlew` tras generar el wrapper con `gradle wrapper`)
- ZeroC Ice 3.7 para Java, incluyendo la herramienta `slice2java` disponible en el PATH
- ZeroC Ice para Node.js (paquete npm `ice`) y, si necesitas generar stubs adicionales, `slice2js` en el PATH
- Node.js (v18 o superior)
- npm
- Navegador web moderno con soporte Web Audio API
- https://www.zeroc.com/ice/downloads/3.7/java 

## Comandos útiles

- `./gradlew slice`  
  Genera/actualiza los stubs Java de Ice (corre `slice2java` sobre `backend-java/src/main/ice` y escribe en `backend-java/src/main/generated/TecnoChat/`). Ejecútalo siempre que modifiques las interfaces `.ice` antes de compilar o levantar el backend.


## Instrucciones para Ejecutar el Sistema

### Arranque manual
- Backend Java:
  - `cd backend-java`
  - `./gradlew build`
  - `./gradlew --no-daemon runServer` (expone TCP 6789 e Ice 10000)
- Proxy HTTP (Node):
  - `cd proxy-node`
  - `npm install`
  - `npm run start`
- Servidor WebSocket de audio
  - `cd audio-server`
  - `node audio-ws-server.mjs`
  -  Este servidor expone:
    ws://localhost:9098/ws/audio/{username}
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
- Nota de voz: se puede grabar, enviar y reproducir además se guardan en el   historial.
- Llamadas de audio en tiempo real mediante WebSockets y Web Audio API.


## Nota sobre audio y backend
- El backend Java solo maneja señalización, no transmite audio.
- Las notas de voz sí pasan por el backend para almacenarse.
- Las llamadas usan el servidor de audio basado en WebSockets.
- El diseño permite una comunicación en tiempo real fluida, simple y          totalmente manejable desde Node.js.


