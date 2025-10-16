package Client;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class AudioSender {

    public static void grabarYEnviarAudio(Socket socket, Scanner scanner2, boolean esGrupo) {
        try {
            Scanner scanner = new Scanner(System.in);

            String destino = null;
            if (scanner2 == null) {
                System.out.println(esGrupo ? "Ingresa el nombre del grupo:" : "Ingresa el nombre del usuario:");
                destino = scanner.nextLine().trim();
            } else {
                destino = scanner2.nextLine().trim();
            }

            AudioFormat formato = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Micrófono no soportado");
                return;
            }

            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(formato);
            mic.start();

            System.out.println("Grabando... Presiona ENTER para detener");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            Thread stopper = new Thread(() -> {
                Scanner stopScanner = new Scanner(System.in);
                stopScanner.nextLine();
                mic.stop();
                mic.close();
            });
            stopper.start();

            while (mic.isOpen()) {
                int count = mic.read(buffer, 0, buffer.length);
                if (count > 0) {
                    out.write(buffer, 0, count);
                }
            }
            stopper.join();

            byte[] audioData = out.toByteArray();
            File archivoWav = new File("temp_audio.wav");
            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, formato, audioData.length / formato.getFrameSize())) {
            enviarArchivoAudio(socket, destino, esGrupo, archivoWav);
            }

            enviarArchivoAudio(socket, destino, esGrupo, archivoWav);

            archivoWav.delete();

            System.out.println("Audio enviado correctamente.");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void enviarArchivoAudio(Socket socket, String destino, boolean esGrupo, File archivo) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

        out.println(esGrupo ? "6" : "5");

        out.println(destino);

        dataOut.writeUTF(archivo.getName());
        dataOut.writeLong(archivo.length());

        // Enviar el archivo
        try (FileInputStream fis = new FileInputStream(archivo)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = fis.read(buffer)) > 0) {
                dataOut.write(buffer, 0, count);
            }
            dataOut.flush();
        }
    }

    public static void reproducirAudioDesdeBytes(byte[] audioBytes) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(
                new java.io.ByteArrayInputStream(audioBytes))) {

            AudioFormat formato = ais.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            SourceDataLine linea = (SourceDataLine) AudioSystem.getLine(info);

            linea.open(formato);
            linea.start();

            byte[] buffer = new byte[4096];
            int bytesLeidos;
            while ((bytesLeidos = ais.read(buffer, 0, buffer.length)) != -1) {
                linea.write(buffer, 0, bytesLeidos);
            }

            linea.drain();
            linea.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
