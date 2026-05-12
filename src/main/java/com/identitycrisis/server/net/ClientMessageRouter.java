package com.identitycrisis.server.net;

import com.identitycrisis.server.game.ServerGameLoop;
import com.identitycrisis.shared.net.MessageDecoder;
import com.identitycrisis.shared.net.MessageType;

/**
 * Routes decoded client messages to server-side handlers.
 * Runs on ClientConnection's reader thread — must be thread-safe.
 */
public class ClientMessageRouter {

    private final GameServer server;

    public ClientMessageRouter(GameServer server) { this.server = server; }

    public void route(ClientConnection sender, MessageType type,
                      MessageDecoder decoder) {
        switch (type) {
            case C_JOIN_REQUEST -> {
                String name = decoder.decodeJoinRequest();
                server.getLobbyManager().handleJoin(sender, name);
            }
            case C_READY -> {
                decoder.decodeReady();
                server.getLobbyManager().handleReady(sender);
            }
            case C_PLAYER_INPUT -> {
                boolean[] flags = decoder.decodePlayerInput();
                ServerGameLoop loop = server.getGameLoop();
                if (loop != null) {
                    loop.enqueueInput(sender.getClientId(), flags);
                }
            }
            case C_CHAT_SEND -> {
                String text = decoder.decodeChatSend();
                server.getChatManager().handleChat(sender, text);
            }
            default -> { /* unknown message type — ignore */ }
        }
    }
}
