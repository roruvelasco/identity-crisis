package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.SafeZoneTmxLoader;

import java.util.List;

/**
 * Static registry of the eight candidate safe-zone rectangles defined by the
 * {@code safezoneN} layers of {@code ArenaMap.tmx}.
 *
 * <p>Loaded eagerly by class initialisation on first reference (thread-safe
 * via the JVM's class-init lock).  If the TMX is missing or malformed the
 * static initialiser throws and the server fails fast at startup — this is
 * intentional, since gameplay cannot proceed without a valid safe-zone pool.
 *
 * <p>Each round, {@code SafeZoneManager} shuffles a copy of {@link #ALL} and
 * picks the first <em>N</em> entries to use as that round's active zones.
 */
public final class SafeZoneSpots {

    /** Resource path of the canonical map containing the safe-zone layers. */
    public static final String DEFAULT_TMX_PATH = "/sprites/map/ArenaMap.tmx";

    /**
     * The eight safe-zone rectangles, sorted ascending by id.
     *
     * <p>This list is unmodifiable.  Callers that need to shuffle should copy
     * into a fresh list first.
     */
    public static final List<SafeZone> ALL = SafeZoneTmxLoader.load(DEFAULT_TMX_PATH);

    private SafeZoneSpots() {}
}
