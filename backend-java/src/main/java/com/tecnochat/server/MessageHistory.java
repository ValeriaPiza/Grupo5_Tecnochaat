package com.tecnochat.server;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MessageHistory {
    private static final String HISTORY_DIR = "chat_history";
    private static final String AUDIO_HISTORY_DIR = "audio_history";
    private static final String LLAMADAS_HISTORY_DIR = "llamadas_history";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    static {
        new File(HISTORY_DIR).mkdirs();
        new File(AUDIO_HISTORY_DIR).mkdirs();
        new File(LLAMADAS_HISTORY_DIR).mkdirs();
    }
    
    public static void savePrivateMessage(String sender, String receiver, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] %s -> %s: %s", timestamp, sender, receiver, message);
        
        File historyFile = getPrivateHistoryFile(sender, receiver);
        appendToFile(historyFile, logEntry);
        
        System.out.println("Historial guardado: " + sender + " -> " + receiver);
    }
    
    public static void saveGroupMessage(String sender, String groupName, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] %s en %s: %s", timestamp, sender, groupName, message);
        
        appendToFile(getGroupHistoryFile(groupName), logEntry);
        System.out.println("Historial grupal guardado: " + sender + " en " + groupName);
    }
    
    public static void savePrivateAudio(String sender, String receiver, File audioFile) {
        String timestamp = LocalDateTime.now().format(formatter);
        
        File audioHistoryFile = copyAudioToHistory(audioFile, sender, receiver, timestamp, false);
        
        String logEntry = String.format("[%s] %s -> %s: [AUDIO: %s]", 
            timestamp, sender, receiver, audioHistoryFile.getName());
        
        File historyFile = getPrivateHistoryFile(sender, receiver);
        appendToFile(historyFile, logEntry);
        
        System.out.println("Audio privado guardado en historial: " + sender + " -> " + receiver);
    }
    
    public static void saveGroupAudio(String sender, String groupName, File audioFile) {
        String timestamp = LocalDateTime.now().format(formatter);
        
        File audioHistoryFile = copyAudioToHistory(audioFile, sender, groupName, timestamp, true);
        
        String logEntry = String.format("[%s] %s en %s: [AUDIO: %s]", 
            timestamp, sender, groupName, audioHistoryFile.getName());
        
        appendToFile(getGroupHistoryFile(groupName), logEntry);
        System.out.println("Audio grupal guardado en historial: " + sender + " en " + groupName);
    }
    
    public static void saveLlamadaIndividual(String caller, String receiver, String estado) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] LLAMADA_INDIVIDUAL: %s -> %s [%s]", 
            timestamp, caller, receiver, estado);
        
        File historyFile = getLlamadasHistoryFile();
        appendToFile(historyFile, logEntry);
        
        File privateHistory = getPrivateHistoryFile(caller, receiver);
        String privateLogEntry = String.format("[%s] %s -> %s: [LLAMADA: %s]", 
            timestamp, caller, receiver, estado.toLowerCase());
        appendToFile(privateHistory, privateLogEntry);
        
        System.out.println("Historial llamada individual: " + caller + " -> " + receiver + " [" + estado + "]");
    }
    
    public static void saveLlamadaGrupal(String caller, String groupName, int miembrosInvitados, String estado) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] LLAMADA_GRUPAL: %s -> %s [%s - %d miembros]", 
            timestamp, caller, groupName, estado, miembrosInvitados);
        
        File historyFile = getLlamadasHistoryFile();
        appendToFile(historyFile, logEntry);
        
        File groupHistory = getGroupHistoryFile(groupName);
        String groupLogEntry = String.format("[%s] %s: [LLAMADA_GRUPAL: %s - %d miembros]", 
            timestamp, caller, estado.toLowerCase(), miembrosInvitados);
        appendToFile(groupHistory, groupLogEntry);
        
        System.out.println("Historial llamada grupal: " + caller + " -> " + groupName + 
                          " [" + estado + "] - " + miembrosInvitados + " miembros");
    }
    
    public static List<String> getPrivateHistory(String user1, String user2) {
        File historyFile = getPrivateHistoryFile(user1, user2);
        return readHistoryFromFile(historyFile);
    }
    
    public static List<String> getGroupHistory(String groupName) {
        File historyFile = getGroupHistoryFile(groupName);
        return readHistoryFromFile(historyFile);
    }
    
    public static List<String> getLlamadasHistory() {
        File historyFile = getLlamadasHistoryFile();
        return readHistoryFromFile(historyFile);
    }
    
    private static File getPrivateHistoryFile(String user1, String user2) {
        List<String> users = Arrays.asList(user1, user2);
        Collections.sort(users);
        String fileName = "private_" + users.get(0) + "_" + users.get(1) + ".txt";
        return new File(HISTORY_DIR, fileName);
    }
    
    private static File getGroupHistoryFile(String groupName) {
        String fileName = "group_" + groupName + ".txt";
        return new File(HISTORY_DIR, fileName);
    }
    
    private static File getLlamadasHistoryFile() {
        String fileName = "llamadas_history.txt";
        return new File(LLAMADAS_HISTORY_DIR, fileName);
    }
    
    private static void appendToFile(File file, String content) {
        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(content);
            bw.newLine();
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