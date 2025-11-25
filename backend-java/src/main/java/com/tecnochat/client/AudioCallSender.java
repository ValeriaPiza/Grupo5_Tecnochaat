package com.tecnochat.client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioCallSender {
    private static volatile boolean enviando = true;
    private static DatagramSocket socket = null;
    private static TargetDataLine microfono = null;
    
    private static long paquetesEnviados = 0;
    private static long bytesEnviados = 0;
    private static long inicioEnvio = 0;
    
    private static String tipoLlamada = "INDIVIDUAL";
    private static String idLlamada = "";
    static List<Destino> destinos = new CopyOnWriteArrayList<>();
    
    private static final int BUFFER_SIZE = 1024;
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    
    private static class Destino {
        String ip;
        int puerto;
        long paquetesEnviados;
        long bytesEnviados;
        
        Destino(String ip, int puerto) {
            this.ip = ip;
            this.puerto = puerto;
            this.paquetesEnviados = 0;
            this.bytesEnviados = 0;
        }
        
        @Override
        public String toString() {
            return ip + ":" + puerto;
        }
    }

    public static void iniciarLlamada(String ipDestino, int puertoDestino) {
        iniciarLlamada(ipDestino, puertoDestino, "INDIVIDUAL", "");
    }

    public static void iniciarLlamada(String ipDestino, int puertoDestino, String tipo, String idLlamada) {
        AudioCallSender.tipoLlamada = tipo;
        AudioCallSender.idLlamada = idLlamada;
        
        // Solo limpiar destinos si es llamada individual
        if (!"GRUPAL".equals(tipo)) {
            destinos.clear();
            agregarDestino(ipDestino, puertoDestino);
        }
        
        enviando = true;
        socket = null;
        microfono = null;

        System.out.println((tipo.equals("GRUPAL") ? "LLAMADA GRUPAL" : "LLAMADA INDIVIDUAL") + " - INICIANDO ENVIO");
        System.out.println("Destino principal: " + ipDestino + ":" + puertoDestino);
        if (tipo.equals("GRUPAL")) {
            System.out.println("ID Llamada: " + idLlamada);
            System.out.println("Enviando a " + destinos.size() + " destinatarios");
        }

        Thread senderThread = new Thread(() -> {
            try {
                AudioFormat formato = getBestAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("Formato de microfono no soportado. Probando alternativas...");
                    formato = getFallbackAudioFormat();
                    info = new DataLine.Info(TargetDataLine.class, formato);
                }

                microfono = (TargetDataLine) AudioSystem.getLine(info);
                microfono.open(formato);
                microfono.start();

                System.out.println("Microfono configurado:");
                System.out.println("   Sample Rate: " + formato.getSampleRate() + " Hz");
                System.out.println("   Sample Size: " + formato.getSampleSizeInBits() + " bits");
                System.out.println("   Canales: " + formato.getChannels());
                System.out.println("   Buffer: " + BUFFER_SIZE + " bytes");

                socket = new DatagramSocket();
                socket.setSoTimeout(1000);

                byte[] buffer = new byte[BUFFER_SIZE];
                inicioEnvio = System.currentTimeMillis();

                System.out.println("Enviando audio...");
                System.out.println("Habla ahora - Escribe '10' en el menu principal para terminar");

                while (enviando && microfono.isOpen()) {
                    try {
                        int bytesRead = microfono.read(buffer, 0, buffer.length);
                        
                        if (bytesRead > 0) {
                            for (Destino destino : destinos) {
                                try {
                                    DatagramPacket paquete = new DatagramPacket(
                                        buffer, bytesRead,
                                        InetAddress.getByName(destino.ip), destino.puerto
                                    );
                                    socket.send(paquete);
                                    
                                    destino.paquetesEnviados++;
                                    destino.bytesEnviados += bytesRead;
                                } catch (Exception e) {
                                    System.err.println("Error enviando a " + destino + ": " + e.getMessage());
                                }
                            }
                            
                            paquetesEnviados++;
                            bytesEnviados += bytesRead;
                        }
                        
                        Thread.sleep(10);
                        
                    } catch (Exception e) {
                        if (enviando) {
                            System.err.println("Error en envio de audio: " + e.getMessage());
                            Thread.sleep(100);
                        }
                    }
                }

            } catch (LineUnavailableException e) {
                System.err.println("Linea de audio no disponible: " + e.getMessage());
                System.err.println("Verifica que el microfono este conectado y no este siendo usado por otra aplicacion");
            } catch (Exception e) {
                if (enviando) {
                    System.err.println("ERROR en AudioCallSender: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                cerrarRecursos();
                mostrarEstadisticasFinales();
            }
        });
        
        senderThread.setName("AudioSender-" + puertoDestino);
        senderThread.start();
    }

    // Nuevo método para llamadas grupales que ya tienen destinos agregados
    public static void iniciarLlamadaGrupal(String tipo, String idLlamada) {
        AudioCallSender.tipoLlamada = tipo;
        AudioCallSender.idLlamada = idLlamada;
        enviando = true;
        socket = null;
        microfono = null;

        System.out.println("LLAMADA GRUPAL - INICIANDO ENVIO");
        System.out.println("ID Llamada: " + idLlamada);
        System.out.println("Enviando a " + destinos.size() + " destinatarios");

        Thread senderThread = new Thread(() -> {
            try {
                AudioFormat formato = getBestAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("Formato de microfono no soportado. Probando alternativas...");
                    formato = getFallbackAudioFormat();
                    info = new DataLine.Info(TargetDataLine.class, formato);
                }

                microfono = (TargetDataLine) AudioSystem.getLine(info);
                microfono.open(formato);
                microfono.start();

                System.out.println("Microfono configurado:");
                System.out.println("   Sample Rate: " + formato.getSampleRate() + " Hz");
                System.out.println("   Sample Size: " + formato.getSampleSizeInBits() + " bits");
                System.out.println("   Canales: " + formato.getChannels());
                System.out.println("   Buffer: " + BUFFER_SIZE + " bytes");

                socket = new DatagramSocket();
                socket.setSoTimeout(1000);

                byte[] buffer = new byte[BUFFER_SIZE];
                inicioEnvio = System.currentTimeMillis();

                System.out.println("Enviando audio...");
                System.out.println("Habla ahora - Escribe '10' en el menu principal para terminar");

                while (enviando && microfono.isOpen()) {
                    try {
                        int bytesRead = microfono.read(buffer, 0, buffer.length);
                        
                        if (bytesRead > 0) {
                            for (Destino destino : destinos) {
                                try {
                                    DatagramPacket paquete = new DatagramPacket(
                                        buffer, bytesRead,
                                        InetAddress.getByName(destino.ip), destino.puerto
                                    );
                                    socket.send(paquete);
                                    
                                    destino.paquetesEnviados++;
                                    destino.bytesEnviados += bytesRead;
                                } catch (Exception e) {
                                    System.err.println("Error enviando a " + destino + ": " + e.getMessage());
                                }
                            }
                            
                            paquetesEnviados++;
                            bytesEnviados += bytesRead;
                        }
                        
                        Thread.sleep(10);
                        
                    } catch (Exception e) {
                        if (enviando) {
                            System.err.println("Error en envio de audio: " + e.getMessage());
                            Thread.sleep(100);
                        }
                    }
                }

            } catch (LineUnavailableException e) {
                System.err.println("Linea de audio no disponible: " + e.getMessage());
                System.err.println("Verifica que el microfono este conectado y no este siendo usado por otra aplicacion");
            } catch (Exception e) {
                if (enviando) {
                    System.err.println("ERROR en AudioCallSender: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                cerrarRecursos();
                mostrarEstadisticasFinales();
            }
        });
        
        senderThread.setName("AudioSender-GRUPAL");
        senderThread.start();
    }

    public static void agregarDestino(String ip, int puerto) {
        Destino nuevoDestino = new Destino(ip, puerto);
        destinos.add(nuevoDestino);
        System.out.println("Destino agregado: " + nuevoDestino);
    }

    public static boolean removerDestino(String ip, int puerto) {
        for (Destino destino : destinos) {
            if (destino.ip.equals(ip) && destino.puerto == puerto) {
                destinos.remove(destino);
                System.out.println("Destino removido: " + destino);
                return true;
            }
        }
        return false;
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

    private static void mostrarEstadisticasFinales() {
        long tiempoTotal = (System.currentTimeMillis() - inicioEnvio) / 1000;
        if (tiempoTotal == 0) tiempoTotal = 1;
        
        System.out.println("\nESTADISTICAS FINALES DE ENVIO:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Duracion total: " + tiempoTotal + " segundos");
        System.out.println("   Paquetes enviados: " + paquetesEnviados);
        System.out.println("   Datos enviados: " + (bytesEnviados / 1024) + " KB");
        System.out.println("   Promedio: " + (paquetesEnviados / tiempoTotal) + " paquetes/segundo");
        
        if (tipoLlamada.equals("GRUPAL")) {
            System.out.println("   Llamada grupal - Destinos:");
            for (Destino destino : destinos) {
                System.out.println("      - " + destino + ": " + destino.paquetesEnviados + " paquetes");
            }
        } else {
            System.out.println("   Llamada individual finalizada");
        }
    }

    private static void cerrarRecursos() {
        try {
            if (microfono != null) {
                System.out.println("Cerrando microfono...");
                microfono.stop();
                microfono.close();
                microfono = null;
            }
        } catch (Exception e) {
            System.err.println("Error cerrando microfono: " + e.getMessage());
        }

        try {
            if (socket != null && !socket.isClosed()) {
                System.out.println("Cerrando socket de envio...");
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            System.err.println("Error cerrando socket: " + e.getMessage());
        }
    }

    public static void terminarLlamada() {
        System.out.println("Solicitando terminacion de envio...");
        enviando = false;
        
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {
                // Esperado
            }
        }
    }

    public static boolean isEnviando() {
        return enviando;
    }

    public static String getEstadisticas() {
        long tiempoTranscurrido = (System.currentTimeMillis() - inicioEnvio) / 1000;
        if (tiempoTranscurrido == 0) tiempoTranscurrido = 1;
        
        return String.format(
            "Envio %s - %d segundos - %d paquetes - %d KB - %d destinos",
            tipoLlamada,
            tiempoTranscurrido,
            paquetesEnviados,
            bytesEnviados / 1024,
            destinos.size()
        );
    }

    public static String getInfoLlamada() {
        return String.format(
            "Tipo: %s | Destinos: %d | ID: %s | Activa: %s",
            tipoLlamada,
            destinos.size(),
            idLlamada.isEmpty() ? "N/A" : idLlamada,
            enviando ? "Si" : "No"
        );
    }

    public static void probarMicrofono() {
        System.out.println("Probando microfono...");
        try {
            AudioFormat formato = getBestAudioFormat();
            TargetDataLine testLine = (TargetDataLine) AudioSystem.getLine(
                new DataLine.Info(TargetDataLine.class, formato));
            testLine.open(formato);
            testLine.start();
            
            byte[] buffer = new byte[BUFFER_SIZE];
            System.out.println("Habla ahora (3 segundos)...");
            
            long startTime = System.currentTimeMillis();
            int totalBytes = 0;
            
            while (System.currentTimeMillis() - startTime < 3000) {
                int bytesRead = testLine.read(buffer, 0, buffer.length);
                totalBytes += bytesRead;
                
                if (bytesRead > 0) {
                    double nivel = calcularNivelAudio(buffer, bytesRead);
                    System.out.printf("Nivel: %.1f%%\r", nivel * 100);
                }
            }
            
            testLine.stop();
            testLine.close();
            
            System.out.println("\nPrueba completada - Bytes capturados: " + totalBytes);
            
        } catch (Exception e) {
            System.err.println("Error en prueba de microfono: " + e.getMessage());
        }
    }

    private static double calcularNivelAudio(byte[] audioData, int length) {
        if (length < 2) return 0;
        
        long suma = 0;
        for (int i = 0; i < length - 1; i += 2) {
            short muestra = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            suma += Math.abs(muestra);
        }
        
        double promedio = (double) suma / (length / 2);
        return Math.min(promedio / Short.MAX_VALUE, 1.0);
    }

    public static void diagnostico() {
        System.out.println("\nDIAGNOSTICO DE AUDIO CALL SENDER:");
        System.out.println("   Estado: " + (enviando ? "ACTIVO" : "INACTIVO"));
        System.out.println("   Tipo llamada: " + tipoLlamada);
        System.out.println("   Destinos: " + destinos.size());
        System.out.println("   Socket: " + (socket != null ? "CONECTADO" : "DESCONECTADO"));
        System.out.println("   Microfono: " + (microfono != null ? "ABIERTO" : "CERRADO"));
        System.out.println("   Paquetes enviados: " + paquetesEnviados);
        System.out.println("   Bytes enviados: " + bytesEnviados);
    }
}