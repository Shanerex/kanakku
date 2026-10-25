package dev.kanakku.ledger.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.LongStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

/** Properties 2, 3 and 4 of the write-off split, on the pure maths. */
class LargestRemainderPropertiesTest {

  static final long MAX = 1_000_000_000_000_000L;

  @Property
  void property2_theSplitSumsExactlyToTheTotal(
      @ForAll @LongRange(min = -MAX, max = MAX) long total,
      @ForAll @IntRange(min = 1, max = 50) int parts) {
    long[] split = LargestRemainder.split(total, parts);

    assertThat(split).hasSize(parts);
    assertThat(LongStream.of(split).sum()).isEqualTo(total);
  }

  @Property
  void property3_aSingleAbsorberTakesTheWholeAmount(
      @ForAll @LongRange(min = -MAX, max = MAX) long total) {
    assertThat(LargestRemainder.split(total, 1)).containsExactly(total);
  }

  @Property
  void property4_partsDifferByAtMostOneMinorUnit(
      @ForAll @LongRange(min = -MAX, max = MAX) long total,
      @ForAll @IntRange(min = 1, max = 50) int parts) {
    long[] split = LargestRemainder.split(total, parts);

    long min = LongStream.of(split).min().orElseThrow();
    long max = LongStream.of(split).max().orElseThrow();
    assertThat(max - min).isLessThanOrEqualTo(1);
  }

  @Property
  void remainderUnitsGoToTheEarliestMembersSoTheSameInputGivesTheSameSplit(
      @ForAll @LongRange(min = 0, max = MAX) long total,
      @ForAll @IntRange(min = 1, max = 50) int parts) {
    long[] first = LargestRemainder.split(total, parts);
    long[] again = LargestRemainder.split(total, parts);

    assertThat(again).isEqualTo(first);
    for (int i = 1; i < first.length; i++) {
      assertThat(first[i]).isLessThanOrEqualTo(first[i - 1]);
    }
  }

  @Provide
  Arbitrary<long[]> weights() {
    return Arbitraries.longs()
        .between(1, 1_000_000)
        .list()
        .ofMinSize(1)
        .ofMaxSize(12)
        .map(l -> l.stream().mapToLong(Long::longValue).toArray());
  }

  @Property
  void weightedSharesSumToTheTotalAndStayWithinOneUnitOfTheExactShare(
      @ForAll @LongRange(min = -MAX, max = MAX) long total, @ForAll("weights") long[] weights) {
    long[] shares = LargestRemainder.allocate(total, weights);

    assertThat(LongStream.of(shares).sum()).isEqualTo(total);
    long weightSum = LongStream.of(weights).sum();
    for (int i = 0; i < weights.length; i++) {
      java.math.BigDecimal exact =
          new java.math.BigDecimal(total)
              .multiply(new java.math.BigDecimal(weights[i]))
              .divide(new java.math.BigDecimal(weightSum), 30, java.math.RoundingMode.HALF_EVEN);
      assertThat(new java.math.BigDecimal(shares[i]).subtract(exact).abs())
          .isLessThan(java.math.BigDecimal.ONE);
    }
  }

  @Test
  void sevenRupeesOverThreeGoesToTheFirstMemberFirst() {
    assertThat(LargestRemainder.split(7, 3)).containsExactly(3, 2, 2);
    assertThat(LargestRemainder.split(1, 3)).containsExactly(1, 0, 0);
    assertThat(LargestRemainder.split(0, 3)).containsExactly(0, 0, 0);
    assertThat(LargestRemainder.split(-7, 3)).containsExactly(-3, -2, -2);
  }

  @Test
  void refusesAnEmptyOrNonPositiveWeightList() {
    assertThatThrownBy(() -> LargestRemainder.split(10, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LargestRemainder.allocate(10, new long[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LargestRemainder.allocate(10, new long[] {1, 0}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Arrays.stream(LargestRemainder.allocate(10, new long[] {1, 1}))).hasSize(2);
  }
}
