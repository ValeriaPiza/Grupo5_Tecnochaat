package com.tecnochat.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.tecnochat.ice.CallServiceI;
import com.tecnochat.ice.ChatServiceI;

public class Server {
    private static final int PORT = 6789;
    private static final int ICE_PORT = 10000;
    private static final int THREAD_POOL_SIZE = 10;

    public static final Map<String, ClientHandler> clientesConectados = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String, Set<ClientHandler>> grupos = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        iniciarServidorIce();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor TCP iniciado en el puerto " + PORT + "...");
            System.out.println("Esperando conexiones de clientes...");
            System.out.println("NUEVO: Llamadas grupales disponibles");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIP = clientSocket.getInetAddress().getHostAddress();
                System.out.println("Cliente conectado desde: " + clientIP);

                threadPool.execute(new ClientHandler(clientSocket));
            }

        } catch (Exception e) {
            System.err.println("Error en servidor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    public static ClientHandler getClientHandler(String nombreUsuario) {
        return clientesConectados.get(nombreUsuario);
    }

    public static Set<ClientHandler> getMiembrosGrupo(String nombreGrupo) {
        return grupos.get(nombreGrupo);
    }

    private static void iniciarServidorIce() {
        Thread iceThread = new Thread(() -> {
            com.zeroc.Ice.Communicator communicator = null;
            try {
                communicator = com.zeroc.Ice.Util.initialize();
                com.zeroc.Ice.ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                        "TecnoChatAdapter", "default -p " + ICE_PORT);

                adapter.add(new ChatServiceI(), com.zeroc.Ice.Util.stringToIdentity("ChatService"));
                adapter.add(new CallServiceI(), com.zeroc.Ice.Util.stringToIdentity("CallService"));
                adapter.activate();

                System.out.println("Servidor Ice iniciado en puerto " + ICE_PORT + " (ChatService, CallService)");
                communicator.waitForShutdown();
            } catch (Exception e) {
                System.err.println("Error iniciando servidor Ice: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (communicator != null) {
                    try {
                        communicator.destroy();
                    } catch (Exception destroyError) {
                        System.err.println("Error cerrando comunicador Ice: " + destroyError.getMessage());
                    }
                }
            }
        });

        iceThread.setName("IceServerThread");
        iceThread.setDaemon(true);
        iceThread.start();
    }
}
