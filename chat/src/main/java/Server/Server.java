package Server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 6789;
    private static final int THREAD_POOL_SIZE = 10;

    public static final Map<String, ClientHandler> clientesConectados = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String, Set<ClientHandler>> grupos = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor TCP iniciado en el puerto " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                threadPool.execute(new ClientHandler(clientSocket));
            }

        } catch (Exception e) {
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
}
