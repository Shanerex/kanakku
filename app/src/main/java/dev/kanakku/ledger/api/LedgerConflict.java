package dev.kanakku.ledger.api;

/** A post the ledger refuses because of what is already in the journal. */
public class LedgerConflict extends RuntimeException {

  /** Why the post was refused. */
  public enum Reason {
    /** The idempotency key already posted different content. */
    KEY_REUSED,
    /** The entry already has a reversal. */
    ALREADY_REVERSED,
    /** Only EXPENSE and SETTLEMENT entries can be reversed or replaced. */
    NOT_REVERSIBLE,
    /** No such entry in the given group. */
    NOT_IN_GROUP,
    /** The acting user is not a current member of the group. */
    NOT_A_MEMBER
  }

  private final Reason reason;

  public LedgerConflict(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
