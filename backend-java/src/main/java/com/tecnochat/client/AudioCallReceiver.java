package com.tecnochat.client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class AudioCallReceiver {
    private static volatile boolean recibiendo = true;
    private static DatagramSocket socket = null;
    private static SourceDataLine altavoz = null;
    
    private static long paquetesRecibidos = 0;
    private static long bytesRecibidos = 0;
    private static long inicioRecepcion = 0;
    
    private static final int BUFFER_SIZE = 1024;
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    
    private static String tipoLlamada = "INDIVIDUAL";
    private static String idLlamada = "";
    private static int puertoEscucha = 0;

    public static void iniciarRecepcion(int puertoEscucha) {
        AudioCallReceiver.puertoEscucha = puertoEscucha;
        iniciarRecepcion(puertoEscucha, "INDIVIDUAL", "");
    }

    public static void iniciarRecepcion(int puertoEscucha, String tipo, String idLlamada) {
        AudioCallReceiver.tipoLlamada = tipo;
        AudioCallReceiver.idLlamada = idLlamada;
        AudioCallReceiver.puertoEscucha = puertoEscucha;
        
        recibiendo = true;
        socket = null;
        altavoz = null;

        System.out.println((tipo.equals("GRUPAL") ? "LLAMADA GRUPAL" : "LLAMADA INDIVIDUAL") + " - INICIANDO RECEPCIÓN");
        System.out.println("Puerto de escucha: " + puertoEscucha);
        if (tipo.equals("GRUPAL")) {
            System.out.println("ID Llamada: " + idLlamada);
        }

        Thread receiverThread = new Thread(() -> {
            try {
                AudioFormat formato = getBestAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("Formato de audio no soportado. Probando alternativas...");
                    formato = getFallbackAudioFormat();
                    info = new DataLine.Info(SourceDataLine.class, formato);
                }

                altavoz = (SourceDataLine) AudioSystem.getLine(info);
                altavoz.open(formato);
                altavoz.start();

                System.out.println("Altavoz configurado:");
                System.out.println("   Sample Rate: " + formato.getSampleRate() + " Hz");
                System.out.println("   Sample Size: " + formato.getSampleSizeInBits() + " bits");
                System.out.println("   Canales: " + formato.getChannels());
                System.out.println("   Buffer: " + BUFFER_SIZE + " bytes");

                socket = new DatagramSocket(puertoEscucha);
                socket.setSoTimeout(5000);

                byte[] buffer = new byte[BUFFER_SIZE];
                inicioRecepcion = System.currentTimeMillis();

                System.out.println("Escuchando audio entrante en puerto " + puertoEscucha + "...");
                System.out.println("Escribe '10' en el menú principal para terminar la llamada");

                Thread statsThread = new Thread(() -> {
                    while (recibiendo) {
                        try {
                            Thread.sleep(10000);
                            mostrarEstadisticas();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
                statsThread.setDaemon(true);
                statsThread.start();

                while (recibiendo) {
                    try {
                        DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                        socket.receive(paquete);
                        
                        if (paquete.getLength() > 0) {
                            if (esAudioValido(paquete.getData(), paquete.getLength())) {
                                altavoz.write(paquete.getData(), 0, paquete.getLength());
                                paquetesRecibidos++;
                                bytesRecibidos += paquete.getLength();
                            } else {
                                System.err.println("Paquete de audio inválido recibido");
                            }
                        }
                        
                    } catch (java.net.SocketTimeoutException e) {
                        if (recibiendo) {
                            System.out.println("Timeout de recepción, continuando escucha...");
                        }
                    } catch (Exception e) {
                        if (recibiendo) {
                            System.err.println("Error en recepción de audio: " + e.getMessage());
                            Thread.sleep(100);
                        }
                    }
                }

            } catch (LineUnavailableException e) {
                System.err.println("Línea de audio no disponible: " + e.getMessage());
                System.err.println("Verifica que los altavoces estén conectados y no estén siendo usados por otra aplicación");
            } catch (SocketException e) {
                if (recibiendo) {
                    System.err.println("Error de socket: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("ERROR en AudioCallReceiver: " + e.getMessage());
                e.printStackTrace();
            } finally {
                cerrarRecursos();
                mostrarEstadisticasFinales();
            }
        });
        
        receiverThread.setName("AudioReceiver-" + puertoEscucha);
        receiverThread.start();
    }

    private static AudioFormat getBestAudioFormat() {
        try {
            return new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
        } catch (Exception e) {
            return getFallbackAudioFormat();
        }
    }

    private static AudioFormat getFallbackAudioFormat() {
        System.out.println("Usando formato de audio alternativo...");
        return new AudioFormat(8000.0f, 16, 1, true, false);
    }

    private static boolean esAudioValido(byte[] audioData, int length) {
        if (length <= 0 || length > BUFFER_SIZE) {
            return false;
        }
        boolean esMayormenteSilencio = true;
        for (int i = 0; i < Math.min(length, 100); i++) {
            if (Math.abs(audioData[i]) > 10) {
                esMayormenteSilencio = false;
                break;
            }
        }
        return !esMayormenteSilencio;
    }

    private static void mostrarEstadisticas() {
        if (paquetesRecibidos == 0) return;
        
        long tiempoTranscurrido = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTranscurrido == 0) return;
        
        long paquetesPorSegundo = paquetesRecibidos / tiempoTranscurrido;
        long kbps = (bytesRecibidos * 8) / (tiempoTranscurrido * 1024);
        
        System.out.println("\nESTADÍSTICAS DE RECEPCIÓN:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Tiempo: " + tiempoTranscurrido + " segundos");
        System.out.println("   Paquetes: " + paquetesRecibidos + " (" + paquetesPorSegundo + "/s)");
        System.out.println("   Datos: " + (bytesRecibidos / 1024) + " KB (" + kbps + " kbps)");
        System.out.println("   Puerto: " + puertoEscucha);
    }

    private static void mostrarEstadisticasFinales() {
        long tiempoTotal = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTotal == 0) tiempoTotal = 1;
        
        System.out.println("\nESTADÍSTICAS FINALES DE LLAMADA:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Duración total: " + tiempoTotal + " segundos");
        System.out.println("   Paquetes recibidos: " + paquetesRecibidos);
        System.out.println("   Datos recibidos: " + (bytesRecibidos / 1024) + " KB");
        System.out.println("   Promedio: " + (paquetesRecibidos / tiempoTotal) + " paquetes/segundo");
        
        if (tipoLlamada.equals("GRUPAL")) {
            System.out.println("   Llamada grupal finalizada");
        } else {
            System.out.println("   Llamada individual finalizada");
        }
    }

    private static void cerrarRecursos() {
        try {
            if (altavoz != null) {
                System.out.println("Cerrando altavoz...");
                altavoz.stop();
                altavoz.close();
                altavoz = null;
            }
        } catch (Exception e) {
            System.err.println("Error cerrando altavoz: " + e.getMessage());
        }

        try {
            if (socket != null && !socket.isClosed()) {
                System.out.println("Cerrando socket de recepción...");
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            System.err.println("Error cerrando socket: " + e.getMessage());
        }
    }

    public static void terminarRecepcion() {
        System.out.println("Solicitando terminación de recepción...");
        recibiendo = false;
        
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {}
        }
    }

    public static boolean isRecibiendo() {
        return recibiendo;
    }

    public static String getEstadisticas() {
        long tiempoTranscurrido = (System.currentTimeMillis() - inicioRecepcion) / 1000;
        if (tiempoTranscurrido == 0) tiempoTranscurrido = 1;
        
        return String.format(
            "Llamada %s - %d segundos - %d paquetes - %d KB",
            tipoLlamada,
            tiempoTranscurrido,
            paquetesRecibidos,
            bytesRecibidos / 1024
        );
    }

    public static String getInfoLlamada() {
        return String.format(
            "Tipo: %s | Puerto: %d | ID: %s | Activa: %s",
            tipoLlamada,
            puertoEscucha,
            idLlamada.isEmpty() ? "N/A" : idLlamada,
            recibiendo ? "Sí" : "No"
        );
    }
}
