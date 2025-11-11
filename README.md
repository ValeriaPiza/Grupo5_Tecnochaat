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
Se comunica directamente con los clientes mediante sockets y se ocupa de distribuir los mensajes, mantener el historial y gestionar los grupos.

- Servidor Proxy HTTP (Node.js + Express)
Este componente funciona como un puente entre el cliente web y el servidor Java.
Recibe las peticiones del navegador y las traduce a mensajes que el servidor de Java puede entender.
También devuelve las respuestas del servidor al navegador.

- Cliente Web (Interfaz de Usuario)
Es la parte visual del sistema, desarrollada con HTML, CSS y JavaScript.
Desde aquí los usuarios pueden conectarse, escribir mensajes, ver el chat en tiempo real y crear grupos con otros usuarios conectados.

## Cómo Funciona la Comunicación

El cliente web envía un mensaje o una acción al proxy usando HTTP.

El proxy (Node.js) traduce esa información y la envía por sockets al backend en Java.

El servidor Java procesa el mensaje y lo distribuye a los usuarios correspondientes (grupal, privado o por grupo).

La respuesta regresa al proxy, que la entrega de nuevo al cliente web.

Finalmente, el navegador actualiza la interfaz del chat en tiempo real.

## Requisitos Previos

- Java JDK 23 o superior
- Gradle
- Node.js (v18 o superior)
- npm
- Navegador web moderno


## Instrucciones para Ejecutar el Sistema

Para que todo funcione correctamente, necesitamos ejecutar los tres componentes al mismo tiempo, cada uno en una terminal diferente.

primero ejecutamos  "gradle buil" para ver que todo este funcionando bien

- Terminal 1 — Backend Java

Primero ejecutamos el servidor principal, que maneja las conexiones y los mensajes del chat usando el comando: 
 "java -cp build/classes/java/main Server.Server"

- Terminal 2 — Proxy HTTP

Luego iniciamos el proxy, que conecta el backend con el cliente web con el comando:

"node .\proxy\index.js"


Antes de eso, si es la primera vez, instalamos las dependencias con:

"npm install" 

-  Terminal 3 — Cliente Web

Finalmente, ejecutamos la interfaz web del chat, que se abrirá en el navegador.

- npm run frontend

Cuando se cargue, aparecerá algo como esto en la consola:

[i] [webpack-dev-server] On Your Network (IPv4): http://192.168.1.8:8080/


Abrimos ese enlace en el navegador, ingresamos nuestro nombre de usuario y ya podemos empezar a chatear con las demás personas conectadas.

## Funcionalidades Principales

- Chat en tiempo real:
Todos los usuarios conectados pueden enviarse mensajes instantáneamente. Cada mensaje se distribuye a los demás clientes sin necesidad de recargar la página.

- Mensajes privados:
Podemos conversar directamente con otra persona de manera individual, sin que los demás vean la conversación.

- Grupos:
Tenemos la opción de crear grupos y enviar mensajes dentro de ellos.
El sistema valida que no se creen grupos vacíos, es decir, solo se permite crear un grupo si hay participantes disponibles.

- Historial de mensajes:
Podemos consultar los mensajes anteriores tanto de conversaciones individuales como de grupos.
En la interfaz hay una cajita o espacio de selección donde escogemos si queremos ver el historial de un usuario o de un grupo, y luego escribimos el nombre correspondiente para cargarlo.

- Lista de usuarios conectados:
El sistema muestra en todo momento las personas que están conectadas al chat, para saber con quiénes se puede conversar o crear grupos.

- Desconexión segura:
Cada usuario puede salir del chat de forma segura sin afectar la comunicación de los demás.