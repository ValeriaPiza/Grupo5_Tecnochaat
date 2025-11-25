module TecnoChat {

    // Definimos un tipo secuencia de strings
    sequence<string> StringSeq;
    // Secuencia de bytes para audio
    sequence<byte> ByteSeq;

    interface ChatService {
        void sendMessage(string from, string to, string message);
        StringSeq getOnlineUsers();
        StringSeq getGroupMembers(string groupName);
        void sendGroupMessage(string from, string group, string message);
        bool createGroup(string name, StringSeq members);
        StringSeq getPrivateHistory(string requester, string other);
        StringSeq getGroupHistory(string groupName);
        StringSeq getGroups();
        bool login(string username);
        void logout(string username);
        // Notas de audio via RPC (almacenadas en audio_history)
        bool sendAudio(string from, string to, bool isGroup, string filename, ByteSeq data);
    }

    interface CallService {
        void startCall(string from, string to);
        void endCall(string user);
        // Señalización WebRTC básica: oferta, respuesta y candidatos
        void sendRtcSignal(string from, string to, string type, string payload);
        StringSeq pullRtcSignals(string user);
    }
}
