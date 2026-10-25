package dev.kanakku.identity.service;

import dev.kanakku.identity.domain.DirectAdd;
import dev.kanakku.identity.domain.Profile;
import dev.kanakku.identity.exception.VersionConflictException;
import dev.kanakku.identity.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The caller's own profile. Every call takes the caller's id from the session, never a request. */
@Service
public class ProfileService {

  private final UserRepository users;

  ProfileService(UserRepository users) {
    this.users = users;
  }

  public Profile get(UUID callerId) {
    return users.findProfile(callerId).orElseThrow(() -> new IllegalStateException("no such user"));
  }

  /**
   * @param upiId blank means none
   * @throws VersionConflictException when {@code version} is not the stored version
   */
  public Profile update(
      UUID callerId, String displayName, String currency, String upiId, int version) {
    return update(callerId, displayName, currency, upiId, null, version);
  }

  /**
   * @param upiId blank means none
   * @param directAdd null keeps the stored choice
   * @throws VersionConflictException when {@code version} is not the stored version
   */
  public Profile update(
      UUID callerId,
      String displayName,
      String currency,
      String upiId,
      DirectAdd directAdd,
      int version) {
    String upi = upiId == null || upiId.isBlank() ? null : upiId.strip();
    return users
        .updateProfile(callerId, displayName.strip(), currency, upi, directAdd, version)
        .orElseThrow(VersionConflictException::new);
  }
}
