package com.identitycrisis.shared.util;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Networking utility helpers used by the room-code system.
 */
public final class NetworkUtils {

    private static final Logger LOG = new Logger("NetworkUtils");

    /**
     * Name prefixes of interfaces that are <em>never</em> the real LAN.
     * Docker bridges ({@code docker0}, {@code br-*}), libvirt
     * ({@code virbr0}), VMware ({@code vmnet*}), VirtualBox
     * ({@code vboxnet*}), WSL ({@code veth*}), VPN tunnels
     * ({@code tun*}, {@code tap*}, {@code zt*}, {@code wg*}) and Windows
     * virtual adapters all show up with {@code isUp() == true} but are not
     * routable from other machines on the LAN.
     */
    private static final String[] VIRTUAL_IFACE_PREFIXES = {
        "docker", "br-", "virbr", "vmnet", "vboxnet", "veth",
        "tun", "tap", "zt", "wg", "utun", "ham", "ppp", "vEthernet"
    };

    private NetworkUtils() {}

    /**
     * Returns the machine's outward-facing LAN IPv4 address.
     *
     * <p>Strategy (first hit wins):
     * <ol>
     *   <li><b>UDP-connect probe</b> — opens a datagram socket "connected" to a
     *       public IP. No packet is actually sent; the kernel just picks the
     *       interface it would use for outbound traffic and exposes its address
     *       via {@link DatagramSocket#getLocalAddress()}. This is the single
     *       most reliable way to discover the real LAN IP because it defers to
     *       the host's routing table.</li>
     *   <li><b>Interface scan fallback</b> — iterates all non-loopback,
     *       non-virtual interfaces (filtering docker/VM/VPN adapters by name,
     *       since {@link NetworkInterface#isVirtual()} does <i>not</i> flag
     *       them) and returns the first private IPv4 found, preferring
     *       {@code 192.168.*} and {@code 10.*} over {@code 172.16-31.*}.</li>
     *   <li><b>Loopback</b> — last resort {@code "127.0.0.1"} so single-machine
     *       testing still works.</li>
     * </ol>
     *
     * @return IPv4 address string, e.g. {@code "192.168.1.42"}
     */
    public static String getLanIp() {
        // 1. UDP-connect probe — asks the OS routing table directly.
        try (DatagramSocket probe = new DatagramSocket()) {
            // 8.8.8.8:53 is Google DNS. No datagram is actually sent because
            // we never call send() — connect() on a DatagramSocket just sets
            // the default peer and triggers route lookup.
            probe.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = probe.getLocalAddress();
            if (local instanceof Inet4Address
                    && !local.isAnyLocalAddress()
                    && !local.isLoopbackAddress()
                    && !local.isLinkLocalAddress()) {
                String ip = local.getHostAddress();
                LOG.info("getLanIp() picked " + ip + " via UDP probe");
                return ip;
            }
        } catch (Exception e) {
            LOG.warn("UDP probe for LAN IP failed; falling back to interface scan: " + e.getMessage());
        }

        // 2. Interface scan fallback, with bridge/VM interfaces filtered by name
        //    and with private-range preference.
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                    if (isVirtualByName(ni)) continue;
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (!(addr instanceof Inet4Address)) continue;
                        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                        String ip = addr.getHostAddress();
                        candidates.add(ni.getName() + "=" + ip);
                        int rank = rankIp(ip);
                        if (rank < bestRank) {
                            bestRank = rank;
                            best     = ip;
                        }
                    }
                }
            }
        } catch (SocketException ignored) {}

        if (best != null) {
            LOG.info("getLanIp() picked " + best + " via interface scan (candidates: " + candidates + ")");
            return best;
        }

        // 3. Loopback fallback — keeps single-machine testing functional.
        LOG.warn("getLanIp() found no non-virtual IPv4; falling back to 127.0.0.1");
        return "127.0.0.1";
    }

    /**
     * Returns {@code true} if the interface's system name matches any of the
     * {@link #VIRTUAL_IFACE_PREFIXES} — docker/VM/VPN/WSL bridges that
     * {@link NetworkInterface#isVirtual()} does not catch.
     */
    private static boolean isVirtualByName(NetworkInterface ni) {
        String name = ni.getName();
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String prefix : VIRTUAL_IFACE_PREFIXES) {
            if (lower.startsWith(prefix.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Lower rank = more preferred. Prefers real home/office LAN ranges
     * ({@code 192.168.*} and {@code 10.*}) over corporate/Docker-style
     * {@code 172.16-31.*} which is most often a docker/virtual bridge.
     */
    private static int rankIp(String ip) {
        if (ip.startsWith("192.168.")) return 0;
        if (ip.startsWith("10."))       return 1;
        if (ip.startsWith("172.")) {
            // 172.16.0.0–172.31.255.255 is private; anything else is public.
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return 3; // likely docker/vm
                } catch (NumberFormatException ignored) {}
            }
            return 4;
        }
        return 2; // other public-routable IPv4 (rare on LAN)
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

    public static int findFreeRoomPort() {
        int end = RoomCodec.ROOM_PORT_BASE + RoomCodec.ROOM_PORT_COUNT;
        List<Integer> ports = new ArrayList<>();
        for (int port = RoomCodec.ROOM_PORT_BASE; port < end; port++) {
            ports.add(port);
        }
        Collections.shuffle(ports);
        for (int port : ports) {
            try (ServerSocket probe = new ServerSocket(port)) {
                probe.setReuseAddress(true);
                return port;
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("Could not find a free room port");
    }
}
