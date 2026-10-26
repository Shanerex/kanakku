package dev.kanakku.groups.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Makes invite tokens and the hash that is stored in their place. */
public final class InviteTokens {

  private InviteTokens() {}

  /**
   * The token for an invite: HMAC-SHA256 of its id under the server secret, as 43 URL-safe
   * characters. Without the secret it cannot be guessed from the id, and it can be recomputed, so
   * no copy of it ever has to be stored.
   */
  public static String derive(byte[] secret, UUID inviteId) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      byte[] digest = mac.doFinal(inviteId.toString().getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The SHA-256 of the token; the only form the database holds. */
  public static byte[] hash(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
