package dev.kanakku.groups.repository;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;

import dev.kanakku.jooq.tables.GroupMembers;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * The membership guards every groups query carries, so scoping to the caller happens inside the SQL
 * and a row of someone else's group is never selected, locked or changed.
 */
final class Scope {

  private Scope() {}

  /** The caller is a current member of the group whose id is {@code groupId}. */
  static Condition member(Field<UUID> groupId, UUID callerId) {
    GroupMembers me = GROUP_MEMBERS.as("me");
    return DSL.exists(
        DSL.selectOne()
            .from(me)
            .where(me.GROUP_ID.eq(groupId))
            .and(me.USER_ID.eq(callerId))
            .and(me.LEFT_AT.isNull()));
  }

  /** The caller is the current Owner of the group whose id is {@code groupId}. */
  static Condition owner(Field<UUID> groupId, UUID callerId) {
    GroupMembers me = GROUP_MEMBERS.as("me");
    return DSL.exists(
        DSL.selectOne()
            .from(me)
            .where(me.GROUP_ID.eq(groupId))
            .and(me.USER_ID.eq(callerId))
            .and(me.ROLE.eq("OWNER"))
            .and(me.LEFT_AT.isNull()));
  }
}
