package dev.kanakku.platform.ratelimit;

import static dev.kanakku.jooq.Tables.RATE_LIMIT_HITS;

import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Reads and writes rate limit hits. The limit rules live in RateLimiter. */
@Repository
class RateLimitRepository {

  private final DSLContext dsl;

  RateLimitRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Takes a transaction-scoped advisory lock for one (bucket, key). */
  void lock(String bucket, String key) {
    dsl.fetch("select pg_advisory_xact_lock(hashtextextended(? || '|' || ?, 0))", bucket, key);
  }

  /** Hit times for the (bucket, key) after {@code since}, oldest first. */
  List<OffsetDateTime> timesAfter(String bucket, String key, OffsetDateTime since) {
    return dsl.select(RATE_LIMIT_HITS.AT)
        .from(RATE_LIMIT_HITS)
        .where(RATE_LIMIT_HITS.BUCKET.eq(bucket))
        .and(RATE_LIMIT_HITS.KEY.eq(key))
        .and(RATE_LIMIT_HITS.AT.gt(since))
        .orderBy(RATE_LIMIT_HITS.AT.asc())
        .fetch(RATE_LIMIT_HITS.AT);
  }

  void insert(String bucket, String key, OffsetDateTime at) {
    dsl.insertInto(RATE_LIMIT_HITS)
        .set(RATE_LIMIT_HITS.BUCKET, bucket)
        .set(RATE_LIMIT_HITS.KEY, key)
        .set(RATE_LIMIT_HITS.AT, at)
        .execute();
  }

  /** Forgets every hit of the (bucket, key), for example after a successful verification. */
  void deleteAll(String bucket, String key) {
    dsl.deleteFrom(RATE_LIMIT_HITS)
        .where(RATE_LIMIT_HITS.BUCKET.eq(bucket))
        .and(RATE_LIMIT_HITS.KEY.eq(key))
        .execute();
  }

  int deleteBefore(OffsetDateTime cutoff) {
    return dsl.deleteFrom(RATE_LIMIT_HITS).where(RATE_LIMIT_HITS.AT.lt(cutoff)).execute();
  }
}
