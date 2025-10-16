package Client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioCallSender {
    private static volatile boolean enviando = true;

    public static void iniciarLlamada(String ipDestino, int puertoDestino) {
        enviando = true;
        DatagramSocket socket = null;
        TargetDataLine microfono = null;

        try {
            System.out.println("=== INICIANDO ENVÍO DE AUDIO ===");
            System.out.println("Destino: " + ipDestino + ":" + puertoDestino);

            // Usar un formato más compatible
            AudioFormat formato = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Línea de audio no soportada. Probando formato alternativo...");
                formato = new AudioFormat(8000.0f, 16, 1, true, false);
                info = new DataLine.Info(TargetDataLine.class, formato);
            }

            microfono = (TargetDataLine) AudioSystem.getLine(info);
            microfono.open(formato);
            microfono.start();

            socket = new DatagramSocket();
            byte[] buffer = new byte[1024]; // Buffer más pequeño

            System.out.println("Enviando audio...");

            while (enviando) {
                int bytesRead = microfono.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    DatagramPacket paquete = new DatagramPacket(buffer, bytesRead,
                            InetAddress.getByName(ipDestino), puertoDestino);
                    socket.send(paquete);
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR en AudioCallSender: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (microfono != null) {
                microfono.stop();
                microfono.close();
            }
            if (socket != null) {
                socket.close();
            }
            System.out.println("Envío de audio finalizado.");
        }
    }

    public static void terminarLlamada() {
        enviando = false;
    }
}