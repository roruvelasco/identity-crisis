package com.identitycrisis.shared.net.server;

import com.identitycrisis.shared.model.ChaosEventType;

public record ChaosEventMessage(ChaosEventType type, double durationSeconds) { }
