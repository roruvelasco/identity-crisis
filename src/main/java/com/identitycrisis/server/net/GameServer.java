package com.identitycrisis.server.net;

import com.identitycrisis.server.game.*;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** TCP server. Manages connections, lobby, game start. */
public class GameServer {

    private ServerSocket serverSocket;
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private LobbyManager lobbyManager;
    private ServerGameLoop gameLoop;
    private ClientMessageRouter router;

    public GameServer(int port) { }

    /** Accept connections. Blocks or runs on own thread. */
    public void start() { }

    /** Called when lobby signals all ready. */
    public void startGame() { }

    /** Remove disconnected client. */
    public void removeClient(ClientConnection client) { }

    /** Broadcast raw bytes to ALL clients. */
    public void broadcastToAll(byte[] data) { }

    /** Send to specific client. */
    public void sendToClient(ClientConnection client, byte[] data) { }

    public List<ClientConnection> getClients() { throw new UnsupportedOperationException("stub"); }

    public LobbyManager getLobbyManager() { throw new UnsupportedOperationException("stub"); }

    public ServerGameLoop getGameLoop() { throw new UnsupportedOperationException("stub"); }

    public void shutdown() { }
}
