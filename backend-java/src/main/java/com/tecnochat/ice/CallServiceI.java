package com.tecnochat.ice;

import TecnoChat.CallService;
import com.tecnochat.server.ClientHandler;
import com.tecnochat.server.MessageHistory;
import com.zeroc.Ice.Current;

public class CallServiceI implements CallService {
    @Override
    public void startCall(String from, String to, Current current) {
        boolean delivered = ClientHandler.notifyIncomingCall(from, to);
        MessageHistory.saveLlamadaIndividual(from, to, delivered ? "INICIADA_RPC" : "RECHAZADA_RPC");
    }

    @Override
    public void endCall(String user, Current current) {
        MessageHistory.saveLlamadaIndividual(user, "DESCONOCIDO", "TERMINADA_RPC");
        ClientHandler.notifyCallEnded(user);
    }

    @Override
    public void sendRtcSignal(String from, String to, String type, String payload, Current current) {
        // Encola señalización WebRTC para el destinatario (RPC/Web)
        ClientHandler.enqueueRtcSignal(to, from, type, payload);
        // Guardar en historial básico de llamadas WebRTC
        String estado;
        switch (type == null ? "" : type.toLowerCase()) {
            case "offer":
                estado = "WEBRTC_OFFER";
                break;
            case "answer":
                estado = "WEBRTC_ANSWER";
                break;
            case "hangup":
                estado = "WEBRTC_HANGUP";
                break;
            default:
                estado = null;
        }
        if (estado != null) {
            MessageHistory.saveLlamadaIndividual(from, to, estado);
        }
    }

    @Override
    public String[] pullRtcSignals(String user, Current current) {
        return ClientHandler.drainRtcSignals(user).toArray(new String[0]);
    }
}
