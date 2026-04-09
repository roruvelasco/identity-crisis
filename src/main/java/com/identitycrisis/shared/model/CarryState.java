package com.identitycrisis.shared.model;

/** Represents a carry relationship. Only server creates/destroys these. */
public record CarryState(int carrierPlayerId, int carriedPlayerId) { }
