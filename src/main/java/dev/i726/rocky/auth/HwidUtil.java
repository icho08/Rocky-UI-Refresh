package dev.i726.rocky.auth;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class HwidUtil {

    private HwidUtil() {}

    public static String generate() {
        StringBuilder raw = new StringBuilder();

        raw.append(getMacAddresses());
        raw.append("|");
        raw.append(System.getProperty("os.name", "unknown"));
        raw.append("|");
        raw.append(System.getProperty("os.arch", "unknown"));
        raw.append("|");
        raw.append(System.getProperty("user.name", "unknown"));
        raw.append("|");
        raw.append(getHostname());

        return sha256(raw.toString());
    }

    private static String getMacAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length == 6) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : mac) {
                            sb.append(String.format("%02X", b));
                        }
                        macs.add(sb.toString());
                    }
                }
            }
        } catch (Exception ignored) {}
        Collections.sort(macs);
        return String.join(",", macs);
    }

    private static String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getenv().getOrDefault("COMPUTERNAME",
                   System.getenv().getOrDefault("HOSTNAME", "unknown"));
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
