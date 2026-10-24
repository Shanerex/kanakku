package dev.kanakku.identity.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThrottleIpTest {

  @Test
  void anIpv4AddressKeepsItsFullAddress() {
    assertThat(ThrottleIp.of("203.0.113.7")).isEqualTo("203.0.113.7");
  }

  @Test
  void anIpv6AddressKeepsOnlyItsFirst64Bits() {
    assertThat(ThrottleIp.of("2001:db8:aaaa:bbbb:1:2:3:4"))
        .isEqualTo(ThrottleIp.of("2001:db8:aaaa:bbbb:ffff:ffff:ffff:ffff"))
        .isEqualTo("2001:db8:aaaa:bbbb:0:0:0:0");
  }

  @Test
  void aDifferent64PrefixIsADifferentKey() {
    assertThat(ThrottleIp.of("2001:db8:aaaa:bbbb::1"))
        .isNotEqualTo(ThrottleIp.of("2001:db8:aaaa:bbbc::1"));
  }

  @Test
  void anIpv4MappedIpv6AddressIsTheIpv4Address() {
    assertThat(ThrottleIp.of("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
  }

  @Test
  void aZoneIdIsIgnored() {
    assertThat(ThrottleIp.of("fe80::1%eth0")).isEqualTo(ThrottleIp.of("fe80::2"));
  }

  @Test
  void anUnreadableAddressFallsBackToTheUnspecifiedAddress() {
    assertThat(ThrottleIp.of("not an ip")).isEqualTo("0.0.0.0");
    assertThat(ThrottleIp.of(null)).isEqualTo("0.0.0.0");
  }
}
