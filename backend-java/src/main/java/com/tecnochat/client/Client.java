package com.tecnochat.client;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private static String SERVER_ADDRESS = "localhost";
    private static int SERVER_PORT = 6789;
    public static volatile boolean llamadaActiva = false;

    private static volatile boolean esperandoEnter = false;
    private static final Object enterLock = new Object();
    private static Thread recibirThread;

    private static final Scanner SCANNER = new Scanner(System.in);


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== TECNOCHAT CLIENTE ===");
        System.out.println("1. Conectar al servidor local (localhost:6789)");
        System.out.println("2. Conectar a otro servidor");
        System.out.print("Elige opcion: ");
        
        String opcionConexion = scanner.nextLine();
        
        if (opcionConexion.equals("2")) {
            System.out.print("Ingresa la direccion IP del servidor: ");
            SERVER_ADDRESS = scanner.nextLine();
            System.out.print("Ingresa el puerto del servidor: ");
            try {
                SERVER_PORT = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Puerto invalido. Usando puerto por defecto 6789");
                SERVER_PORT = 6789;
            }
        } else {
            SERVER_ADDRESS = "localhost";
            SERVER_PORT = 6789;
        }
        
        System.out.println("Conectando a " + SERVER_ADDRESS + ":" + SERVER_PORT + "...");

        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                DataInputStream dataIn = new DataInputStream(socket.getInputStream());
                DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
        ) {
            new File("audios_recibidos").mkdirs();

            recibirThread = new Thread(() -> {
                recibirMensajes(in, dataIn, out);
            });
            recibirThread.start();

            procesarMenuPrincipal(scanner, in, out, dataIn, dataOut);

        } catch (Exception e) {
            System.err.println("Error en el cliente: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("Cliente desconectado.");
            scanner.close();
        }
    }

    private static void recibirMensajes(BufferedReader in, DataInputStream dataIn, PrintWriter out) {
        try {
            String lineaRecibida;
            while ((lineaRecibida = in.readLine()) != null) {
                procesarMensajeRecibido(lineaRecibida, in, dataIn, out);
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("Socket closed")) {
                System.out.println("Conexion con el servidor cerrada o error de lectura.");
            }
        }
    }

    private static void procesarMensajeRecibido(String lineaRecibida, BufferedReader in, DataInputStream dataIn, PrintWriter out) {
        try {
            if (lineaRecibida.equals("AUDIO_INCOMING")) {
                recibirYReproducirAudio(dataIn);
            } else if (lineaRecibida.equals("LLAMADA_INCOMING")) {
                manejarLlamadaEntrante(dataIn, out);
            } else if (lineaRecibida.equals("LLAMADA_GRUPAL_INCOMING")) {
                manejarLlamadaGrupalEntrante(dataIn, out);
            } else if (lineaRecibida.startsWith("IP_DESTINO:")) {
                manejarConfiguracionLlamada(lineaRecibida, in);
            } else if (lineaRecibida.startsWith("CONFIG_LLAMADA_GRUPAL")) {
                manejarConfiguracionLlamadaGrupal(in);
            } else if (lineaRecibida.startsWith("CLIENTES_CONECTADOS:")) {
                mostrarClientesConectados(lineaRecibida);
            } else if (lineaRecibida.equals("LISTO_PARA_AUDIO")) {
                System.out.println("Servidor listo para recibir audio...");
            } else {
                System.out.println(lineaRecibida);
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
        }
    }

    // Llamada entrante individual
    private static void manejarLlamadaEntrante(DataInputStream dataIn, PrintWriter out) {
        try {
            String emisor = dataIn.readUTF();
            String ip = dataIn.readUTF();
            int puertoRecepcion = dataIn.readInt();
            int puertoEnvio = dataIn.readInt();

            System.out.println("\n=== LLAMADA ENTRANTE ===");
            System.out.println("De: " + emisor);
            System.out.println("Desde: " + ip);
            System.out.println("Puerto recepcion: " + puertoRecepcion);
            System.out.println("Puerto envio: " + puertoEnvio);
            System.out.println("Aceptar llamada? (S/N):");
            
            String respuesta;
            synchronized (enterLock) {
                Scanner tempScanner = new Scanner(System.in);
                respuesta = tempScanner.nextLine().trim();
            }

            if (respuesta.equalsIgnoreCase("S")) {
                System.out.println("Aceptando llamada...");

                System.out.println("Configuracion de puertos:");
                System.out.println("   Escucha en: " + puertoRecepcion);
                System.out.println("   Envia a: " + puertoEnvio);

                // Iniciar recepcion PRIMERO
                new Thread(() -> {
                    System.out.println("Iniciando receptor de audio...");
                    AudioCallReceiver.iniciarRecepcion(puertoRecepcion);
                }).start();

                Thread.sleep(1000);

                // Luego iniciar envio
                new Thread(() -> {
                    System.out.println("Iniciando envio de audio...");
                    AudioCallSender.iniciarLlamada(ip, puertoEnvio);
                }).start();

                llamadaActiva = true;
                out.println("CALL_ACCEPTED");
                System.out.println("*** Llamada activa - Escribe '10' para terminar ***");

            } else {
                System.out.println("Llamada rechazada.");
                out.println("CALL_REJECTED");
            }
        } catch (Exception e) {
            System.err.println("Error al manejar llamada entrante: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Llamada grupal entrante
    private static void manejarLlamadaGrupalEntrante(DataInputStream dataIn, PrintWriter out) {
        try {
            String emisor = dataIn.readUTF();
            String grupo = dataIn.readUTF();
            String ip = dataIn.readUTF();
            int puertoRecepcion = dataIn.readInt();
            int puertoEnvio = dataIn.readInt();
            String idLlamada = dataIn.readUTF();

            System.out.println("\n=== LLAMADA GRUPAL ENTRANTE ===");
            System.out.println("De: " + emisor);
            System.out.println("Grupo: " + grupo);
            System.out.println("ID Llamada: " + idLlamada);
            System.out.println("Puerto recepcion: " + puertoRecepcion);
            System.out.println("Puerto envio: " + puertoEnvio);
            System.out.println("Unirte a la llamada grupal? (S/N):");
            
            String respuesta;
            synchronized (enterLock) {
                Scanner tempScanner = new Scanner(System.in);
                respuesta = tempScanner.nextLine().trim();
            }

            if (respuesta.equalsIgnoreCase("S")) {
                System.out.println("Uniendote a llamada grupal...");

                new Thread(() -> AudioCallReceiver.iniciarRecepcion(puertoRecepcion, "GRUPAL", idLlamada)).start();
                Thread.sleep(500);
                new Thread(() -> AudioCallSender.iniciarLlamada(ip, puertoEnvio, "GRUPAL", idLlamada)).start();

                llamadaActiva = true;
                out.println("CALL_GRUPAL_ACCEPTED");
                System.out.println("*** En llamada grupal - Escribe '10' para salir ***");
            } else {
                System.out.println("Llamada grupal rechazada.");
                out.println("CALL_GRUPAL_REJECTED");
            }
        } catch (Exception e) {
            System.err.println("Error al manejar llamada grupal entrante: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Configuracion de llamada individual
    private static void manejarConfiguracionLlamada(String lineaRecibida, BufferedReader in) {
        try {
            String ip = lineaRecibida.split(":")[1];
            String puertoEnvioLine = in.readLine();
            String puertoRecepcionLine = in.readLine();

            if (puertoEnvioLine != null && puertoEnvioLine.startsWith("PUERTO_ENVIO:") &&
                puertoRecepcionLine != null && puertoRecepcionLine.startsWith("PUERTO_RECEPCION:")) {
                
                int puertoEnvio = Integer.parseInt(puertoEnvioLine.split(":")[1]);
                int puertoRecepcion = Integer.parseInt(puertoRecepcionLine.split(":")[1]);

                System.out.println("\n=== INICIANDO LLAMADA ===");
                System.out.println("Conectando con: " + ip);
                System.out.println("Puerto envio: " + puertoEnvio);
                System.out.println("Puerto recepcion: " + puertoRecepcion);

                new Thread(() -> AudioCallSender.iniciarLlamada(ip, puertoEnvio)).start();
                Thread.sleep(500);
                new Thread(() -> AudioCallReceiver.iniciarRecepcion(puertoRecepcion)).start();

                llamadaActiva = true;
                System.out.println("*** Llamada activa - Escribe '10' para terminar ***");
            }
        } catch (Exception e) {
            System.err.println("Error en configuracion de llamada: " + e.getMessage());
        }
    }

   
    private static void manejarConfiguracionLlamadaGrupal(BufferedReader in) {
        try {
            String ipCreador = in.readLine().split(":")[1];
            String puertoRecepcionLine = in.readLine().split(":")[1];
            String puertoEnvioLine = in.readLine().split(":")[1];
            String miembrosLine = in.readLine().split(":")[1];
            String idLlamada = in.readLine().split(":")[1];

            int puertoRecepcion = Integer.parseInt(puertoRecepcionLine);
            int puertoEnvio = Integer.parseInt(puertoEnvioLine);
            int miembrosInvitados = Integer.parseInt(miembrosLine);

            System.out.println("\n=== INICIANDO LLAMADA GRUPAL ===");
            System.out.println("Miembros invitados: " + miembrosInvitados);
            System.out.println("Puerto recepcion: " + puertoRecepcion);
            System.out.println("Puerto envio: " + puertoEnvio);
            System.out.println("ID Llamada: " + idLlamada);

            // Limpiar destinos anteriores
            AudioCallSender.destinos.clear();

            // Agregar al creador como destino
            AudioCallSender.agregarDestino(ipCreador, puertoEnvio);

            // Leer IPs de otros miembros
            String linea;
            while (!(linea = in.readLine()).equals("END_IP_LIST")) {
                if (linea.startsWith("IP_MIEMBRO:")) {
                    String ipMiembro = linea.split(":")[1];
                    AudioCallSender.agregarDestino(ipMiembro, puertoEnvio);
                }
            }

            // Iniciar envío SIN borrar destinos
            new Thread(() -> AudioCallSender.iniciarLlamadaGrupal("GRUPAL", idLlamada)).start();
            Thread.sleep(500);
            new Thread(() -> AudioCallReceiver.iniciarRecepcion(puertoRecepcion, "GRUPAL", idLlamada)).start();

            llamadaActiva = true;
            System.out.println("*** Llamada grupal activa - Escribe '10' para salir ***");

        } catch (Exception e) {
            System.err.println("Error en configuracion de llamada grupal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void procesarMenuPrincipal(Scanner scanner, BufferedReader in, PrintWriter out, 
                                            DataInputStream dataIn, DataOutputStream dataOut) {
        while (true) {
            String linea = obtenerOpcionMenu(scanner);
            
            if (linea == null) continue;

            if (!procesarOpcionMenu(linea, scanner, out, dataOut)) {
                break;
            }
        }
    }

    private static String obtenerOpcionMenu(Scanner scanner) {
        String linea;
        synchronized (enterLock) {
            if (esperandoEnter) {
                return null;
            }
            mostrarMenu();
            System.out.print("> ");
            linea = scanner.nextLine();
        }
        return linea;
    }

    private static boolean procesarOpcionMenu(String linea, Scanner scanner, PrintWriter out, DataOutputStream dataOut) {
        try {
            switch (linea) {
                case "11":
                    out.println("11");
                    return true;
                case "5":
                    out.println(linea);
                    Thread.sleep(100);
                    System.out.println("Ingresa el nombre del usuario destinatario:");
                    String usuario = scanner.nextLine();
                    out.println(usuario);
                    grabarYEnviarAudio(dataOut);
                    return true;
                case "6":
                    out.println(linea);
                    Thread.sleep(100);
                    System.out.println("Ingresa el nombre del grupo:");
                    String grupo = scanner.nextLine();
                    out.println(grupo);
                    grabarYEnviarAudio(dataOut);
                    return true;
                case "9":
                    out.println("9");
                    Thread.sleep(100);
                    System.out.print("Nombre del usuario o grupo a llamar: ");
                    String destinatario = scanner.nextLine();
                    out.println(destinatario);
                    System.out.println("Esperando respuesta del servidor para la llamada...");
                    return true;
                case "10":
                    manejarTerminacionLlamada(out);
                    return true;
                case "4":
                    System.out.println("Desconectando...");
                    if (recibirThread != null) {
                        recibirThread.interrupt();
                    }
                    out.println("4");
                    return false;
                default:
                    out.println(linea);
                    return true;
            }
        } catch (Exception e) {
            System.err.println("Error procesando opcion: " + e.getMessage());
            return true;
        }
    }

    private static void manejarTerminacionLlamada(PrintWriter out) {
        if (llamadaActiva) {
            AudioCallSender.terminarLlamada();
            AudioCallReceiver.terminarRecepcion();
            llamadaActiva = false;
            System.out.println("Llamada terminada.");
            out.println("CALL_ENDED");
        } else {
            System.out.println("No hay llamada activa.");
        }
    }

    private static void mostrarMenu() {
        System.out.println("\n=== MENU TECNOCHAT ===");
        System.out.println("1. Enviar mensaje a usuario");
        System.out.println("2. Crear grupo");
        System.out.println("3. Enviar mensaje a grupo");
        System.out.println("4. Salir");
        System.out.println("5. Nota de voz privada");
        System.out.println("6. Nota de voz a grupo");
        System.out.println("7. Ver historial privado");
        System.out.println("8. Ver historial de grupo");
        System.out.println("9. Llamar a un usuario/grupo");
        System.out.println("10. Terminar llamada");
        System.out.println("11. Ver clientes en linea");
        System.out.println("======================");
    }

    private static void mostrarClientesConectados(String mensajeServidor) {
        System.out.println("\n=== CLIENTES CONECTADOS ===");
        String[] partes = mensajeServidor.split(":", 2);
        if (partes.length > 1) {
            String[] clientes = partes[1].split(",");
            for (String cliente : clientes) {
                if (!cliente.trim().isEmpty()) {
                    System.out.println("- " + cliente.trim());
                }
            }
        } else {
            System.out.println("No hay otros clientes conectados.");
        }
        System.out.println("===========================\n");
    }

    // Recibir y reproducir audio
    private static void recibirYReproducirAudio(DataInputStream dataIn) {
        try {
            String emisor = dataIn.readUTF();
            String nombreArchivo = dataIn.readUTF();
            long tamanoArchivo = dataIn.readLong();

            boolean esAudioGrupal = nombreArchivo.contains("group_") || emisor.contains("[GRUPO");
            
            if (esAudioGrupal) {
                System.out.println("\nAudio grupal recibido de " + emisor + " (" + tamanoArchivo + " bytes)");
            } else {
                System.out.println("\nAudio privado recibido de " + emisor + " (" + tamanoArchivo + " bytes)");
            }

            File carpeta = new File("audios_recibidos");
            if (!carpeta.exists()) {
                carpeta.mkdirs();
            }
            
            String nombreUnico = System.currentTimeMillis() + "_de_" + emisor.replace("[GRUPO ", "").replace("]", "") + "_" + nombreArchivo;
            File archivoAudio = new File(carpeta, nombreUnico);

            try (FileOutputStream fos = new FileOutputStream(archivoAudio)) {
                byte[] buffer = new byte[4096];
                long bytesRecibidos = 0;

                while (bytesRecibidos < tamanoArchivo) {
                    int bytesParaLeer = (int) Math.min(buffer.length, tamanoArchivo - bytesRecibidos);
                    int bytesLeidos = dataIn.read(buffer, 0, bytesParaLeer);

                    if (bytesLeidos == -1) {
                        throw new IOException("Conexion cerrada inesperadamente");
                    }

                    fos.write(buffer, 0, bytesLeidos);
                    bytesRecibidos += bytesLeidos;
                }
                fos.flush();
            }

            System.out.println("Audio guardado: " + nombreUnico);

            // Reproduccion automatica para audios grupales
            if (esAudioGrupal) {
                System.out.println("Reproduciendo audio grupal automaticamente...");
                new Thread(() -> {
                    if (reproducirAudio(archivoAudio)) {
                        System.out.println("Reproduccion de audio grupal completada.");
                    } else {
                        System.out.println("Error en la reproduccion del audio grupal.");
                    }
                }).start();
                
            } else {
                System.out.println("Presiona ENTER para reproducir...");
                
                esperandoEnter = true;
                SCANNER.nextLine();
                
                System.out.println("Reproduciendo audio...");
                if (reproducirAudio(archivoAudio)) {
                    System.out.println("Reproduccion completada.");
                } else {
                    System.out.println("Error en la reproduccion.");
                }
            }

        } catch (Exception e) {
            System.err.println("Error al recibir audio: " + e.getMessage());
        } finally {
            synchronized (enterLock) {
                esperandoEnter = false;
            }
        }
    }

    private static void grabarYEnviarAudio(DataOutputStream dataOut) {
        try {
            AudioFormat formato = new AudioFormat(44100.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microfono no soportado");
                return;
            }

            TargetDataLine microfono = (TargetDataLine) AudioSystem.getLine(info);
            microfono.open(formato);
            microfono.start();

            System.out.println("Grabando... Presiona ENTER UNA SOLA VEZ para detener");
            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            final AtomicBoolean grabando = new AtomicBoolean(true);

            Thread controlGrabacion = new Thread(() -> {
                try {
                    SCANNER.nextLine();
                    grabando.set(false);
                    microfono.stop();
                    microfono.close();
                    System.out.println("Grabacion detenida");
                } catch (Exception e) {
                    System.err.println("Error en control de grabacion: " + e.getMessage());
                }
            });
            controlGrabacion.start();

            while (grabando.get() && microfono.isOpen()) {
                int bytesLeidos = microfono.read(buffer, 0, buffer.length);
                if (bytesLeidos > 0) {
                    audioBuffer.write(buffer, 0, bytesLeidos);
                }
                Thread.sleep(10);
            }

            try {
                controlGrabacion.join(2000);
            } catch (InterruptedException e) {
                System.err.println("Interrupcion en grabacion");
            }

            byte[] audioData = audioBuffer.toByteArray();
            if (audioData.length == 0) {
                System.out.println("No se grabo audio");
                return;
            }

            System.out.println("Grabacion finalizada (" + audioData.length + " bytes)");

            File archivoTemporal = new File("temp_audio_" + System.currentTimeMillis() + ".wav");

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                AudioInputStream ais = new AudioInputStream(bais, formato, audioData.length / formato.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, archivoTemporal);
            }

            dataOut.writeUTF(archivoTemporal.getName());
            dataOut.writeLong(archivoTemporal.length());

            try (FileInputStream fis = new FileInputStream(archivoTemporal)) {
                byte[] bufferEnvio = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = fis.read(bufferEnvio)) > 0) {
                    dataOut.write(bufferEnvio, 0, bytesLeidos);
                }
                dataOut.flush();
            }

            if (!archivoTemporal.delete()) {
                System.err.println("No se pudo eliminar el archivo temporal: " + archivoTemporal.getName());
            }

            System.out.println("Audio enviado correctamente al servidor.");

        } catch (Exception e) {
            System.err.println("Error al grabar/enviar audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean reproducirAudio(File archivoAudio) {
        try {
            if (!archivoAudio.exists() || archivoAudio.length() == 0) {
                System.err.println("El archivo de audio no existe o esta vacio");
                return false;
            }

            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(archivoAudio)) {
                AudioFormat format = audioStream.getFormat();

                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);

                clip.start();

                System.out.println("Duracion: " + (clip.getMicrosecondLength() / 1000000.0) + " segundos");

                while (clip.isRunning()) {
                    Thread.sleep(100);
                }

                clip.close();
                return true;

            } catch (UnsupportedAudioFileException e) {
                System.err.println("Formato de audio no soportado: " + e.getMessage());
                return false;
            } catch (LineUnavailableException e) {
                System.err.println("Linea de audio no disponible: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Error al reproducir audio: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void reproducirAudioLocal(File archivoAudio) {
        new Thread(() -> {
            System.out.println("Reproduciendo audio localmente...");
            if (reproducirAudio(archivoAudio)) {
                System.out.println("Reproduccion local completada.");
            } else {
                System.out.println("Error en reproduccion local.");
            }
        }).start();
    }
}