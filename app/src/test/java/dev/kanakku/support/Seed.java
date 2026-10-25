package dev.kanakku.support;

import static dev.kanakku.jooq.Tables.GROUPS;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.USERS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** Inserts users, groups and members straight into the tables, so ledger tests stand alone. */
public final class Seed {

  private Seed() {}

  public static UUID user(DSLContext dsl, String name) {
    UUID id = UUID.randomUUID();
    dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, TestData.email())
        .set(USERS.DISPLAY_NAME, name)
        .execute();
    return id;
  }

  public static UUID group(DSLContext dsl, String currency) {
    UUID id = UUID.randomUUID();
    dsl.insertInto(GROUPS)
        .set(GROUPS.ID, id)
        .set(GROUPS.NAME, "Group " + id)
        .set(GROUPS.TYPE, "TRIP")
        .set(GROUPS.CURRENCY, currency)
        .execute();
    return id;
  }

  /** A guest member row; its id is what journal lines name. */
  public static UUID guest(DSLContext dsl, UUID groupId, String name) {
    UUID id = UUID.randomUUID();
    dsl.insertInto(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.ID, id)
        .set(GROUP_MEMBERS.GROUP_ID, groupId)
        .set(GROUP_MEMBERS.GUEST_NAME, name)
        .set(GROUP_MEMBERS.ROLE, "MEMBER")
        .execute();
    return id;
  }

  /**
   * A user who is a current member of the group, created on first use; the caller that balance
   * reads are scoped to.
   */
  public static UUID caller(DSLContext dsl, UUID groupId) {
    UUID existing =
        dsl.select(GROUP_MEMBERS.USER_ID)
            .from(GROUP_MEMBERS)
            .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
            .and(GROUP_MEMBERS.USER_ID.isNotNull())
            .and(GROUP_MEMBERS.LEFT_AT.isNull())
            .fetchAny(GROUP_MEMBERS.USER_ID);
    if (existing != null) {
      return existing;
    }
    UUID userId = user(dsl, "Caller");
    dsl.insertInto(GROUP_MEMBERS)
        .set(GROUP_MEMBERS.ID, UUID.randomUUID())
        .set(GROUP_MEMBERS.GROUP_ID, groupId)
        .set(GROUP_MEMBERS.USER_ID, userId)
        .set(GROUP_MEMBERS.ROLE, "OWNER")
        .execute();
    return userId;
  }

  /** {@code count} guest members in a new INR group, as {@code [groupId, member...]}. */
  public static List<UUID> groupWithGuests(DSLContext dsl, int count) {
    UUID groupId = group(dsl, "INR");
    List<UUID> ids = new ArrayList<>();
    ids.add(groupId);
    for (int i = 0; i < count; i++) {
      ids.add(guest(dsl, groupId, "Guest " + i));
    }
    return ids;
  }
}
