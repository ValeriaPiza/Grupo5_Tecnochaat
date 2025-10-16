package Client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class AudioCallReceiver {
    private static volatile boolean recibiendo = true;

    public static void iniciarRecepcion(int puertoEscucha) {
        recibiendo = true;
        DatagramSocket socket = null;
        SourceDataLine altavoz = null;

        try {
            // Usar el mismo formato que el sender
            AudioFormat formato = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Línea de audio no soportada. Probando formato alternativo...");
                formato = new AudioFormat(8000.0f, 16, 1, true, false);
                info = new DataLine.Info(SourceDataLine.class, formato);
            }

            altavoz = (SourceDataLine) AudioSystem.getLine(info);
            altavoz.open(formato);
            altavoz.start();

            socket = new DatagramSocket(puertoEscucha);
            byte[] buffer = new byte[1024];

            System.out.println("Esperando audio entrante en el puerto " + puertoEscucha + "...");

            while (recibiendo) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                altavoz.write(paquete.getData(), 0, paquete.getLength());
            }

        } catch (Exception e) {
            System.err.println("ERROR en AudioCallReceiver: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (altavoz != null) {
                altavoz.stop();
                altavoz.close();
            }
            if (socket != null) {
                socket.close();
            }
            System.out.println("Recepción de audio finalizada.");
        }
    }

    public static void terminarRecepcion() {
        recibiendo = false;
    }
}