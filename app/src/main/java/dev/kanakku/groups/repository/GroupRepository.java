package dev.kanakku.groups.repository;

import static dev.kanakku.jooq.Tables.GROUPS;
import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;

import dev.kanakku.groups.domain.Group;
import dev.kanakku.groups.domain.GroupType;
import dev.kanakku.groups.domain.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Reads and writes groups. Every read joins the caller's membership; every edit needs Owner. */
@Repository
public class GroupRepository {

  private final DSLContext dsl;

  GroupRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void insert(UUID id, String name, GroupType type, String currency, String cover) {
    dsl.insertInto(GROUPS)
        .set(GROUPS.ID, id)
        .set(GROUPS.NAME, name)
        .set(GROUPS.TYPE, type.name())
        .set(GROUPS.CURRENCY, currency)
        .set(GROUPS.COVER, cover)
        .execute();
  }

  /** The groups the caller currently belongs to, newest first. */
  public List<Group> listForCaller(UUID callerId) {
    return dsl.select(GROUPS.fields())
        .select(GROUP_MEMBERS.ROLE)
        .from(GROUPS)
        .join(GROUP_MEMBERS)
        .on(GROUP_MEMBERS.GROUP_ID.eq(GROUPS.ID))
        .and(GROUP_MEMBERS.USER_ID.eq(callerId))
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .orderBy(GROUPS.CREATED_AT.desc(), GROUPS.ID)
        .fetch(r -> toGroup(r, Role.valueOf(r.get(GROUP_MEMBERS.ROLE))));
  }

  /** The group, or empty when it does not exist or the caller is not in it. */
  public Optional<Group> findForCaller(UUID groupId, UUID callerId) {
    return dsl.select(GROUPS.fields())
        .select(GROUP_MEMBERS.ROLE)
        .from(GROUPS)
        .join(GROUP_MEMBERS)
        .on(GROUP_MEMBERS.GROUP_ID.eq(GROUPS.ID))
        .and(GROUP_MEMBERS.USER_ID.eq(callerId))
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .where(GROUPS.ID.eq(groupId))
        .fetchOptional(r -> toGroup(r, Role.valueOf(r.get(GROUP_MEMBERS.ROLE))));
  }

  /**
   * Edits the group if the caller is its Owner and {@code version} is current, in one statement.
   * Null arguments stay as they are.
   *
   * @return the saved group, or empty when the caller is not the Owner or the version is stale
   */
  public Optional<Group> update(
      UUID groupId, UUID callerId, int version, String name, GroupType type, String cover) {
    Map<Field<?>, Object> changes = new LinkedHashMap<>();
    if (name != null) {
      changes.put(GROUPS.NAME, name);
    }
    if (type != null) {
      changes.put(GROUPS.TYPE, type.name());
    }
    if (cover != null) {
      changes.put(GROUPS.COVER, cover);
    }
    changes.put(GROUPS.VERSION, GROUPS.VERSION.plus(1));
    return dsl.update(GROUPS)
        .set(changes)
        .where(GROUPS.ID.eq(groupId))
        .and(GROUPS.VERSION.eq(version))
        .and(Scope.owner(GROUPS.ID, callerId))
        .returning(GROUPS.fields())
        .fetchOptional()
        .map(r -> toGroup(r, Role.OWNER));
  }

  /**
   * Archives or unarchives the group if the caller is its Owner, the version is current and the
   * group is in the opposite state.
   */
  public Optional<Group> setArchived(UUID groupId, UUID callerId, int version, boolean archive) {
    return dsl.update(GROUPS)
        .set(
            GROUPS.ARCHIVED_AT,
            archive ? DSL.currentOffsetDateTime() : DSL.val(null, GROUPS.ARCHIVED_AT))
        .set(GROUPS.VERSION, GROUPS.VERSION.plus(1))
        .where(GROUPS.ID.eq(groupId))
        .and(GROUPS.VERSION.eq(version))
        .and(archive ? GROUPS.ARCHIVED_AT.isNull() : GROUPS.ARCHIVED_AT.isNotNull())
        .and(Scope.owner(GROUPS.ID, callerId))
        .returning(GROUPS.fields())
        .fetchOptional()
        .map(r -> toGroup(r, Role.OWNER));
  }

  private static Group toGroup(Record r, Role role) {
    return new Group(
        r.get(GROUPS.ID),
        r.get(GROUPS.NAME),
        GroupType.valueOf(r.get(GROUPS.TYPE)),
        r.get(GROUPS.CURRENCY),
        r.get(GROUPS.COVER),
        r.get(GROUPS.ARCHIVED_AT),
        r.get(GROUPS.VERSION),
        role);
  }
}
