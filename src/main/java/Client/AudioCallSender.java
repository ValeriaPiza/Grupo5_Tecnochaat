package Client;

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
    
    private static final int BUFFER_SIZE = 512;
    private static final int SAMPLE_RATE = 8000;
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

    public static void prepararNuevaLlamada() {
        destinos.clear();
        System.out.println("Lista de destinos limpiada para nueva llamada");
    }

    public static void agregarDestinoLlamada(String ip, int puerto) {
        for (Destino destino : destinos) {
            if (destino.ip.equals(ip) && destino.puerto == puerto) {
                System.out.println("Destino ya existe: " + destino);
                return;
            }
        }
        
        Destino nuevoDestino = new Destino(ip, puerto);
        destinos.add(nuevoDestino);
        System.out.println("Destino agregado: " + nuevoDestino);
    }

    public static void iniciarLlamadaIndividual(String ipDestino, int puertoDestino) {
        prepararNuevaLlamada();
        agregarDestinoLlamada(ipDestino, puertoDestino);
        System.out.println("Iniciando llamada...");
        iniciarLlamada("INDIVIDUAL", "");
    }

    public static void iniciarLlamadaGrupal(String idLlamadaGrupal) {
        if (destinos.isEmpty()) {
            System.err.println("Error: No hay destinos configurados para la llamada grupal");
            System.out.println("   Destinos actuales: " + destinos.size());
            return;
        }
        
        System.out.println("INICIANDO LLAMADA GRUPAL con " + destinos.size() + " destinos:");
        for (Destino destino : destinos) {
            System.out.println("   " + destino);
        }
        
        iniciarLlamada("GRUPAL", idLlamadaGrupal);
    }

    private static void iniciarLlamada(String tipo, String idLlamadaEspecifica) {
        if (destinos.isEmpty()) {
            System.err.println("Error: No hay destinos configurados");
            return;
        }

        AudioCallSender.tipoLlamada = tipo;
        AudioCallSender.idLlamada = idLlamadaEspecifica;
        enviando = true;

        System.out.println("\n=== INICIANDO " + (tipo.equals("GRUPAL") ? "LLAMADA GRUPAL" : "LLAMADA INDIVIDUAL") + " ===");
        System.out.println("ID Llamada: " + (idLlamadaEspecifica.isEmpty() ? "N/A" : idLlamadaEspecifica));
        System.out.println("Enviando a " + destinos.size() + " destinatarios:");
        for (Destino destino : destinos) {
            System.out.println("   " + destino);
        }

        Thread senderThread = new Thread(() -> {
            ejecutarEnvioAudio();
        });
        
        String threadName = "AudioSender-" + tipo + "-" + 
                           (idLlamadaEspecifica.isEmpty() ? "IND" : idLlamadaEspecifica);
        senderThread.setName(threadName);
        senderThread.start();
    }


private static void ejecutarEnvioAudio() {
        try {
            AudioFormat formato = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println(" Formato no soportado, probando alternativo...");
                formato = new AudioFormat(16000.0f, 16, 1, true, false);
                info = new DataLine.Info(TargetDataLine.class, formato);
                
                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("No se pudo encontrar un formato de audio compatible");
                    return;
                }
            }

            System.out.println(" Creando socket de envío...");
            socket = new DatagramSocket();
            socket.setSoTimeout(1000);
            microfono = (TargetDataLine) AudioSystem.getLine(info);
            microfono.open(formato);
            microfono.start();

            System.out.println("Micrófono configurado:");
            System.out.println("   Sample Rate: " + formato.getSampleRate() + " Hz");
            System.out.println("   Buffer Size: " + BUFFER_SIZE + " bytes");


            byte[] buffer = new byte[BUFFER_SIZE];
            inicioEnvio = System.currentTimeMillis();

            System.out.println("ENVÍO INICIADO - Puerto destino: " + 
                          (destinos.isEmpty() ? "N/A" : destinos.get(0).puerto));

            System.out.println("Escribe '10' en el menú para terminar");

            while (enviando && microfono.isOpen()) {
                try {
                    int bytesLeidos = microfono.read(buffer, 0, buffer.length);
                    
                    if (bytesLeidos > 0) {
                        if (paquetesEnviados == 0) {
                            System.out.println("PRIMER PAQUETE ENVIADO! - " + bytesLeidos + " bytes");
                        }
                        for (Destino destino : destinos) {
                            try {
                                DatagramPacket paquete = new DatagramPacket(
                                    buffer, bytesLeidos,
                                    InetAddress.getByName(destino.ip), destino.puerto
                                );
                                socket.send(paquete);
                                
                                destino.paquetesEnviados++;
                                destino.bytesEnviados += bytesLeidos;
                            } catch (Exception e) {
                                System.err.println("Error enviando a " + destino + ": " + e.getMessage());
                            }
                        }
                        
                        paquetesEnviados++;
                        bytesEnviados += bytesLeidos;
                        if (paquetesEnviados % 10 == 0) {
                            System.out.printf("Paquetes enviados: %d\r", paquetesEnviados);
                        }
                    }
                    
                } catch (Exception e) {
                    if (enviando) {
                        System.err.println("Error en envío: " + e.getMessage());
                    }
                }
            }

        } catch (LineUnavailableException e) {
            System.err.println("Línea de audio no disponible: " + e.getMessage());
            System.err.println("   Verifica que el micrófono esté conectado");
        } catch (Exception e) {
            if (enviando) {
                System.err.println(" ERROR en AudioCallSender: " + e.getMessage());
            }
        } finally {
            cerrarRecursos();
            mostrarEstadisticasFinales();
        }
    }

    public static void terminarLlamada() {
        System.out.println("Terminando llamada...");
        enviando = false;
        cerrarRecursos();
    }

    public static boolean removerDestino(String ip, int puerto) {
        for (Destino destino : destinos) {
            if (destino.ip.equals(ip) && destino.puerto == puerto) {
                destinos.remove(destino);
                System.out.println(" Destino removido: " + destino);
                return true;
            }
        }
        return false;
    }

    public static List<Destino> getDestinos() {
        return new CopyOnWriteArrayList<>(destinos);
    }

    public static boolean isEnviando() {
        return enviando;
    }

    public static String getEstadisticas() {
        if (inicioEnvio == 0) return "No hay llamada activa";
        
        long tiempoTranscurrido = (System.currentTimeMillis() - inicioEnvio) / 1000;
        if (tiempoTranscurrido == 0) tiempoTranscurrido = 1;
        
        return String.format(
            "Envío %s - %d segundos - %d paquetes - %d KB - %d destinos",
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
            enviando ? "Sí" : "No"
        );
    }

    public static void diagnostico() {
        System.out.println("\nDIAGNÓSTICO DE AUDIO CALL SENDER:");
        System.out.println("   Estado: " + (enviando ? "ACTIVO" : "INACTIVO"));
        System.out.println("   Tipo llamada: " + tipoLlamada);
        System.out.println("   Destinos: " + destinos.size());
        for (Destino destino : destinos) {
            System.out.println("      - " + destino + " (" + destino.paquetesEnviados + " paquetes)");
        }
        System.out.println("   Socket: " + (socket != null ? "CONECTADO" : "DESCONECTADO"));
        System.out.println("   Micrófono: " + (microfono != null ? "ABIERTO" : "CERRADO"));
        System.out.println("   Paquetes enviados: " + paquetesEnviados);
        System.out.println("   Bytes enviados: " + bytesEnviados);
    }

    public static void probarMicrofono() {
        System.out.println("Probando micrófono...");
        try {
            AudioFormat formato = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
            TargetDataLine testLine = (TargetDataLine) AudioSystem.getLine(
                new DataLine.Info(TargetDataLine.class, formato));
            testLine.open(formato);
            testLine.start();
            
            byte[] buffer = new byte[BUFFER_SIZE];
            System.out.println("Habla ahora (3 segundos)...");
            
            long startTime = System.currentTimeMillis();
            int totalBytes = 0;
            int segmentosConAudio = 0;
            
            while (System.currentTimeMillis() - startTime < 3000) {
                int bytesRead = testLine.read(buffer, 0, buffer.length);
                totalBytes += bytesRead;
                
                if (bytesRead > 0) {
                    double nivel = calcularNivelAudio(buffer, bytesRead);
                    System.out.printf("Nivel: %.1f%%\r", nivel * 100);
                    if (nivel > 0.1) segmentosConAudio++;
                }
            }
            
            testLine.stop();
            testLine.close();
            
            System.out.println("\nPrueba completada:");
            System.out.println("   Bytes capturados: " + totalBytes);
            System.out.println("   Segmentos con audio: " + segmentosConAudio);
            System.out.println("   Estado: " + (segmentosConAudio > 5 ? "FUNCIONANDO" : "REVISAR MICRÓFONO"));
            
        } catch (Exception e) {
            System.err.println("Error en prueba de micrófono: " + e.getMessage());
        }
    }

    private static void mostrarEstadisticasFinales() {
        long tiempoTotal = (System.currentTimeMillis() - inicioEnvio) / 1000;
        if (tiempoTotal == 0) tiempoTotal = 1;
        
        System.out.println("\nESTADÍSTICAS FINALES:");
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   Duración: " + tiempoTotal + " segundos");
        System.out.println("   Paquetes enviados: " + paquetesEnviados);
        System.out.println("   Datos enviados: " + (bytesEnviados / 1024) + " KB");
        System.out.println("   Promedio: " + (paquetesEnviados / tiempoTotal) + " paquetes/segundo");
        
        if (tipoLlamada.equals("GRUPAL") && !destinos.isEmpty()) {
            System.out.println("   Destinos:");
            for (Destino destino : destinos) {
                System.out.println("      - " + destino + ": " + destino.paquetesEnviados + " paquetes");
            }
        }
    }

    private static void cerrarRecursos() {
        try {
            if (microfono != null) {
                microfono.stop();
                microfono.close();
                microfono = null;
                System.out.println("Micrófono cerrado");
            }
        } catch (Exception e) {
            if (microfono != null) {
                System.err.println("Error cerrando micrófono: " + e.getMessage());
            }
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
                System.out.println("🔌 Socket cerrado");
            }
        } catch (Exception e) {
            System.err.println("Error cerrando socket: " + e.getMessage());
        }
        enviando = false;
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

    public static void diagnosticoGrupal() {
        System.out.println("\nDIAGNÓSTICO LLAMADA GRUPAL:");
        System.out.println("   Estado: " + (enviando ? "ENVIANDO" : "DETENIDO"));
        System.out.println("   Tipo: " + tipoLlamada);
        System.out.println("   ID: " + (idLlamada.isEmpty() ? "N/A" : idLlamada));
        System.out.println("   Destinos configurados: " + destinos.size());
        
        if (destinos.isEmpty()) {
            System.out.println("  ERROR: No hay destinos configurados");
        } else {
            System.out.println("  Destinos activos:");
            for (Destino destino : destinos) {
                System.out.println("      - " + destino + " (" + destino.paquetesEnviados + " paquetes)");
            }
        }
        
        System.out.println("   Socket: " + (socket != null ? "CONECTADO" : "DESCONECTADO"));
        System.out.println("   Micrófono: " + (microfono != null ? "ABIERTO" : "CERRADO"));
        System.out.println("   Paquetes totales: " + paquetesEnviados);
    }
}