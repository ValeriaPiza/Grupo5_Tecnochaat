package Client;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;

public class ClientAudioReceiver {

    public static void recibirAudio(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            DataInputStream dis = new DataInputStream(is);
            String nombreArchivo = dis.readUTF();

            File archivo = new File("audios_recibidos");
            if (!archivo.exists()) {
                archivo.mkdir();
            }

            File audio = new File(archivo, nombreArchivo);
            FileOutputStream fos = new FileOutputStream(audio);

            byte[] buffer = new byte[4096];
            int bytesLeidos;
            while ((bytesLeidos = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesLeidos);
                if (bytesLeidos < 4096) break;
            }
            fos.close();

            System.out.println("Nota de voz recibida: " + nombreArchivo);
            System.out.print("¿Deseas escucharla? (S/N): ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String respuesta = reader.readLine();
            if (respuesta.equalsIgnoreCase("S")) {
                AudioPlayer.reproducirAudio(audio);
            }
        } catch (IOException e) {
            System.err.println("Error recibiendo audio: " + e.getMessage());
        }
    }
}
