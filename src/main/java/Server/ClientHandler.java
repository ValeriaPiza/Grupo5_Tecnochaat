package Server;

import java.io.*;
import java.net.Socket;
import java.util.*;

import Client.Client;
import Server.MessageHistory;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private String clientName;
    private BufferedReader in;
    private PrintWriter out;

    private static final Map<String, ClientHandler> users = Collections.synchronizedMap(new HashMap<>());

    private static final Map<String, Set<ClientHandler>> groups = Collections.synchronizedMap(new HashMap<>());

    private DataInputStream dataIn;
    private DataOutputStream dataOut;

    private static final Map<String, Set<ClientHandler>> llamadasGrupalesActivas = Collections
            .synchronizedMap(new HashMap<>());

    public ClientHandler(Socket socket) throws IOException {
        this.clientSocket = socket;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.dataIn = new DataInputStream(clientSocket.getInputStream());
        this.dataOut = new DataOutputStream(clientSocket.getOutputStream());

        new File("server_audios").mkdirs();
    }

    @Override
    public void run() {
        try {
            out.println("Ingresa tu nombre:");
            clientName = in.readLine();

            synchronized (users) {
                if (users.containsKey(clientName)) {
                    out.println("Nombre ya en uso. Conexion terminada.");
                    clientSocket.close();
                    return;
                }
                users.put(clientName, this);
            }

            out.println("Hola " + clientName + "! Bienvenido a TecnoChat.");

            System.out.println("Cliente '" + clientName + "' conectado desde " + clientSocket.getInetAddress());

            // Menú principal
            String opcion;
            while (true) {
                out.println("\n=== MENU TECNOCHAT ===");
                out.println("1. Enviar mensaje a usuario");
                out.println("2. Crear grupo");
                out.println("3. Enviar mensaje a grupo");
                out.println("4. Salir");
                out.println("5. Nota de voz privada");
                out.println("6. Nota de voz a grupo");
                out.println("7. Ver historial privado");
                out.println("8. Ver historial de grupo");
                out.println("9. Llamar a un usuario/grupo");
                out.println("10. Terminar llamada");
                out.println("11. Ver clientes en linea");
                out.println("======================");
                out.println("Elige opcion:");

                opcion = in.readLine();

                if (opcion == null || opcion.equals("4"))
                    break;

                switch (opcion) {
                    case "1":
                        enviarPrivado();
                        break;
                    case "2":
                        crearGrupo();
                        break;
                    case "3":
                        enviarAGrupo();
                        break;
                    case "5":
                        manejarNotaVozPrivada();
                        break;
                    case "6":
                        manejarNotaVozGrupo();
                        break;
                    case "7":
                        verHistorialPrivado();
                        break;
                    case "8":
                        verHistorialGrupo();
                        break;
                    case "9":
                        manejarLlamada();
                        break;
                    case "10":
                        terminarLlamada();
                        break;
                    case "11":
                        listarClientesConectados();
                        break;
                    case "CALL_ACCEPTED":
                        System.out.println("Llamada individual aceptada por: " + clientName);
                        break;
                    case "CALL_REJECTED":
                        System.out.println("Llamada individual rechazada por: " + clientName);
                        break;
                    case "CALL_GRUPAL_ACCEPTED":
                        System.out.println("Llamada grupal aceptada por: " + clientName);
                        manejarAceptacionLlamadaGrupal();
                        break;
                    case "CALL_GRUPAL_REJECTED":
                        System.out.println("Llamada grupal rechazada por: " + clientName);
                        break;
                    case "CALL_ENDED":
                        System.out.println("Llamada terminada por: " + clientName);
                        break;
                    case "CALL_GRUPAL_ENDED":
                        System.out.println("Cliente " + clientName + " salio de llamada grupal");
                        break;
                    default:
                        out.println("Opcion no valida.");
                        break;
                }
            }

        } catch (IOException e) {
            System.out.println("Error con el cliente " + clientName + ": " + e.getMessage());
        } finally {
            try {
                synchronized (users) {
                    users.remove(clientName);
                }
                synchronized (groups) {
                    for (Set<ClientHandler> grupo : groups.values()) {
                        grupo.remove(this);
                    }
                }
                
                synchronized (llamadasGrupalesActivas) {
                    for (Set<ClientHandler> llamada : llamadasGrupalesActivas.values()) {
                        llamada.remove(this);
                    }
                }
                clientSocket.close();
                System.out.println("Cliente " + clientName + " desconectado.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    
    private void terminarLlamada() throws IOException {
        out.println("Que llamada deseas terminar?");
        out.println("1. Llamada individual");
        out.println("2. Llamada grupal");
        String tipo = in.readLine();

        if ("2".equals(tipo)) {
            out.println("CALL_GRUPAL_ENDED");
            System.out.println("Cliente " + clientName + " salio de llamada grupal");
        } else {
            out.println("CALL_ENDED");
            System.out.println("Cliente " + clientName + " termino llamada individual");
        }
    }

    
    private void manejarLlamada() throws IOException {
        out.println("Que tipo de llamada deseas realizar?");
        out.println("1. Llamada individual");
        out.println("2. Llamada grupal (con un grupo)");
        out.print("Elige opcion: ");

        String tipoLlamada = in.readLine();

        if ("2".equals(tipoLlamada)) {
            manejarLlamadaGrupal();
        } else {
            manejarLlamadaIndividual();
        }
    }

    
    private void manejarLlamadaGrupal() throws IOException {
        if (groups.isEmpty()) {
            out.println("No hay grupos disponibles para llamar.");
            return;
        }

        out.println("Grupos disponibles para llamada grupal:");
        synchronized (groups) {
            for (String nombreGrupo : groups.keySet()) {
                Set<ClientHandler> miembros = groups.get(nombreGrupo);
                out.println(" - " + nombreGrupo + " (" + miembros.size() + " miembros)");
            }
        }

        out.println("A que grupo deseas llamar?");
        String grupoDestino = in.readLine();

        Set<ClientHandler> miembros;
        synchronized (groups) {
            miembros = groups.get(grupoDestino);
        }

        if (miembros == null || miembros.size() < 2) {
            out.println("Grupo no encontrado o sin suficientes miembros (minimo 2).");
            return;
        }

        try {
            int puertoBase = 60000 + new Random().nextInt(5000);
            String idLlamadaGrupal = grupoDestino + "_" + System.currentTimeMillis();

            System.out.println(" INICIANDO LLAMADA GRUPAL CORREGIDA:");
            System.out.println("   Creador: " + clientName);
            System.out.println("   Grupo: " + grupoDestino);
            System.out.println("   Miembros totales: " + miembros.size());

            int puertoRecepcionCreador = puertoBase;           
            int puertoRecepcionMiembros = puertoBase + 1000;   

            System.out.println("CONFIGURACIÓN SIMÉTRICA:");
            System.out.println("   Creador:");
            System.out.println("     - Escucha en: " + puertoRecepcionCreador + " (miembros ENVÍAN aquí)");
            System.out.println("     - Envía a: " + puertoRecepcionMiembros + " (miembros ESCUCHAN aquí)");
            System.out.println("   Miembros:");
            System.out.println("     - Escuchan en: " + puertoRecepcionMiembros + " (creador ENVÍA aquí)");
            System.out.println("     - Envían a: " + puertoRecepcionCreador + " (creador ESCUCHA aquí)");

            String ipCreador = this.clientSocket.getInetAddress().getHostAddress();
            
            if (ipCreador.equals("127.0.0.1") || ipCreador.equals("localhost")) {
                try {
                    Socket testSocket = new Socket("8.8.8.8", 53);
                    ipCreador = testSocket.getLocalAddress().getHostAddress();
                    testSocket.close();
                    System.out.println("   IP real del creador detectada: " + ipCreador);
                } catch (Exception e) {
                    System.out.println("   No se pudo detectar IP real del creador, usando: " + ipCreador);
                }
            }

            this.out.println("CONFIG_LLAMADA_GRUPAL");
            this.out.println("IP_CREADOR:" + ipCreador);  
            this.out.println("PUERTO_RECEPCION:" + puertoRecepcionCreador);   
            this.out.println("PUERTO_ENVIO:" + puertoRecepcionMiembros);      
            this.out.println("MIEMBROS_INVITADOS:" + (miembros.size() - 1));
            this.out.println("ID_LLAMADA:" + idLlamadaGrupal);

            for (ClientHandler miembro : miembros) {
                if (!miembro.clientName.equals(this.clientName)) {
                    String ipMiembro = miembro.clientSocket.getInetAddress().getHostAddress();
                    
                    if (ipMiembro.equals("127.0.0.1") || ipMiembro.equals("localhost")) {
                        try {
                            Socket testSocket = new Socket("8.8.8.8", 53);
                            ipMiembro = testSocket.getLocalAddress().getHostAddress();
                            testSocket.close();
                        } catch (Exception e) {
                        }
                    }
                    
                    this.out.println("IP_MIEMBRO:" + ipMiembro);
                    this.out.println("PUERTO_ENVIO_MIEMBRO:" + puertoRecepcionMiembros);
                }
            }
            this.out.println("END_IP_LIST");

            int miembrosNotificados = 0;
            
            for (ClientHandler miembro : miembros) {
                if (!miembro.clientName.equals(this.clientName)) {
                    try {
                        String ipMiembro = miembro.clientSocket.getInetAddress().getHostAddress();
                        
                        System.out.println("Configurando miembro " + miembro.clientName + ":");
                        System.out.println("   IP: " + ipMiembro);
                        System.out.println("   Escucha en: " + puertoRecepcionMiembros + " (recibe del creador)");
                        System.out.println("   Envía a: " + ipCreador + ":" + puertoRecepcionCreador + " (envía al creador)");

                        miembro.out.println("LLAMADA_GRUPAL_INCOMING");
                        miembro.dataOut.writeUTF(this.clientName);
                        miembro.dataOut.writeUTF(grupoDestino);
                        miembro.dataOut.writeUTF(ipCreador);  
                        miembro.dataOut.writeInt(puertoRecepcionMiembros);  
                        miembro.dataOut.writeInt(puertoRecepcionCreador);   
                        miembro.dataOut.writeUTF(idLlamadaGrupal);
                        miembro.dataOut.flush();
                        
                        miembrosNotificados++;
                        System.out.println("  Miembro configurado: " + miembro.clientName);

                    } catch (Exception e) {
                        System.err.println("   Error configurando " + miembro.clientName + ": " + e.getMessage());
                    }
                }
            }

            System.out.println("Llamada grupal SIMÉTRICA configurada - " + miembrosNotificados + " miembros notificados");
            out.println("Llamada grupal iniciada al grupo '" + grupoDestino + "'. Esperando respuestas...");

        } catch (Exception e) {
            out.println("Error al iniciar llamada grupal: " + e.getMessage());
            System.err.println("Error en llamada grupal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void manejarAceptacionLlamadaGrupal() {
        System.out.println("Usuario " + clientName + " se unio a llamada grupal activa");
    }

    
    private void manejarLlamadaIndividual() throws IOException {
        List<String> disponibles = new ArrayList<>();
        synchronized (users) {
            for (String nombre : users.keySet()) {
                if (!nombre.equals(this.clientName)) {
                    disponibles.add(nombre);
                }
            }
        }

        if (disponibles.isEmpty()) {
            out.println("No hay otros usuarios conectados.");
            return;
        }

        out.println("Usuarios disponibles para llamada individual:");
        for (String nombre : disponibles) {
            out.println(" - " + nombre);
        }

        out.println("A que usuario deseas llamar?");
        String destinatario = in.readLine();

        if (destinatario == null || !users.containsKey(destinatario)) {
            out.println("El usuario no esta conectado.");
            return;
        }

        ClientHandler receptor = users.get(destinatario);

        try {
            String ipReceptor = receptor.clientSocket.getInetAddress().getHostAddress();
            String ipLlamante = this.clientSocket.getInetAddress().getHostAddress();

            System.out.println("CONFIGURANDO LLAMADA:");
            System.out.println("   Llamante: " + clientName + " (" + ipLlamante + ")");
            System.out.println("   Receptor: " + destinatario + " (" + ipReceptor + ")");

            if (ipLlamante.equals("127.0.0.1") || ipLlamante.equals("localhost")) {
                System.out.println("ADVERTENCIA: Llamante está en localhost");
                // Intentar obtener IP real de la red
                try {
                    Socket testSocket = new Socket("8.8.8.8", 53);
                    ipLlamante = testSocket.getLocalAddress().getHostAddress();
                    testSocket.close();
                    System.out.println("   IP real detectada: " + ipLlamante);
                } catch (Exception e) {
                    System.out.println("   No se pudo detectar IP real");
                }
            }

            if (ipReceptor.equals("127.0.0.1") || ipReceptor.equals("localhost")) {
                System.out.println("ADVERTENCIA: Receptor está en localhost");
                if (ipLlamante.equals("127.0.0.1") || ipLlamante.equals("localhost")) {
                    ipReceptor = "127.0.0.1";
                    ipLlamante = "127.0.0.1";
                    System.out.println("   Ambos en localhost - Usando 127.0.0.1");
                }
            }

            int puertoBase = 50000 + new Random().nextInt(10000);
            int puertoEnvioLlamante = puertoBase;
            int puertoRecepcionLlamante = puertoBase + 1000;

            System.out.println(" CONFIGURACIÓN DE PUERTOS:");
            System.out.println("   " + clientName + " ENVÍA → " + ipReceptor + ":" + puertoEnvioLlamante);
            System.out.println("   " + clientName + " RECIBE ← " + puertoRecepcionLlamante);
            System.out.println("   " + destinatario + " ENVÍA → " + ipLlamante + ":" + puertoRecepcionLlamante);
            System.out.println("   " + destinatario + " RECIBE ← " + puertoEnvioLlamante);

            out.println("IP_DESTINO:" + ipReceptor);
            out.println("PUERTO_ENVIO:" + puertoEnvioLlamante);
            out.println("PUERTO_RECEPCION:" + puertoRecepcionLlamante);

            Thread.sleep(200);

            receptor.out.println("LLAMADA_INCOMING");
            receptor.dataOut.writeUTF(this.clientName);
            receptor.dataOut.writeUTF(ipLlamante);
            receptor.dataOut.writeInt(puertoEnvioLlamante);    
            receptor.dataOut.writeInt(puertoRecepcionLlamante); 
            receptor.dataOut.flush();

            System.out.println("Configuración de llamada completada");

        } catch (Exception e) {
            out.println("Error al iniciar la llamada.");
            System.err.println("Error en llamada: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void listarClientesConectados() {
        StringBuilder listaClientes = new StringBuilder();

        synchronized (users) {
            for (String nombre : users.keySet()) {
                if (!nombre.equals(this.clientName)) {
                    if (listaClientes.length() > 0) {
                        listaClientes.append(",");
                    }
                    listaClientes.append(nombre);
                }
            }
        }

        if (listaClientes.length() == 0) {
            out.println("CLIENTES_CONECTADOS:No hay otros clientes conectados.");
        } else {
            out.println("CLIENTES_CONECTADOS:" + listaClientes.toString());
        }
    }

    private void enviarPrivado() throws IOException {
        List<String> disponibles = new ArrayList<>();
        synchronized (users) {
            for (String nombre : users.keySet()) {
                if (!nombre.equals(this.clientName)) {
                    disponibles.add(nombre);
                }
            }
        }

        if (disponibles.isEmpty()) {
            out.println("No hay otros usuarios conectados en este momento.");
            return;
        }

        out.println("Usuarios disponibles:");
        for (String nombre : disponibles) {
            out.println(" - " + nombre);
        }

        out.println("A que usuario deseas enviar el mensaje?");
        String destino = in.readLine();
        out.println("Escribe tu mensaje:");
        String mensaje = in.readLine();

        ClientHandler receptor;
        synchronized (users) {
            receptor = users.get(destino);
        }
        if (receptor != null && !destino.equals(clientName)) {
            receptor.out.println("Mensaje privado de " + clientName + ": " + mensaje);

            MessageHistory.savePrivateMessage(clientName, destino, mensaje);

            out.println("Mensaje enviado correctamente.");
        } else {
            out.println("Usuario no encontrado o invalido.");
        }
    }

    private void crearGrupo() throws IOException {
        out.println("Nombre del grupo:");
        String nombreGrupo = in.readLine();

        synchronized (groups) {
            groups.putIfAbsent(nombreGrupo, Collections.synchronizedSet(new HashSet<>()));
            groups.get(nombreGrupo).add(this);
        }

        out.println("Grupo '" + nombreGrupo + "' creado.");
        out.println("Usuarios disponibles para agregar:");

        synchronized (users) {
            for (String nombre : users.keySet()) {
                if (!nombre.equals(this.clientName)) {
                    out.println(" - " + nombre);
                }
            }
        }

        out.println("Escribe los nombres de los usuarios a agregar, separados por comas:");
        String linea = in.readLine();
        if (linea == null || linea.trim().isEmpty())
            return;

        String[] nombres = linea.split(",");

        synchronized (groups) {
            for (String nombre : nombres) {
                String limpio = nombre.trim();
                if (!limpio.equals(clientName)) {
                    ClientHandler ch;
                    synchronized (users) {
                        ch = users.get(limpio);
                    }
                    if (ch != null) {
                        groups.get(nombreGrupo).add(ch);
                        ch.out.println("Has sido agregado al grupo '" + nombreGrupo + "' por " + clientName + ".");
                    } else {
                        out.println("No se pudo agregar a '" + limpio + "' (no existe).");
                    }
                } else {
                    out.println("No se pudo agregar a '" + limpio + "' (es tu propio nombre).");
                }
            }
        }

        out.println("Miembros actuales del grupo '" + nombreGrupo + "':");
        synchronized (groups) {
            for (ClientHandler miembro : groups.get(nombreGrupo)) {
                out.println(" - " + miembro.clientName);
            }
        }
    }

    private void enviarAGrupo() throws IOException {
        if (groups.isEmpty()) {
            out.println("No hay grupos creados aun.");
            return;
        }

        out.println("Grupos disponibles:");
        synchronized (groups) {
            for (String nombreGrupo : groups.keySet()) {
                out.println(" - " + nombreGrupo);
            }
        }

        out.println("Nombre del grupo al que deseas enviar mensaje:");
        String grupo = in.readLine();

        synchronized (groups) {
            if (!groups.containsKey(grupo)) {
                out.println("Grupo no encontrado.");
                return;
            }
        }

        out.println("Escribe tu mensaje para el grupo:");
        String mensaje = in.readLine();

        
        int mensajesEnviados = 0;
        synchronized (groups) {
            Set<ClientHandler> miembros = groups.get(grupo);

            if (miembros == null || miembros.isEmpty()) {
                out.println("El grupo no tiene miembros.");
                return;
            }

            for (ClientHandler miembro : miembros) {
                try {
                    miembro.out.println("[" + grupo + "] " + clientName + ": " + mensaje);
                    miembro.out.flush(); 
                    mensajesEnviados++;
                } catch (Exception e) {
                    System.err.println("Error enviando mensaje a " + miembro.clientName + ": " + e.getMessage());
                }
            }
        }

        MessageHistory.saveGroupMessage(clientName, grupo, mensaje);

        out.println("Mensaje enviado al grupo correctamente. (" + mensajesEnviados + " miembros)");
        System.out.println(" Mensaje grupal de " + clientName + " al grupo '" + grupo + "' (" + mensajesEnviados
                + " receptores)");
    }

    private void manejarNotaVozPrivada() throws IOException {
        List<String> disponibles = new ArrayList<>();
        synchronized (users) {
            for (String nombre : users.keySet()) {
                if (!nombre.equals(this.clientName)) {
                    disponibles.add(nombre);
                }
            }
        }

        if (disponibles.isEmpty()) {
            out.println("No hay otros usuarios conectados.");
            return;
        }

        out.println("Usuarios disponibles:");
        for (String nombre : disponibles) {
            out.println(" - " + nombre);
        }
        out.flush();

        String destino = in.readLine();
        if (destino == null || destino.trim().isEmpty()) {
            out.println("Destinatario no valido.");
            return;
        }

        ClientHandler receptor;
        synchronized (users) {
            receptor = users.get(destino.trim());
        }

        if (receptor == null || destino.equals(clientName)) {
            out.println("Usuario no encontrado o invalido.");
            return;
        }

        try {
            out.println("LISTO_PARA_AUDIO");
            File audioRecibido = recibirArchivoAudio();

            if (audioRecibido == null || audioRecibido.length() == 0) {
                out.println("Error: Audio no recibido correctamente.");
                return;
            }

            boolean enviado = enviarAudioACliente(receptor, audioRecibido, this.clientName);

            if (enviado) {
                MessageHistory.savePrivateAudio(this.clientName, destino, audioRecibido);
                out.println("Nota de voz enviada correctamente a " + destino);
                System.out.println("Audio privado enviado de " + clientName + " a " + destino);
            } else {
                out.println("Error al enviar la nota de voz.");
            }

        } catch (IOException e) {
            out.println("Error al procesar la nota de voz: " + e.getMessage());
            System.err.println("Error procesando audio de " + clientName + ": " + e.getMessage());
        }
    }

    private void manejarNotaVozGrupo() throws IOException {
        if (groups.isEmpty()) {
            out.println("No hay grupos disponibles.");
            return;
        }

        out.println("Grupos disponibles:");
        synchronized (groups) {
            for (String nombreGrupo : groups.keySet()) {
                Set<ClientHandler> miembros = groups.get(nombreGrupo);
                out.println(" - " + nombreGrupo + " (" + miembros.size() + " miembros)");
            }
        }
        out.flush();

        String nombreGrupo = in.readLine();
        if (nombreGrupo == null || nombreGrupo.trim().isEmpty()) {
            out.println("Nombre de grupo no valido.");
            return;
        }

        Set<ClientHandler> miembros;
        synchronized (groups) {
            miembros = groups.get(nombreGrupo.trim());
        }

        if (miembros == null || miembros.isEmpty()) {
            out.println("Grupo no encontrado o sin miembros.");
            return;
        }

        try {
            out.println("LISTO_PARA_AUDIO");
            File audioRecibido = recibirArchivoAudio();

            if (audioRecibido == null || audioRecibido.length() == 0) {
                out.println("Error: Audio no recibido correctamente.");
                return;
            }

            System.out.println("Audio grupal recibido de " + clientName + " para grupo " + nombreGrupo);

            File audioGrupal = new File(audioRecibido.getParent(),
                    "group_" + nombreGrupo + "_" + audioRecibido.getName());
            audioRecibido.renameTo(audioGrupal);

            if (!audioGrupal.exists()) {
                try (FileInputStream fis = new FileInputStream(audioRecibido);
                        FileOutputStream fos = new FileOutputStream(audioGrupal)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }

            int exitosos = 0;
            int totalMiembros = 0;

            synchronized (groups) {
                for (ClientHandler miembro : miembros) {
                    totalMiembros++;
                    if (enviarAudioACliente(miembro, audioGrupal, "[GRUPO " + nombreGrupo + "] " + clientName)) {
                        exitosos++;
                        System.out.println("   Enviado a: " + miembro.clientName);
                    } else {
                        System.out.println("   Fallo en: " + miembro.clientName);
                    }
                }
            }

            if (exitosos > 0) {
                MessageHistory.saveGroupAudio(this.clientName, nombreGrupo, audioGrupal);
                out.println("Nota de voz enviada al grupo " + nombreGrupo +
                        " (" + exitosos + "/" + totalMiembros + " miembros)");
                System.out.println("Audio grupal enviado de " + clientName +
                        " al grupo " + nombreGrupo + " (" + exitosos + "/" + totalMiembros + " receptores)");
            } else {
                out.println("No se pudo enviar la nota de voz a ningun miembro del grupo.");
            }

        } catch (IOException e) {
            out.println("Error al procesar la nota de voz: " + e.getMessage());
            System.err.println("Error procesando audio grupal de " + clientName + ": " + e.getMessage());
        }
    }

    private File recibirArchivoAudio() throws IOException {
        try {
            String nombreArchivo = dataIn.readUTF();
            long tamanoArchivo = dataIn.readLong();

            System.out
                    .println("Recibiendo audio: " + nombreArchivo + " (" + tamanoArchivo + " bytes) de " + clientName);

            if (tamanoArchivo <= 0 || tamanoArchivo > 10000000) {
                throw new IOException("Tamano de archivo invalido: " + tamanoArchivo);
            }

            File carpetaAudios = new File("server_audios");
            if (!carpetaAudios.exists()) {
                carpetaAudios.mkdirs();
            }

            String nombreUnico = System.currentTimeMillis() + "_" + clientName + "_" + nombreArchivo;
            File archivoAudio = new File(carpetaAudios, nombreUnico);

            try (FileOutputStream fos = new FileOutputStream(archivoAudio);
                    BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[4096];
                long bytesRecibidos = 0;

                while (bytesRecibidos < tamanoArchivo) {
                    int bytesParaLeer = (int) Math.min(buffer.length, tamanoArchivo - bytesRecibidos);
                    int bytesLeidos = dataIn.read(buffer, 0, bytesParaLeer);

                    if (bytesLeidos == -1) {
                        throw new IOException("Conexion cerrada inesperadamente");
                    }

                    bos.write(buffer, 0, bytesLeidos);
                    bytesRecibidos += bytesLeidos;
                }
                bos.flush();
            }

            System.out.println(
                    "Audio recibido y guardado: " + archivoAudio.getPath() + " (" + archivoAudio.length() + " bytes)");
            return archivoAudio;

        } catch (IOException e) {
            System.err.println("Error recibiendo archivo de audio de " + clientName + ": " + e.getMessage());
            throw e;
        }
    }

    private boolean enviarAudioACliente(ClientHandler cliente, File audioFile, String emisor) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0) {
                System.err.println("Archivo de audio invalido: " + audioFile.getPath());
                return false;
            }

            cliente.out.println("AUDIO_INCOMING");
            cliente.out.flush();
            Thread.sleep(50);

            boolean esGrupo = audioFile.getName().contains("group_") || emisor.contains("[GRUPO");

            cliente.dataOut.writeUTF(emisor);
            cliente.dataOut.writeUTF(audioFile.getName());
            cliente.dataOut.writeLong(audioFile.length());

            try (FileInputStream fis = new FileInputStream(audioFile);
                    BufferedInputStream bis = new BufferedInputStream(fis)) {

                byte[] buffer = new byte[4096];
                int bytesLeidos;
                long totalEnviado = 0;

                while ((bytesLeidos = bis.read(buffer)) > 0) {
                    cliente.dataOut.write(buffer, 0, bytesLeidos);
                    totalEnviado += bytesLeidos;
                }
                cliente.dataOut.flush();

                if (esGrupo) {
                    System.out.println("Audio grupal enviado a " + cliente.clientName + ": " + audioFile.getName()
                            + " (" + totalEnviado + " bytes)");
                } else {
                    System.out.println("Audio privado enviado a " + cliente.clientName + ": " + audioFile.getName()
                            + " (" + totalEnviado + " bytes)");
                }
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error enviando audio a " + cliente.clientName + ": " + e.getMessage());
            return false;
        }
    }

    private void verHistorialPrivado() throws IOException {
        List<String> disponibles = new ArrayList<>();
        synchronized (users) {
            for (String nombre : users.keySet()) {
                if (!nombre.equals(this.clientName)) {
                    disponibles.add(nombre);
                }
            }
        }

        if (disponibles.isEmpty()) {
            out.println("No hay otros usuarios para ver historial.");
            return;
        }

        out.println("Usuarios disponibles para ver historial:");
        for (String nombre : disponibles) {
            out.println(" - " + nombre);
        }

        out.println("De que usuario quieres ver el historial?");
        String usuario = in.readLine();

        List<String> historial = MessageHistory.getPrivateHistory(clientName, usuario);

        if (historial.isEmpty()) {
            out.println("No hay historial con " + usuario);
        } else {
            out.println("=== HISTORIAL CON " + usuario.toUpperCase() + " ===");
            for (String linea : historial) {
                out.println(linea);
            }
            out.println("=== FIN DEL HISTORIAL ===");
        }
    }

    private void verHistorialGrupo() throws IOException {
        if (groups.isEmpty()) {
            out.println("No hay grupos disponibles.");
            return;
        }

        out.println("Grupos disponibles:");
        synchronized (groups) {
            for (String nombreGrupo : groups.keySet()) {
                out.println(" - " + nombreGrupo);
            }
        }

        out.println("De que grupo quieres ver el historial?");
        String grupo = in.readLine();

        List<String> historial = MessageHistory.getGroupHistory(grupo);

        if (historial.isEmpty()) {
            out.println("No hay historial para el grupo " + grupo);
        } else {
            out.println("=== HISTORIAL DEL GRUPO " + grupo.toUpperCase() + " ===");
            for (String linea : historial) {
                out.println(linea);
            }
            out.println("=== FIN DEL HISTORIAL ===");
        }
    }

    public String getClientName() {
        return clientName;
    }

    public void cerrarConexion() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar conexion: " + e.getMessage());
        }
    }
}