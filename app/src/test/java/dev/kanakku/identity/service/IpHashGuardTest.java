package dev.kanakku.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IpHashGuardTest {

  @Test
  void admitsUpToTheCapPerIpAndRefusesTheRestAtOnce() {
    IpHashGuard guard = new IpHashGuard(2);

    Optional<IpHashGuard.Permit> first = guard.tryEnter("203.0.113.5");
    Optional<IpHashGuard.Permit> second = guard.tryEnter("203.0.113.5");

    assertThat(first).isPresent();
    assertThat(second).isPresent();
    assertThat(guard.tryEnter("203.0.113.5")).isEmpty();
  }

  @Test
  void anotherIpIsNotAffected() {
    IpHashGuard guard = new IpHashGuard(1);
    assertThat(guard.tryEnter("203.0.113.5")).isPresent();

    assertThat(guard.tryEnter("203.0.113.6")).isPresent();
  }

  @Test
  void closingAPermitFreesTheSpotAndForgetsTheIp() {
    IpHashGuard guard = new IpHashGuard(1);
    IpHashGuard.Permit permit = guard.tryEnter("203.0.113.5").orElseThrow();
    assertThat(guard.tryEnter("203.0.113.5")).isEmpty();

    permit.close();
    permit.close(); // closing twice must not free a second spot

    assertThat(guard.tryEnter("203.0.113.5")).isPresent();
    assertThat(guard.tryEnter("203.0.113.5")).isEmpty();
    assertThat(guard.trackedIps()).isEqualTo(1);
  }

  @Test
  void noIpIsTrackedOnceEveryPermitIsClosed() {
    IpHashGuard guard = new IpHashGuard(3);
    guard.tryEnter("203.0.113.5").orElseThrow().close();
    guard.tryEnter("2001:db8:1:2::1").orElseThrow().close();

    assertThat(guard.trackedIps()).isZero();
  }

  @Test
  void ipv6AddressesInOneSlash64ShareTheCap() {
    IpHashGuard guard = new IpHashGuard(1);
    assertThat(guard.tryEnter("2001:db8:1:2::1")).isPresent();

    assertThat(guard.tryEnter("2001:db8:1:2:ffff:eeee:dddd:cccc")).isEmpty();
    assertThat(guard.tryEnter("2001:db8:1:3::1")).isPresent();
  }

  @Test
  void aCapOfZeroRefusesEveryone() {
    assertThat(new IpHashGuard(0).tryEnter("203.0.113.5")).isEmpty();
  }
}
