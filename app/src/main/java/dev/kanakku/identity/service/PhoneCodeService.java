package dev.kanakku.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Makes, stores-as-hash and sends one-time codes. A code is 6 digits from {@link SecureRandom};
 * only its HMAC-SHA256 under the server secret (and bound to the number) is ever stored, and
 * compared in constant time. The secret is required, 32 or more characters, whenever an SMS
 * provider is configured.
 */
@Service
public class PhoneCodeService {

  /** How long a code works. */
  public static final Duration TTL = Duration.ofMinutes(10);

  static final int MIN_SECRET_LENGTH = 32;
  private static final int CODE_BOUND = 1_000_000;

  private final SmsGateway gateway;
  private final byte[] secret;
  private final SecureRandom random = new SecureRandom();

  PhoneCodeService(SmsGateway gateway, @Value("${kanakku.sms.code-secret:}") String secret) {
    if (gateway.enabled() && (secret == null || secret.length() < MIN_SECRET_LENGTH)) {
      throw new IllegalStateException(
          "kanakku.sms.code-secret (KANAKKU_SMS_CODE_SECRET) must be at least "
              + MIN_SECRET_LENGTH
              + " characters when an SMS webhook is configured");
    }
    this.gateway = gateway;
    this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
  }

  /** Whether a provider is configured, so numbers can be verified. */
  public boolean enabled() {
    return gateway.enabled();
  }

  /** A fresh 6-digit code, zero padded. */
  public String newCode() {
    return String.format("%06d", random.nextInt(CODE_BOUND));
  }

  /** The value to store in place of {@code code} for the number {@code e164}. */
  public byte[] hash(String e164, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      mac.update(e164.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) '|');
      return mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * The rate-limit key for a number: an HMAC under the server secret, so the table that counts
   * sends and wrong codes never holds a phone number.
   */
  public String numberKey(String e164) {
    try {
      byte[] digest;
      if (secret.length == 0) {
        digest = MessageDigest.getInstance("SHA-256").digest(e164.getBytes(StandardCharsets.UTF_8));
      } else {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        mac.update("rate-limit|".getBytes(StandardCharsets.UTF_8));
        digest = mac.doFinal(e164.getBytes(StandardCharsets.UTF_8));
      }
      return "number:" + java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  /** True when {@code attempt} is the code that produced {@code stored} for this number. */
  public boolean matches(byte[] stored, String e164, String attempt) {
    return stored != null && attempt != null && MessageDigest.isEqual(stored, hash(e164, attempt));
  }

  /** Sends the code to the number. False when the provider did not accept it. */
  public boolean send(String e164, String code) {
    return gateway.send(
        e164, "Your Kanakku code is " + code + ". It expires in 10 minutes. Do not share it.");
  }
}
