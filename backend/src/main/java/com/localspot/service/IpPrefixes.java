package com.localspot.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * "Dải IP" của luật cảnh báo U3: IPv4 theo {@code /24} ({@code 203.0.113.7 → 203.0.113.0/24}) như requirements §5; IPv6
 * theo {@code /64} — một mạng con IPv6 thường cấp cho cả hộ gia đình / một thuê bao, tương đương vai trò {@code /24}.
 * Địa chỉ IPv4 dạng IPv6 ({@code ::ffff:a.b.c.d}) được coi là IPv4.
 */
public final class IpPrefixes {

    /** Chỉ nhận chuỗi ký tự số / hex của một địa chỉ — không bao giờ tra DNS cho một tên miền. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

    private IpPrefixes() {}

    public static String of(String ipAddress) {
        if (ipAddress == null || !IP_LITERAL.matcher(ipAddress).matches()) {
            return "unknown";
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            byte[] b = address.getAddress();
            if (address instanceof Inet4Address) {
                return (b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff) + ".0/24";
            }
            if (address instanceof Inet6Address) {
                StringBuilder prefix = new StringBuilder();
                for (int i = 0; i < 8; i += 2) {
                    prefix.append(Integer.toHexString(((b[i] & 0xff) << 8) | (b[i + 1] & 0xff)))
                            .append(':');
                }
                return prefix.append(":/64").toString();
            }
        } catch (UnknownHostException e) {
            // không phải địa chỉ hợp lệ — rơi xuống "unknown"
        }
        return "unknown";
    }
}
