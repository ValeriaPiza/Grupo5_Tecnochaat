package com.tecnochat.client;

import javax.sound.sampled.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientAudioReceiver {

    private static final Scanner SCANNER = new Scanner(System.in);

    public static void recibirAudio(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            DataInputStream dis = new DataInputStream(is);
            
            String nombreArchivo = dis.readUTF();
            long tamanoArchivo = dis.readLong();

            System.out.println("Recibiendo nota de voz: " + nombreArchivo + " (" + tamanoArchivo + " bytes)");

            File carpetaAudios = new File("audios_recibidos");
            if (!carpetaAudios.exists()) {
                if (carpetaAudios.mkdirs()) {
                    System.out.println("Carpeta de audios creada: " + carpetaAudios.getAbsolutePath());
                }
            }

            String nombreUnico = System.currentTimeMillis() + "_" + nombreArchivo;
            File archivoAudio = new File(carpetaAudios, nombreUnico);

            try (FileOutputStream fos = new FileOutputStream(archivoAudio);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[4096];
                long bytesRecibidos = 0;
                int bytesLeidos;

                System.out.println("Descargando audio...");

                while (bytesRecibidos < tamanoArchivo && (bytesLeidos = dis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesLeidos);
                    bytesRecibidos += bytesLeidos;
                    
                    if (tamanoArchivo > 100000) {
                        int porcentaje = (int) ((bytesRecibidos * 100) / tamanoArchivo);
                        if (porcentaje % 25 == 0) {
                            System.out.println("Progreso: " + porcentaje + "%");
                        }
                    }
                }

                bos.flush();
            }

            if (archivoAudio.length() != tamanoArchivo) {
                System.err.println("Error: El archivo recibido esta incompleto.");
                System.err.println("   Esperado: " + tamanoArchivo + " bytes");
                System.err.println("   Recibido: " + archivoAudio.length() + " bytes");
                
                if (!archivoAudio.delete()) {
                    System.err.println("No se pudo eliminar el archivo corrupto.");
                }
                return;
            }

            System.out.println("Audio guardado: " + nombreUnico + " (" + archivoAudio.length() + " bytes)");

            manejarReproduccionAudio(archivoAudio);

        } catch (IOException e) {
            System.err.println("Error recibiendo audio: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error inesperado al recibir audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void manejarReproduccionAudio(File archivoAudio) {        
        try {
            System.out.println("\nQue deseas hacer con el audio recibido?");
            System.out.println("1. Reproducir ahora");
            System.out.println("2. Reproducir mas tarde");
            System.out.println("3. Solo guardar (no reproducir)");
            System.out.println("4. Ver informacion del audio");
            System.out.print("Elige opcion: ");
            
            String opcion = SCANNER.nextLine().trim();

            switch (opcion) {
                case "1":
                    reproducirAudioInmediato(archivoAudio);
                    break;
                case "2":
                    System.out.println("Audio guardado para reproducir despues: " + archivoAudio.getName());
                    System.out.println("Usa la opcion 'Reproducir audio guardado' del menu principal");
                    break;
                case "3":
                    System.out.println("Audio guardado: " + archivoAudio.getName());
                    break;
                case "4":
                    mostrarInformacionAudio(archivoAudio);
                    System.out.print("Reproducir ahora? (S/N): ");
                    String respuesta = SCANNER.nextLine().trim();
                    if (respuesta.equalsIgnoreCase("S")) {
                        reproducirAudioInmediato(archivoAudio);
                    }
                    break;
                default:
                    System.out.println("Opcion no valida. Reproduciendo audio...");
                    reproducirAudioInmediato(archivoAudio);
                    break;
            }

        } catch (Exception e) {
            System.err.println("Error en el menu de reproduccion: " + e.getMessage());
        }
    }

    private static void reproducirAudioInmediato(File archivoAudio) {
        try {
            System.out.println("Reproduciendo audio...");
            
            if (!archivoAudio.exists()) {
                System.err.println("El archivo de audio no existe: " + archivoAudio.getPath());
                return;
            }

            if (archivoAudio.length() == 0) {
                System.err.println("El archivo de audio esta vacio.");
                return;
            }

            boolean exito = AudioPlayer.reproducirAudio(archivoAudio);
            
            if (exito) {
                System.out.println("Reproduccion completada.");
                
                System.out.print("Reproducir nuevamente? (S/N): ");
                String respuesta = SCANNER.nextLine().trim();
                
                if (respuesta.equalsIgnoreCase("S")) {
                    System.out.println("Reproduciendo nuevamente...");
                    AudioPlayer.reproducirAudio(archivoAudio);
                }
            } else {
                System.err.println("Error en la reproduccion del audio.");
            }

        } catch (Exception e) {
            System.err.println("Error al reproducir audio: " + e.getMessage());
        }
    }

    private static void mostrarInformacionAudio(File archivoAudio) {
        try {
            System.out.println("\nINFORMACION DEL AUDIO:");
            System.out.println("Archivo: " + archivoAudio.getName());
            System.out.println("Tamano: " + archivoAudio.length() + " bytes");
            System.out.println("Modificado: " + new java.util.Date(archivoAudio.lastModified()));
            System.out.println("Ruta: " + archivoAudio.getAbsolutePath());

            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(archivoAudio)) {
                AudioFormat format = audioInputStream.getFormat();
                System.out.println("Formato: " + format.toString());
                System.out.println("Sample Rate: " + format.getSampleRate() + " Hz");
                System.out.println("Canales: " + format.getChannels());
                System.out.println("Sample Size: " + format.getSampleSizeInBits() + " bits");
                
                long frames = audioInputStream.getFrameLength();
                double duration = (frames / format.getFrameRate());
                System.out.println("Duracion: " + String.format("%.2f", duration) + " segundos");
                
            } catch (UnsupportedAudioFileException e) {
                System.out.println("No se pudo leer la informacion del formato de audio");
            } catch (IOException e) {
                System.out.println("Error al leer el archivo de audio");
            }

        } catch (Exception e) {
            System.err.println("Error al obtener informacion del audio: " + e.getMessage());
        }
    }

    public static void recibirAudioSilencioso(Socket socket) {
        try {
            InputStream is = socket.getInputStream();
            DataInputStream dis = new DataInputStream(is);
            
            String nombreArchivo = dis.readUTF();
            long tamanoArchivo = dis.readLong();

            System.out.println("Recibiendo audio en segundo plano: " + nombreArchivo);

            File carpetaAudios = new File("audios_recibidos");
            if (!carpetaAudios.exists()) {
                carpetaAudios.mkdirs();
            }

            String nombreUnico = System.currentTimeMillis() + "_" + nombreArchivo;
            File archivoAudio = new File(carpetaAudios, nombreUnico);

            try (FileOutputStream fos = new FileOutputStream(archivoAudio);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[4096];
                long bytesRecibidos = 0;
                int bytesLeidos;

                while (bytesRecibidos < tamanoArchivo && (bytesLeidos = dis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesLeidos);
                    bytesRecibidos += bytesLeidos;
                }
                bos.flush();
            }

            System.out.println("Audio guardado silenciosamente: " + nombreUnico);

        } catch (IOException e) {
            System.err.println("Error recibiendo audio silencioso: " + e.getMessage());
        }
    }

    public static void listarAudiosRecibidos() {
        try {
            File carpetaAudios = new File("audios_recibidos");
            if (!carpetaAudios.exists() || !carpetaAudios.isDirectory()) {
                System.out.println("No hay carpeta de audios recibidos.");
                return;
            }

            File[] archivosAudio = carpetaAudios.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".wav") || 
                name.toLowerCase().endsWith(".mp3") ||
                name.toLowerCase().endsWith(".aiff"));

            if (archivosAudio == null || archivosAudio.length == 0) {
                System.out.println("No hay audios recibidos.");
                return;
            }

            System.out.println("\nAUDIOS RECIBIDOS (" + archivosAudio.length + " archivos):");
            System.out.println("==========================================");

            for (int i = 0; i < archivosAudio.length; i++) {
                File audio = archivosAudio[i];
                String tamano = formatFileSize(audio.length());
                String fecha = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                                  .format(new java.util.Date(audio.lastModified()));
                
                System.out.println((i + 1) + ". " + audio.getName());
                System.out.println("   " + tamano + " | " + fecha);
                
                if ((i + 1) % 5 == 0 && i < archivosAudio.length - 1) {
                    System.out.println("------------------------------------------");
                }
            }

            System.out.print("\nReproducir un audio? (numero o 'N' para salir): ");
            String input = SCANNER.nextLine().trim();
            
            if (!input.equalsIgnoreCase("N")) {
                try {
                    int index = Integer.parseInt(input) - 1;
                    if (index >= 0 && index < archivosAudio.length) {
                        File audioSeleccionado = archivosAudio[index];
                        System.out.println("Reproduciendo: " + audioSeleccionado.getName());
                        reproducirAudioInmediato(audioSeleccionado);
                    } else {
                        System.out.println("Numero fuera de rango.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Entrada no valida.");
                }
            }

        } catch (Exception e) {
            System.err.println("Error listando audios: " + e.getMessage());
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    public static void limpiarAudiosAntiguos(int dias) {
        try {
            File carpetaAudios = new File("audios_recibidos");
            if (!carpetaAudios.exists()) {
                System.out.println("No hay carpeta de audios para limpiar.");
                return;
            }

            File[] archivosAudio = carpetaAudios.listFiles();
            if (archivosAudio == null || archivosAudio.length == 0) {
                System.out.println("No hay audios para limpiar.");
                return;
            }

            long tiempoLimite = System.currentTimeMillis() - (dias * 24L * 60 * 60 * 1000);
            int archivosEliminados = 0;

            for (File audio : archivosAudio) {
                if (audio.lastModified() < tiempoLimite) {
                    if (audio.delete()) {
                        archivosEliminados++;
                        System.out.println("Eliminado: " + audio.getName());
                    } else {
                        System.err.println("No se pudo eliminar: " + audio.getName());
                    }
                }
            }

            System.out.println("Limpieza completada: " + archivosEliminados + " archivos eliminados.");

        } catch (Exception e) {
            System.err.println("Error en limpieza de audios: " + e.getMessage());
        }
    }

    public interface AudioReceivedCallback {
        void onAudioReceived(File audioFile);
        void onError(String errorMessage);
    }

    public static void recibirAudioConCallback(Socket socket, AudioReceivedCallback callback) {
        new Thread(() -> {
            try {
                InputStream is = socket.getInputStream();
                DataInputStream dis = new DataInputStream(is);
                
                String nombreArchivo = dis.readUTF();
                long tamanoArchivo = dis.readLong();

                File carpetaAudios = new File("audios_recibidos");
                if (!carpetaAudios.exists()) {
                    carpetaAudios.mkdirs();
                }

                String nombreUnico = System.currentTimeMillis() + "_" + nombreArchivo;
                File archivoAudio = new File(carpetaAudios, nombreUnico);

                try (FileOutputStream fos = new FileOutputStream(archivoAudio);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                    byte[] buffer = new byte[4096];
                    long bytesRecibidos = 0;
                    int bytesLeidos;

                    while (bytesRecibidos < tamanoArchivo && (bytesLeidos = dis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesLeidos);
                        bytesRecibidos += bytesLeidos;
                    }
                    bos.flush();
                }

                if (archivoAudio.length() == tamanoArchivo) {
                    callback.onAudioReceived(archivoAudio);
                } else {
                    callback.onError("Archivo incompleto: " + archivoAudio.length() + "/" + tamanoArchivo + " bytes");
                }

            } catch (IOException e) {
                callback.onError("Error recibiendo audio: " + e.getMessage());
            }
        }).start();
    }
}