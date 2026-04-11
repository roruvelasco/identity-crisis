package com.identitycrisis.shared.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Networking utility helpers used by the room-code system.
 */
public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Returns the machine's LAN IPv4 address (first non-loopback, non-link-local
     * address found on an active interface).
     *
     * <p>Falls back to {@code "127.0.0.1"} if no suitable address is found —
     * which still works for single-machine testing.
     *
     * @return IPv4 address string, e.g. {@code "192.168.1.42"}
     */
    public static String getLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return "127.0.0.1";
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {}
        return "127.0.0.1";
    }

    /**
     * Finds a free TCP port by briefly opening a {@link ServerSocket} on port 0
     * (which lets the OS pick an available port), then immediately closing it.
     *
     * <p>There is a small TOCTOU window between closing the probe socket and the
     * caller binding to the returned port, but in practice this is negligible
     * on a LAN with a small number of players.
     *
     * @return an available port number in the ephemeral range
     * @throws RuntimeException if no free port can be found
     */
    public static int findFreePort() {
        try (ServerSocket probe = new ServerSocket(0)) {
            probe.setReuseAddress(true);
            return probe.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("Could not find a free port", e);
        }
    }
}
