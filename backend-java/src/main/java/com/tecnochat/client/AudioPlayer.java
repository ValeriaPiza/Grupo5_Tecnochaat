package com.tecnochat.client;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioPlayer {
    private static Clip clipActual = null;
    private static volatile boolean reproduciendo = false;
    private static long inicioReproduccion = 0;
    
    private static long totalReproducciones = 0;
    private static long totalTiempoReproducido = 0;
    
    private static float volumen = 1.0f;
    private static FloatControl volumenControl = null;
    
    private static enum Estado { DETENIDO, REPRODUCIENDO, PAUSADO }
    private static Estado estadoActual = Estado.DETENIDO;
    
    public interface AudioPlayerCallback {
        void onReproduccionIniciada(File archivo);
        void onReproduccionFinalizada(File archivo);
        void onError(File archivo, String mensajeError);
        void onProgreso(File archivo, long tiempoActual, long duracionTotal);
    }
    
    private static AudioPlayerCallback callback = null;

    public static boolean reproducirAudio(File archivo) {
        return reproducirAudio(archivo, null);
    }

    public static boolean reproducirAudio(File archivo, AudioPlayerCallback callback) {
        AudioPlayer.callback = callback;
        
        if (!validarArchivoAudio(archivo)) {
            return false;
        }

        try {
            detenerReproduccion();
            
            System.out.println("Cargando archivo: " + archivo.getName());
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(archivo);
            AudioFormat formatoOriginal = audioStream.getFormat();
            
            AudioFormat formatoDecodificado = decodificarFormatoAudio(formatoOriginal);
            
            if (!formatoOriginal.matches(formatoDecodificado)) {
                System.out.println("Convertiendo formato de audio para mejor compatibilidad...");
                audioStream = AudioSystem.getAudioInputStream(formatoDecodificado, audioStream);
            }

            clipActual = AudioSystem.getClip();
            clipActual.open(audioStream);
            
            configurarControlesVolumen();
            configurarListenersReproduccion(archivo);
            
            mostrarInformacionAudio(archivo, formatoDecodificado);
            
            clipActual.start();
            reproduciendo = true;
            estadoActual = Estado.REPRODUCIENDO;
            inicioReproduccion = System.currentTimeMillis();
            totalReproducciones++;
            
            System.out.println("Reproduciendo audio...");
            System.out.println("Escribe 'pausar' para pausar, 'detener' para detener");
            
            if (callback != null) {
                callback.onReproduccionIniciada(archivo);
            }
            
            Thread monitorThread = new Thread(() -> {
                try {
                    while (reproduciendo && clipActual != null && clipActual.isRunning()) {
                        long tiempoActual = clipActual.getMicrosecondPosition() / 1000;
                        long duracionTotal = clipActual.getMicrosecondLength() / 1000;
                        
                        if (callback != null && duracionTotal > 0) {
                            callback.onProgreso(archivo, tiempoActual, duracionTotal);
                        }
                        
                        if (tiempoActual % 5000 < 100) {
                            mostrarProgreso(tiempoActual, duracionTotal);
                        }
                        
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    // Interrumpido
                }
            });
            monitorThread.setDaemon(true);
            monitorThread.start();
            
            while (clipActual != null && clipActual.isRunning()) {
                Thread.sleep(100);
            }
            
            if (reproduciendo) {
                long duracion = (System.currentTimeMillis() - inicioReproduccion);
                totalTiempoReproducido += duracion;
                
                System.out.println("Reproduccion completada - Duracion: " + 
                    formatDuracion(duracion));
                
                if (callback != null) {
                    callback.onReproduccionFinalizada(archivo);
                }
            }
            
            return true;

        } catch (UnsupportedAudioFileException e) {
            String errorMsg = "Formato de audio no soportado: " + e.getMessage();
            System.err.println(errorMsg);
            if (callback != null) {
                callback.onError(archivo, errorMsg);
            }
            return false;
        } catch (LineUnavailableException e) {
            String errorMsg = "Linea de audio no disponible: " + e.getMessage();
            System.err.println(errorMsg);
            if (callback != null) {
                callback.onError(archivo, errorMsg);
            }
            return false;
        } catch (IOException e) {
            String errorMsg = "Error de E/S al leer el archivo: " + e.getMessage();
            System.err.println(errorMsg);
            if (callback != null) {
                callback.onError(archivo, errorMsg);
            }
            return false;
        } catch (Exception e) {
            String errorMsg = "Error inesperado: " + e.getMessage();
            System.err.println(errorMsg);
            if (callback != null) {
                callback.onError(archivo, errorMsg);
            }
            return false;
        } finally {
            limpiarRecursos();
        }
    }

    private static boolean validarArchivoAudio(File archivo) {
        if (archivo == null) {
            System.err.println("Archivo nulo");
            return false;
        }
        
        if (!archivo.exists()) {
            System.err.println("Archivo no existe: " + archivo.getPath());
            return false;
        }
        
        if (!archivo.canRead()) {
            System.err.println("No se puede leer el archivo: " + archivo.getPath());
            return false;
        }
        
        if (archivo.length() == 0) {
            System.err.println("Archivo vacio: " + archivo.getPath());
            return false;
        }
        
        String nombre = archivo.getName().toLowerCase();
        if (!nombre.endsWith(".wav") && !nombre.endsWith(".mp3") && 
            !nombre.endsWith(".aiff") && !nombre.endsWith(".au")) {
            System.err.println("Extension de archivo no reconocida: " + archivo.getName());
            System.err.println("Formatos soportados: WAV, MP3, AIFF, AU");
        }
        
        return true;
    }

    private static AudioFormat decodificarFormatoAudio(AudioFormat formatoOriginal) {
        if (formatoOriginal.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
            return formatoOriginal;
        }
        
        return new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            formatoOriginal.getSampleRate(),
            16,
            formatoOriginal.getChannels(),
            formatoOriginal.getChannels() * 2,
            formatoOriginal.getSampleRate(),
            false
        );
    }

    private static void configurarControlesVolumen() {
        if (clipActual != null && clipActual.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumenControl = (FloatControl) clipActual.getControl(FloatControl.Type.MASTER_GAIN);
            aplicarVolumenActual();
            System.out.println("Control de volumen disponible");
        } else {
            volumenControl = null;
            System.out.println("Control de volumen no disponible");
        }
    }

    private static void aplicarVolumenActual() {
        if (volumenControl != null) {
            float min = volumenControl.getMinimum();
            float max = volumenControl.getMaximum();
            float rango = max - min;
            float gain = min + (rango * volumen);
            volumenControl.setValue(gain);
        }
    }

    private static void configurarListenersReproduccion(File archivo) {
        if (clipActual == null) return;
        
        clipActual.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                if (estadoActual == Estado.REPRODUCIENDO) {
                    reproduciendo = false;
                    estadoActual = Estado.DETENIDO;
                }
            }
        });
    }

    private static void mostrarInformacionAudio(File archivo, AudioFormat formato) {
        System.out.println("\nINFORMACION DEL AUDIO:");
        System.out.println("Archivo: " + archivo.getName());
        System.out.println("Tamano: " + formatTamanoArchivo(archivo.length()));
        System.out.println("Formato: " + formato.getEncoding());
        System.out.println("Sample Rate: " + formato.getSampleRate() + " Hz");
        System.out.println("Canales: " + formato.getChannels());
        System.out.println("Sample Size: " + formato.getSampleSizeInBits() + " bits");
        
        if (clipActual != null) {
            long duracionMicros = clipActual.getMicrosecondLength();
            double duracionSegundos = duracionMicros / 1_000_000.0;
            System.out.println("Duracion: " + String.format("%.2f", duracionSegundos) + " segundos");
        }
        
        System.out.println("Volumen: " + (int)(volumen * 100) + "%");
    }

    private static void mostrarProgreso(long tiempoActual, long duracionTotal) {
        if (duracionTotal <= 0) return;
        
        int porcentaje = (int) ((tiempoActual * 100) / duracionTotal);
        String barra = generarBarraProgreso(porcentaje, 20);
        
        System.out.printf("Progreso: [%s] %d%% %s/%s\r", 
            barra, porcentaje, 
            formatDuracion(tiempoActual), 
            formatDuracion(duracionTotal));
    }

    private static String generarBarraProgreso(int porcentaje, int longitud) {
        int barrasCompletas = (porcentaje * longitud) / 100;
        StringBuilder barra = new StringBuilder();
        
        for (int i = 0; i < longitud; i++) {
            if (i < barrasCompletas) {
                barra.append("#");
            } else {
                barra.append("-");
            }
        }
        
        return barra.toString();
    }

    public static boolean pausarReproduccion() {
        if (clipActual != null && estadoActual == Estado.REPRODUCIENDO) {
            clipActual.stop();
            estadoActual = Estado.PAUSADO;
            System.out.println("Reproduccion pausada");
            return true;
        }
        return false;
    }

    public static boolean reanudarReproduccion() {
        if (clipActual != null && estadoActual == Estado.PAUSADO) {
            clipActual.start();
            estadoActual = Estado.REPRODUCIENDO;
            System.out.println("Reproduccion reanudada");
            return true;
        }
        return false;
    }

    public static boolean detenerReproduccion() {
        if (clipActual != null) {
            clipActual.stop();
            clipActual.close();
            reproduciendo = false;
            estadoActual = Estado.DETENIDO;
            System.out.println("Reproduccion detenida");
            return true;
        }
        return false;
    }

    public static boolean saltarA(long milisegundos) {
        if (clipActual != null && clipActual.isOpen()) {
            long microsegundos = milisegundos * 1000;
            if (microsegundos <= clipActual.getMicrosecondLength()) {
                clipActual.setMicrosecondPosition(microsegundos);
                System.out.println("Saltando a: " + formatDuracion(milisegundos));
                return true;
            }
        }
        return false;
    }

    public static boolean ajustarVolumen(float nuevoVolumen) {
        if (nuevoVolumen < 0.0f) nuevoVolumen = 0.0f;
        if (nuevoVolumen > 1.0f) nuevoVolumen = 1.0f;
        
        volumen = nuevoVolumen;
        aplicarVolumenActual();
        
        System.out.println("Volumen ajustado a: " + (int)(volumen * 100) + "%");
        return true;
    }

    public static float getVolumen() {
        return volumen;
    }

    public static Estado getEstado() {
        return estadoActual;
    }

    public static long getPosicionActual() {
        if (clipActual != null && clipActual.isOpen()) {
            return clipActual.getMicrosecondPosition() / 1000;
        }
        return 0;
    }

    public static long getDuracionTotal() {
        if (clipActual != null && clipActual.isOpen()) {
            return clipActual.getMicrosecondLength() / 1000;
        }
        return 0;
    }

    private static void limpiarRecursos() {
        if (clipActual != null) {
            clipActual.close();
            clipActual = null;
        }
        reproduciendo = false;
        estadoActual = Estado.DETENIDO;
    }

    private static String formatDuracion(long milisegundos) {
        long segundos = milisegundos / 1000;
        long minutos = segundos / 60;
        segundos = segundos % 60;
        return String.format("%02d:%02d", minutos, segundos);
    }

    private static String formatTamanoArchivo(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    public static String getEstadisticas() {
        return String.format(
            "Reproducciones: %d | Tiempo total: %s",
            totalReproducciones,
            formatDuracion(totalTiempoReproducido)
        );
    }

    public static Thread reproducirEnSegundoPlano(File archivo) {
        Thread hiloReproduccion = new Thread(() -> {
            reproducirAudio(archivo);
        });
        hiloReproduccion.setDaemon(true);
        hiloReproduccion.start();
        return hiloReproduccion;
    }

    public static void mostrarFormatosSoportados() {
        System.out.println("\nFORMATOS DE AUDIO SOPORTADOS:");
        
        AudioFileFormat.Type[] tipos = AudioSystem.getAudioFileTypes();
        for (AudioFileFormat.Type tipo : tipos) {
            System.out.println("   - " + tipo.getExtension() + " (" + tipo + ")");
        }
        
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        System.out.println("\nMEZCLADORES DE AUDIO DISPONIBLES:");
        for (Mixer.Info mixer : mixers) {
            System.out.println("   - " + mixer.getName());
        }
    }

    public static void diagnostico() {
        System.out.println("\nDIAGNOSTICO DE AUDIO PLAYER:");
        System.out.println("   Estado: " + estadoActual);
        System.out.println("   Reproduciendo: " + reproduciendo);
        System.out.println("   Clip activo: " + (clipActual != null));
        System.out.println("   Volumen: " + (int)(volumen * 100) + "%");
        System.out.println("   Control volumen: " + (volumenControl != null ? "SI" : "NO"));
        System.out.println("   Total reproducciones: " + totalReproducciones);
        System.out.println("   Tiempo total reproducido: " + formatDuracion(totalTiempoReproducido));
        
        if (clipActual != null && clipActual.isOpen()) {
            System.out.println("   Posicion actual: " + formatDuracion(getPosicionActual()));
            System.out.println("   Duracion total: " + formatDuracion(getDuracionTotal()));
        }
    }

    public static void reproducirTonoPrueba(double frecuencia, int duracionMs) {
        try {
            AudioFormat formato = new AudioFormat(44100, 16, 1, true, false);
            byte[] tonoBuffer = generarTono(formato, frecuencia, duracionMs);
            
            Clip clipTono = AudioSystem.getClip();
            clipTono.open(formato, tonoBuffer, 0, tonoBuffer.length);
            
            System.out.println("Reproduciendo tono de prueba (" + frecuencia + " Hz)");
            clipTono.start();
            
            while (clipTono.isRunning()) {
                Thread.sleep(100);
            }
            
            clipTono.close();
            System.out.println("Tono de prueba completado");
            
        } catch (Exception e) {
            System.err.println("Error reproduciendo tono: " + e.getMessage());
        }
    }

    private static byte[] generarTono(AudioFormat formato, double frecuencia, int duracionMs) {
        int sampleRate = (int) formato.getSampleRate();
        int sampleSize = formato.getSampleSizeInBits() / 8;
        int canales = formato.getChannels();
        int framesPorBuffer = sampleRate * duracionMs / 1000;
        int bufferSize = framesPorBuffer * sampleSize * canales;
        
        byte[] buffer = new byte[bufferSize];
        double periodo = sampleRate / frecuencia;
        
        for (int i = 0; i < framesPorBuffer; i++) {
            double angulo = 2.0 * Math.PI * i / periodo;
            short muestra = (short) (Math.sin(angulo) * Short.MAX_VALUE * 0.5);
            
            for (int canal = 0; canal < canales; canal++) {
                int posicion = (i * canales + canal) * sampleSize;
                for (int byteIndex = 0; byteIndex < sampleSize; byteIndex++) {
                    buffer[posicion + byteIndex] = (byte) (muestra >> (byteIndex * 8));
                }
            }
        }
        
        return buffer;
    }
}