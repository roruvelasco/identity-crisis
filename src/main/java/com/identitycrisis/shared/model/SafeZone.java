package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;

/** Safe zone circle. Server knows which is true; clients just get a list. */
public record SafeZone(Vector2D position, double radius) { }
