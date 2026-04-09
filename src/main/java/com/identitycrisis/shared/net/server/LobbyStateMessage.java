package com.identitycrisis.shared.net.server;

public record LobbyStateMessage(int connectedCount, int requiredCount,
                                 String[] playerNames,
                                 boolean[] readyFlags) { }
