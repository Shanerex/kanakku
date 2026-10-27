package dev.kanakku.identity.api;

/** Which field of a user a search query matched. */
public enum MatchedBy {
  /** The display name. */
  NAME,
  /** The email address. */
  EMAIL,
  /** The mobile number, verified or not. */
  PHONE
}
