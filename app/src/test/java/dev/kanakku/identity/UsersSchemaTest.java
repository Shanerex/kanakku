package dev.kanakku.identity;

import static dev.kanakku.jooq.Tables.PASSWORD_CREDENTIALS;
import static dev.kanakku.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.TestData;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

@SpringBootTest
class UsersSchemaTest extends PostgresIntegrationTest {

  @Autowired DSLContext dsl;

  @Test
  void newUserDefaultsToUnverifiedInrAndVersionZero() {
    UUID id = UUID.randomUUID();
    dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, TestData.email())
        .set(USERS.DISPLAY_NAME, "Asha")
        .execute();

    var row = dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOne();

    assertThat(row).isNotNull();
    assertThat(row.getEmailVerified()).isFalse();
    assertThat(row.getDefaultCurrency()).isEqualTo("INR");
    assertThat(row.getUpiId()).isNull();
    assertThat(row.getVersion()).isZero();
    assertThat(row.getCreatedAt()).isNotNull();
  }

  @Test
  void emailIsUniqueIgnoringCase() {
    String email = TestData.email();
    dsl.insertInto(USERS)
        .set(USERS.ID, UUID.randomUUID())
        .set(USERS.EMAIL, email)
        .set(USERS.DISPLAY_NAME, "A")
        .execute();

    assertThatThrownBy(
            () ->
                dsl.insertInto(USERS)
                    .set(USERS.ID, UUID.randomUUID())
                    .set(USERS.EMAIL, email.toUpperCase())
                    .set(USERS.DISPLAY_NAME, "B")
                    .execute())
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void passwordHashLivesInItsOwnTable() {
    assertThat(USERS.fieldsRow().fields()).extracting("name").doesNotContain("password_hash");
    assertThat(PASSWORD_CREDENTIALS.fieldsRow().fields())
        .extracting("name")
        .contains("user_id", "password_hash");
  }

  private UUID userWithPhone(String phone, boolean verified) {
    UUID id = UUID.randomUUID();
    dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, TestData.email())
        .set(USERS.DISPLAY_NAME, "P")
        .set(USERS.PHONE_E164, phone)
        .set(USERS.PHONE_VERIFIED_AT, verified ? java.time.OffsetDateTime.now() : null)
        .execute();
    return id;
  }

  @Test
  void aNewUserHasNoPhone() {
    UUID id = userWithPhone(null, false);

    var row = dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOne();

    assertThat(row.getPhoneE164()).isNull();
    assertThat(row.getPhoneVerifiedAt()).isNull();
  }

  @Test
  void manyUsersMayHoldTheSameUnverifiedNumber() {
    String phone = TestData.phone();
    userWithPhone(phone, false);
    userWithPhone(phone, false);
  }

  @Test
  void aNumberCanBeVerifiedByOnlyOneUser() {
    String phone = TestData.phone();
    userWithPhone(phone, true);
    userWithPhone(phone, false);

    assertThatThrownBy(() -> userWithPhone(phone, true)).isInstanceOf(DuplicateKeyException.class);
  }
}
