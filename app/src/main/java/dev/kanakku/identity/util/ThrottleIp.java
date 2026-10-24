package dev.kanakku.identity.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * The part of a client address the sign-in throttle counts. An IPv4 address counts in full. A
 * single IPv6 customer routinely owns a whole /64, so counting full IPv6 addresses would let an
 * attacker rotate through billions of them; only the first 64 bits count.
 */
public final class ThrottleIp {

  private static final String UNSPECIFIED = "0.0.0.0";

  private ThrottleIp() {}

  /** The throttle key for {@code ip}; an unreadable address maps to {@code 0.0.0.0}. */
  public static String of(String ip) {
    String address = ip == null ? "" : ip;
    int zone = address.indexOf('%');
    if (zone >= 0) {
      address = address.substring(0, zone);
    }
    InetAddress parsed;
    try {
      parsed = InetAddress.ofLiteral(address);
    } catch (IllegalArgumentException e) {
      return UNSPECIFIED;
    }
    if (parsed instanceof Inet6Address v6) {
      byte[] bytes = v6.getAddress();
      Arrays.fill(bytes, 8, 16, (byte) 0);
      try {
        return InetAddress.getByAddress(bytes).getHostAddress();
      } catch (java.net.UnknownHostException e) {
        return UNSPECIFIED;
      }
    }
    return parsed.getHostAddress();
  }
}
