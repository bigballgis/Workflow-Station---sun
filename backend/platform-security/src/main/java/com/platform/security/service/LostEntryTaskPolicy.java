package com.platform.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Who handles a task whose holder lost Portal entry or left (platform access model §7.3; D-07, D-17, D-39, D-42). The
 * one definition for the Portal (what to offer) and the engine (what to allow and what to do by itself).
 *
 * <ul>
 *   <li>Once the holder has no Portal entry, any pool member with entry may release the task, take it over or hand it
 *       to another member with entry. While the holder has entry, the BU Approver / Leader rule in Admin Center
 *       applies.</li>
 *   <li>Once the holder has left (account INACTIVE or deleted; LOCKED is not leaving), a task assigned to that person,
 *       or held by them in a pool where nobody else has entry, goes to their Entity Manager, else their Function
 *       Manager, whoever is available (ACTIVE with Portal entry). An unclaimed list of named people goes to a manager
 *       only once all of them have left. A holder who only lost Portal entry keeps the task and asks for entry again;
 *       an unclaimed membership pool (BU + Role, legacy virtual group) waits for whoever gains the membership.</li>
 * </ul>
 *
 * <p>Entry and leaving are evaluated when asked and never stored on tasks: they change without any event a task
 * could follow. The decisions take the sets of users with entry / who left so a list can be judged with few queries.</p>
 */
@Service
@RequiredArgsConstructor
public class LostEntryTaskPolicy {

    /** How the people of a task were chosen. */
    public enum PoolKind {
        /** One person: the initiator, a form field, a fixed user, a manager. */
        NONE,
        /** A list of named people, e.g. a multi-user form field. */
        PEOPLE,
        /** A membership: BU + Role (BPMN pool, manual BU + Role, MI role row) or a legacy virtual group. */
        MEMBERSHIP
    }

    /**
     * A task as far as these rules care.
     *
     * @param assigneeId    current holder, or null when unclaimed
     * @param kind          how its people were chosen
     * @param poolMemberIds the pool's people in their order (Flowable candidates, or the virtual group's members); empty
     *                      for NONE
     */
    public record TaskHolding(String assigneeId, PoolKind kind, Set<String> poolMemberIds) {

        public TaskHolding {
            assigneeId = assigneeId == null || assigneeId.isBlank() ? null : assigneeId.trim();
            poolMemberIds = poolMemberIds == null ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(poolMemberIds));
        }

        public boolean held() {
            return assigneeId != null;
        }

        /** Everyone whose entry the decisions read. */
        public Set<String> people() {
            Set<String> people = new LinkedHashSet<>(poolMemberIds);
            if (held()) {
                people.add(assigneeId);
            }
            return people;
        }
    }

    private static final int BATCH = 500;
    private static final String DEPARTED_SQL = "SELECT id FROM sys_users "
            + "WHERE (status = 'INACTIVE' OR COALESCE(deleted, false) = true) AND id IN (%s)";
    private static final String MANAGERS_SQL =
            "SELECT id, entity_manager_id, function_manager_id FROM sys_users WHERE id IN (%s)";

    private final PortalEntryService portalEntryService;
    private final JdbcTemplate jdbcTemplate;

    /** The users among {@code userIds} that have Portal entry ({@link PortalEntryService#withPortalEntry}). */
    public Set<String> withPortalEntry(Collection<String> userIds) {
        return portalEntryService.withPortalEntry(userIds);
    }

    /** True when the holder lost entry and {@code actorId}, a pool member with entry, may act on the task. */
    public static boolean mayTakeOver(String actorId, TaskHolding task, Set<String> withEntry) {
        return task.held()
                && task.kind() != PoolKind.NONE
                && !withEntry.contains(task.assigneeId())
                && actorId != null
                && !actorId.equals(task.assigneeId())
                && withEntry.contains(actorId)
                && task.poolMemberIds().contains(actorId);
    }

    /** Pool members with entry, other than the holder: whom a lost holder's task may go to within its pool. */
    public static Set<String> takeoverTargets(TaskHolding task, Set<String> withEntry) {
        Set<String> targets = new LinkedHashSet<>();
        for (String member : task.poolMemberIds()) {
            if (withEntry.contains(member) && !member.equals(task.assigneeId())) {
                targets.add(member);
            }
        }
        return targets;
    }

    /**
     * Whose manager receives the task now that its people left (D-42), in order of preference; empty when the task
     * stays where it is. A held task: its holder, if they left and nobody else in its pool has entry (a pool member
     * with entry takes it over instead, AC-26). An unclaimed list of named people: all of them, once all have left.
     */
    public static List<String> departedOwners(TaskHolding task, Set<String> withEntry, Set<String> departed) {
        if (task.held()) {
            boolean poolCanTakeOver = task.kind() != PoolKind.NONE && !takeoverTargets(task, withEntry).isEmpty();
            return departed.contains(task.assigneeId()) && !poolCanTakeOver ? List.of(task.assigneeId()) : List.of();
        }
        boolean allLeft = task.kind() == PoolKind.PEOPLE && !task.poolMemberIds().isEmpty()
                && departed.containsAll(task.poolMemberIds());
        return allLeft ? List.copyOf(task.poolMemberIds()) : List.of();
    }

    /** The users among {@code userIds} who left: account INACTIVE or deleted (LOCKED is not leaving). */
    public Set<String> departedAmong(Collection<String> userIds) {
        Set<String> departed = new HashSet<>();
        for (List<String> slice : slices(userIds)) {
            departed.addAll(jdbcTemplate.queryForList(String.format(DEPARTED_SQL, placeholders(slice)), String.class,
                    slice.toArray()));
        }
        return departed;
    }

    /**
     * The manager who receives the task of {@code owners} (in order): the first owner's Entity Manager if available,
     * else their Function Manager, else the next owner's. Available = ACTIVE with Portal entry. Empty when nobody is.
     */
    public Optional<String> managerFor(List<String> owners) {
        Map<String, String[]> managers = new HashMap<>();
        for (List<String> slice : slices(owners)) {
            jdbcTemplate.query(String.format(MANAGERS_SQL, placeholders(slice)), (RowCallbackHandler) rs ->
                    managers.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3)}), slice.toArray());
        }
        Set<String> withEntry = portalEntryService.withPortalEntry(managers.values().stream()
                .flatMap(Arrays::stream).filter(Objects::nonNull).toList());
        for (String owner : owners) {
            for (String manager : managers.getOrDefault(owner, new String[0])) {
                if (manager != null && withEntry.contains(manager)) {
                    return Optional.of(manager);
                }
            }
        }
        return Optional.empty();
    }

    private static List<List<String>> slices(Collection<String> userIds) {
        List<String> ids = userIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        List<List<String>> slices = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            slices.add(ids.subList(from, Math.min(from + BATCH, ids.size())));
        }
        return slices;
    }

    private static String placeholders(List<String> slice) {
        return String.join(",", slice.stream().map(id -> "?").toList());
    }

}
