package com.identitycrisis.shared.net.server;

public record RoundStateUpdate(int roundNumber, byte phaseOrdinal,
                                double timerRemaining) { }
