package dev.kanakku.ledger.util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Splits an amount of minor units into whole minor units that sum exactly to it. Each exact share
 * is computed without rounding, floored, and the units left over go one each to the members with
 * the largest fractional remainders. Ties go to the earlier member in the given order, so the same
 * input always gives the same split.
 */
public final class LargestRemainder {

  private LargestRemainder() {}

  /** Splits {@code total} into {@code parts} equal shares; the extra units go to the first ones. */
  public static long[] split(long total, int parts) {
    if (parts < 1) {
      throw new IllegalArgumentException("parts must be at least 1");
    }
    long[] weights = new long[parts];
    Arrays.fill(weights, 1L);
    return allocate(total, weights);
  }

  /**
   * Splits {@code total} in proportion to {@code weights}.
   *
   * @throws IllegalArgumentException when there are no weights or one is not positive
   */
  public static long[] allocate(long total, long[] weights) {
    if (weights.length == 0 || Arrays.stream(weights).anyMatch(w -> w <= 0)) {
      throw new IllegalArgumentException("weights must be positive and not empty");
    }
    BigInteger weightSum =
        Arrays.stream(weights)
            .mapToObj(BigInteger::valueOf)
            .reduce(BigInteger.ZERO, BigInteger::add);
    // A negative total is split by its size and negated, so the earlier members take the extra
    // unit of a debt exactly as they do of a credit.
    boolean negative = total < 0;
    BigInteger bigTotal = BigInteger.valueOf(total).abs();

    long[] shares = new long[weights.length];
    BigInteger[] remainders = new BigInteger[weights.length];
    long assigned = 0;
    for (int i = 0; i < weights.length; i++) {
      BigInteger numerator = bigTotal.multiply(BigInteger.valueOf(weights[i]));
      // The total is non-negative here, so the remainder is too and so is the leftover below.
      BigInteger[] qr = numerator.divideAndRemainder(weightSum);
      BigInteger quotient = qr[0];
      BigInteger remainder = qr[1];
      shares[i] = quotient.longValueExact();
      remainders[i] = remainder;
      assigned += shares[i];
    }

    long leftover = bigTotal.subtract(BigInteger.valueOf(assigned)).longValueExact();
    Integer[] order =
        IntStream.range(0, weights.length)
            .boxed()
            .sorted(
                Comparator.<Integer, BigInteger>comparing(i -> remainders[i])
                    .reversed()
                    .thenComparing(Comparator.naturalOrder()))
            .toArray(Integer[]::new);
    for (int k = 0; k < leftover; k++) {
      shares[order[k]]++;
    }
    if (negative) {
      for (int i = 0; i < shares.length; i++) {
        shares[i] = -shares[i];
      }
    }
    return shares;
  }
}
