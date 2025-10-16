package Server;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MessageHistory {
    private static final String HISTORY_DIR = "chat_history";
    private static final String AUDIO_HISTORY_DIR = "audio_history";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Inicializar directorios
    static {
        new File(HISTORY_DIR).mkdirs();
        new File(AUDIO_HISTORY_DIR).mkdirs();
    }
    
    // Guardar mensaje de texto privado
    public static void savePrivateMessage(String sender, String receiver, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] %s -> %s: %s%n", timestamp, sender, receiver, message);
        
        // Guardar en archivo del emisor
        appendToFile(getPrivateHistoryFile(sender, receiver), logEntry);
        // Guardar en archivo del receptor 
        appendToFile(getPrivateHistoryFile(receiver, sender), logEntry);
    }
    
    // Guardar mensaje de texto grupal
    public static void saveGroupMessage(String sender, String groupName, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] %s en %s: %s%n", timestamp, sender, groupName, message);
        
        appendToFile(getGroupHistoryFile(groupName), logEntry);
    }
    
    // Guardar nota de voz privada
    public static void savePrivateAudio(String sender, String receiver, File audioFile) {
        String timestamp = LocalDateTime.now().format(formatter);
        
        // Copiar archivo de audio al historial
        File audioHistoryFile = copyAudioToHistory(audioFile, sender, receiver, timestamp, false);
        
        // Registrar en historial de texto
        String logEntry = String.format("[%s] %s -> %s: [AUDIO: %s]%n", 
            timestamp, sender, receiver, audioHistoryFile.getName());
        
        appendToFile(getPrivateHistoryFile(sender, receiver), logEntry);
        appendToFile(getPrivateHistoryFile(receiver, sender), logEntry);
    }
    
    // Guardar nota de voz grupal
    public static void saveGroupAudio(String sender, String groupName, File audioFile) {
        String timestamp = LocalDateTime.now().format(formatter);
        
        // Copiar archivo de audio al historial
        File audioHistoryFile = copyAudioToHistory(audioFile, sender, groupName, timestamp, true);
        
        // Registrar en historial de texto
        String logEntry = String.format("[%s] %s en %s: [AUDIO: %s]%n", 
            timestamp, sender, groupName, audioHistoryFile.getName());
        
        appendToFile(getGroupHistoryFile(groupName), logEntry);
    }
    
    // Obtener historial de conversación privada
    public static List<String> getPrivateHistory(String user1, String user2) {
        File historyFile = getPrivateHistoryFile(user1, user2);
        return readHistoryFromFile(historyFile);
    }
    
    // Obtener historial de grupo
    public static List<String> getGroupHistory(String groupName) {
        File historyFile = getGroupHistoryFile(groupName);
        return readHistoryFromFile(historyFile);
    }
    
    // Métodos auxiliares
    private static File getPrivateHistoryFile(String user1, String user2) {
        // Crear nombre consistente para la conversación (orden alfabético)
        List<String> users = Arrays.asList(user1, user2);
        Collections.sort(users);
        String fileName = "private_" + users.get(0) + "_" + users.get(1) + ".txt";
        return new File(HISTORY_DIR, fileName);
    }
    
    private static File getGroupHistoryFile(String groupName) {
        String fileName = "group_" + groupName + ".txt";
        return new File(HISTORY_DIR, fileName);
    }
    
    private static void appendToFile(File file, String content) {
        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(content);
            bw.flush();
        } catch (IOException e) {
            System.err.println("Error al guardar en historial: " + e.getMessage());
        }
    }
    
    private static List<String> readHistoryFromFile(File file) {
        List<String> history = new ArrayList<>();
        if (!file.exists()) return history;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                history.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error al leer historial: " + e.getMessage());
        }
        return history;
    }
    
    private static File copyAudioToHistory(File sourceAudio, String sender, String destination, 
                                         String timestamp, boolean isGroup) {
        String cleanTimestamp = timestamp.replace(":", "-").replace(" ", "_");
        String prefix = isGroup ? "group_" + destination : "private_" + sender + "_" + destination;
        String fileName = prefix + "_" + cleanTimestamp + "_" + sourceAudio.getName();
        
        File destFile = new File(AUDIO_HISTORY_DIR, fileName);
        
        try (FileInputStream fis = new FileInputStream(sourceAudio);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
        } catch (IOException e) {
            System.err.println("Error al copiar audio al historial: " + e.getMessage());
        }
        
        return destFile;
    }
}