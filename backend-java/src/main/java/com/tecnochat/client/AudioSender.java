package com.tecnochat.client;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioSender {

    private static final Scanner SCANNER = new Scanner(System.in);

    public static void grabarYEnviarAudio(Socket socket, Scanner scanner2, boolean esGrupo) {
        try {

            String destino = null;
            if (scanner2 == null) {
                System.out.println(esGrupo ? "Ingresa el nombre del grupo:" : "Ingresa el nombre del usuario:");
                destino = SCANNER.nextLine().trim();
            } else {
                destino = scanner2.nextLine().trim();
            }

            if (destino == null || destino.isEmpty()) {
                System.out.println("Destino no valido.");
                return;
            }

            System.out.println("Destino: " + destino + " (" + (esGrupo ? "Grupo" : "Usuario") + ")");

            AudioFormat formato = getOptimalAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
            
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microfono no soportado. Probando formato alternativo...");
                formato = getFallbackAudioFormat();
                info = new DataLine.Info(TargetDataLine.class, formato);
                
                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("No se pudo encontrar un microfono compatible.");
                    return;
                }
            }

            System.out.println("Configurando microfono...");
            System.out.println("   Formato: " + formato.getSampleRate() + "Hz, " + 
                              formato.getSampleSizeInBits() + "bits, " + 
                              formato.getChannels() + " canal(es)");

            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(formato);
            mic.start();

            System.out.println("\n=== GRABACION INICIADA ===");
            System.out.println("Grabando... Presiona ENTER para detener");
            System.out.println("Habla claramente y manten el microfono cerca");

            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            final AtomicBoolean grabando = new AtomicBoolean(true);
            final long inicioGrabacion = System.currentTimeMillis();

            Thread progressThread = new Thread(() -> {
                try {
                    while (grabando.get()) {
                        Thread.sleep(1000);
                        long tiempoTranscurrido = (System.currentTimeMillis() - inicioGrabacion) / 1000;
                        System.out.printf("Grabando... %d segundos\r", tiempoTranscurrido);
                    }
                } catch (InterruptedException e) {
                    // Thread interrumpido
                }
            });
            progressThread.setDaemon(true);
            progressThread.start();

            Thread stopper = new Thread(() -> {
                try {
                    SCANNER.nextLine();
                    grabando.set(false);
                    mic.stop();
                    mic.close();
                    System.out.println("\nGrabacion detenida");
                } catch (Exception e) {
                    System.err.println("Error en control de grabacion: " + e.getMessage());
                }
            });
            stopper.start();

            int totalBytesGrabados = 0;
            int buffersVacios = 0;
            final int MAX_BUFFERS_VACIOS = 10;

            while (grabando.get() && mic.isOpen()) {
                int bytesLeidos = mic.read(buffer, 0, buffer.length);
                
                if (bytesLeidos > 0) {
                    audioBuffer.write(buffer, 0, bytesLeidos);
                    totalBytesGrabados += bytesLeidos;
                    buffersVacios = 0;
                } else {
                    buffersVacios++;
                    if (buffersVacios > MAX_BUFFERS_VACIOS) {
                        System.out.println("\nNo se detecta audio. Verifica el microfono.");
                        break;
                    }
                }
                
                Thread.sleep(10);
            }

            stopper.join(2000);
            if (stopper.isAlive()) {
                stopper.interrupt();
            }

            progressThread.interrupt();

            byte[] audioData = audioBuffer.toByteArray();
            long duracion = (System.currentTimeMillis() - inicioGrabacion) / 1000;

            System.out.println("\nGRABACION FINALIZADA:");
            System.out.println("   Duracion: " + duracion + " segundos");
            System.out.println("   Datos: " + (audioData.length / 1024) + " KB");
            System.out.println("   Calidad: " + (audioData.length > 0 ? "BUENA" : "VACIA"));

            if (audioData.length == 0) {
                System.out.println("No se grabo audio. Verifica el microfono.");
                return;
            }

            if (audioData.length < 1024) {
                System.out.println("Grabacion muy corta. Estas seguro de enviarla? (S/N)");
                String confirmacion = SCANNER.nextLine();
                if (!confirmacion.equalsIgnoreCase("S")) {
                    System.out.println("Envio cancelado.");
                    return;
                }
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            String nombreArchivo = "audio_msg_" + timestamp + ".wav";
            File archivoWav = new File(nombreArchivo);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, formato, audioData.length / formato.getFrameSize())) {
                
                System.out.println("Guardando audio temporal...");
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, archivoWav);
            }

            System.out.println("Enviando audio a " + destino + "...");
            boolean enviado = enviarArchivoAudio(socket, destino, esGrupo, archivoWav);

            if (enviado) {
                System.out.println("Audio enviado correctamente a " + destino);
                
                System.out.println("Resumen:");
                System.out.println("   Destino: " + destino);
                System.out.println("   Tipo: " + (esGrupo ? "Grupo" : "Privado"));
                System.out.println("   Duracion: " + duracion + "s");
                System.out.println("   Tamano: " + (archivoWav.length() / 1024) + " KB");
                System.out.println("   Calidad: " + getCalidadAudio(audioData.length, duracion));
            } else {
                System.out.println("Error al enviar el audio.");
            }

            if (archivoWav.exists()) {
                if (archivoWav.delete()) {
                    System.out.println("Archivo temporal eliminado");
                } else {
                    System.err.println("No se pudo eliminar el archivo temporal: " + archivoWav.getName());
                }
            }

        } catch (LineUnavailableException e) {
            System.err.println("Error: Microfono no disponible.");
            System.err.println("Soluciones:");
            System.err.println("   - Verifica que el microfono este conectado");
            System.err.println("   - Asegurate de que no este siendo usado por otra aplicacion");
            System.err.println("   - Verifica los permisos del sistema");
        } catch (Exception e) {
            System.err.println("Error al grabar/enviar audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static AudioFormat getOptimalAudioFormat() {
        AudioFormat[] formatosPreferidos = {
            new AudioFormat(16000, 16, 1, true, false),
            new AudioFormat(44100, 16, 1, true, false),
            new AudioFormat(8000, 16, 1, true, false),
            new AudioFormat(16000, 8, 1, true, false)
        };

        for (AudioFormat formato : formatosPreferidos) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
            if (AudioSystem.isLineSupported(info)) {
                System.out.println("Formato seleccionado: " + formato.getSampleRate() + "Hz");
                return formato;
            }
        }

        return formatosPreferidos[formatosPreferidos.length - 1];
    }

    private static AudioFormat getFallbackAudioFormat() {
        System.out.println("Usando formato de audio basico...");
        return new AudioFormat(8000, 8, 1, true, false);
    }

    private static double calcularNivelAudio(byte[] audioData, int length) {
        if (length < 2) return 0;
        
        long suma = 0;
        int muestras = 0;
        
        for (int i = 0; i < length - 1; i += 2) {
            short muestra = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            suma += Math.abs(muestra);
            muestras++;
        }
        
        if (muestras == 0) return 0;
        double promedio = (double) suma / muestras;
        return Math.min(promedio / Short.MAX_VALUE, 1.0);
    }

    private static String getCalidadAudio(int bytes, long duracion) {
        if (duracion == 0) return "DESCONOCIDA";
        
        double bytesPorSegundo = bytes / duracion;
        double kbps = (bytesPorSegundo * 8) / 1024;
        
        if (kbps > 128) return "EXCELENTE";
        if (kbps > 64) return "BUENA";
        if (kbps > 32) return "ACEPTABLE";
        return "BAJA";
    }

    private static boolean enviarArchivoAudio(Socket socket, String destino, boolean esGrupo, File archivo) {
        long totalEnviado = 0;
        
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

            String comando = esGrupo ? "6" : "5";
            out.println(comando);
            
            Thread.sleep(50);

            out.println(destino);

            if (!archivo.exists()) {
                System.err.println("El archivo de audio no existe: " + archivo.getPath());
                return false;
            }

            if (archivo.length() == 0) {
                System.err.println("El archivo de audio esta vacio");
                return false;
            }

            if (archivo.length() > 10 * 1024 * 1024) {
                System.err.println("El archivo es demasiado grande: " + (archivo.length() / 1024 / 1024) + "MB");
                return false;
            }

            System.out.println("Enviando archivo: " + archivo.getName() + " (" + archivo.length() + " bytes)");

            dataOut.writeUTF(archivo.getName());
            dataOut.writeLong(archivo.length());
            dataOut.flush();

            try (FileInputStream fis = new FileInputStream(archivo);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                long tamanoTotal = archivo.length();
                totalEnviado = 0;

                while ((bytesLeidos = bis.read(buffer)) > 0) {
                    dataOut.write(buffer, 0, bytesLeidos);
                    totalEnviado += bytesLeidos;
                    
                    if (tamanoTotal > 100000) {
                        int porcentaje = (int) ((totalEnviado * 100) / tamanoTotal);
                        if (porcentaje % 25 == 0) {
                            System.out.println("Progreso de envio: " + porcentaje + "%");
                        }
                    }
                }
                dataOut.flush();
            }

            System.out.println("Archivo enviado exitosamente: " + totalEnviado + " bytes");
            return true;

        } catch (Exception e) {
            System.err.println("Error enviando archivo de audio: " + e.getMessage());
            return false;
        }
    }

    public static File grabarAudioLocal() {
        try {
            
            AudioFormat formato = getOptimalAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microfono no soportado");
                return null;
            }

            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(formato);
            mic.start();

            System.out.println("Grabando audio local... Presiona ENTER para detener");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            final boolean[] grabando = {true};

            Thread stopper = new Thread(() -> {
                try {
                    SCANNER.nextLine();
                    grabando[0] = false;
                    mic.stop();
                    mic.close();
                    System.out.println("Grabacion local detenida");
                } catch (Exception e) {
                    System.err.println("Error en control de grabacion local: " + e.getMessage());
                }
            });
            stopper.start();

            while (grabando[0] && mic.isOpen()) {
                int count = mic.read(buffer, 0, buffer.length);
                if (count > 0) {
                    out.write(buffer, 0, count);
                }
            }

            stopper.join(1000);
            
            byte[] audioData = out.toByteArray();
            if (audioData.length == 0) {
                System.out.println("No se grabo audio");
                return null;
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            File archivoWav = new File("audio_local_" + timestamp + ".wav");

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, formato, audioData.length / formato.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, archivoWav);
            }

            System.out.println("Audio guardado localmente: " + archivoWav.getName());
            return archivoWav;

        } catch (Exception e) {
            System.err.println("Error grabando audio local: " + e.getMessage());
            return null;
        }
    }

    public static boolean enviarArchivoExistente(Socket socket, File archivoAudio, String destino, boolean esGrupo) {
        if (!archivoAudio.exists() || !archivoAudio.isFile()) {
            System.err.println("El archivo no existe: " + archivoAudio.getPath());
            return false;
        }

        if (!archivoAudio.getName().toLowerCase().endsWith(".wav")) {
            System.err.println("Solo se permiten archivos WAV");
            return false;
        }

        System.out.println("Enviando archivo existente: " + archivoAudio.getName());
        return enviarArchivoAudio(socket, destino, esGrupo, archivoAudio);
    }

    public static void reproducirAudioDesdeBytes(byte[] audioBytes) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(audioBytes))) {

            AudioFormat formato = ais.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            SourceDataLine linea = (SourceDataLine) AudioSystem.getLine(info);

            linea.open(formato);
            linea.start();

            System.out.println("Reproduciendo audio desde memoria...");

            byte[] buffer = new byte[4096];
            int bytesLeidos;
            while ((bytesLeidos = ais.read(buffer, 0, buffer.length)) != -1) {
                linea.write(buffer, 0, bytesLeidos);
            }

            linea.drain();
            linea.close();

            System.out.println("Reproduccion desde memoria completada");

        } catch (Exception e) {
            System.err.println("Error reproduciendo audio desde bytes: " + e.getMessage());
        }
    }

    public static void mostrarFormatosSoportados() {
        System.out.println("\nFORMATOS DE AUDIO SOPORTADOS:");
        
        AudioFormat[] formatosTest = {
            new AudioFormat(44100, 16, 2, true, false),
            new AudioFormat(44100, 16, 1, true, false),
            new AudioFormat(22050, 16, 1, true, false),
            new AudioFormat(16000, 16, 1, true, false),
            new AudioFormat(8000, 16, 1, true, false),
            new AudioFormat(8000, 8, 1, true, false)
        };

        for (AudioFormat formato : formatosTest) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
            boolean soportado = AudioSystem.isLineSupported(info);
            System.out.printf("   %5.1f kHz, %2d bits, %d canal(es): %s%n",
                formato.getSampleRate() / 1000.0,
                formato.getSampleSizeInBits(),
                formato.getChannels(),
                soportado ? "SOPORTADO" : "NO SOPORTADO");
        }
    }

    public static void diagnosticoAudio() {
        System.out.println("\nDIAGNOSTICO DEL SISTEMA DE AUDIO:");
        
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        System.out.println("Mixers disponibles: " + mixers.length);
        
        for (Mixer.Info mixerInfo : mixers) {
            System.out.println("   - " + mixerInfo.getName());
            System.out.println("     " + mixerInfo.getDescription());
        }

        System.out.println("\nLINEAS DE CAPTURA (Microfonos):");
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] lineInfos = mixer.getTargetLineInfo();
            
            for (Line.Info lineInfo : lineInfos) {
                if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
                    System.out.println("   " + mixerInfo.getName() + " - CAPTURA DISPONIBLE");
                }
            }
        }

        mostrarFormatosSoportados();
    }
}