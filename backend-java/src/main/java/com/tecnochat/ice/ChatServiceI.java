package com.tecnochat.ice;

import TecnoChat.ChatService;
import com.tecnochat.server.ClientHandler;
import com.tecnochat.server.MessageHistory;
import com.zeroc.Ice.Current;

public class ChatServiceI implements ChatService {
    @Override
    public void sendMessage(String from, String to, String message, Current current) {
        boolean delivered = ClientHandler.sendPrivateMessageFrom(from, to, message);

        if (!delivered) {
            System.out.println("[ICE] Destinatario no disponible para mensaje privado: " + to);
        }
    }

    @Override
    public String[] getOnlineUsers(Current current) {
        return ClientHandler.getOnlineUsernames().toArray(new String[0]);
    }

    @Override
    public String[] getGroupMembers(String groupName, Current current) {
        return ClientHandler.getGroupMemberNames(groupName).toArray(new String[0]);
    }

    @Override
    public void sendGroupMessage(String from, String group, String message, Current current) {
        boolean delivered = ClientHandler.sendGroupMessageFrom(from, group, message);

        if (!delivered) {
            System.out.println("[ICE] No se envio mensaje grupal. Grupo no encontrado o sin miembros: " + group);
        }
    }

    @Override
    public boolean createGroup(String name, String[] members, Current current) {
        return ClientHandler.createGroupFromRpc(name, members, null);
    }

    @Override
    public String[] getPrivateHistory(String requester, String other, Current current) {
        return ClientHandler.getPrivateHistoryLines(requester, other).toArray(new String[0]);
    }

    @Override
    public String[] getGroupHistory(String groupName, Current current) {
        return MessageHistory.getGroupHistory(groupName).toArray(new String[0]);
    }

    @Override
    public String[] getGroups(Current current) {
        return ClientHandler.getGroupNames().toArray(new String[0]);
    }

    @Override
    public boolean login(String username, Current current) {
        boolean added = ClientHandler.loginRpcUser(username);
        if (added) {
            System.out.println("[ICE] Usuario RPC conectado: " + username);
        }
        return added;
    }

    @Override
    public void logout(String username, Current current) {
        ClientHandler.logoutRpcUser(username);
        System.out.println("[ICE] Usuario RPC desconectado: " + username);
    }

    @Override
    public boolean sendAudio(String from, String to, boolean isGroup, String filename, byte[] data, Current current) {
        return ClientHandler.handleRpcAudio(from, to, isGroup, filename, data);
    }
}
