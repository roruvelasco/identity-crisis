package com.identitycrisis.shared.net.server;

import com.identitycrisis.shared.model.SafeZone;
import java.util.List;

public record SafeZoneUpdate(List<SafeZone> zones) { }
