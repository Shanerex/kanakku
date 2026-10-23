package dev.kanakku.identity.internal;

/** An edit carried an out-of-date version, so someone else changed the record first. */
public class VersionConflictException extends RuntimeException {

  public VersionConflictException() {
    super("stale version");
  }
}
