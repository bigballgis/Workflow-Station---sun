package com.workflow.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which user an engine actor value (a task's assignee, a candidate user) names. Tasks hold the user id; tasks created
 * before the platform assigned by id hold the username instead, which
 * {@code TaskClaimSupport.engineActorMatchesPortalUser} already takes for the same person. The rule, read from
 * {@code sys_users} (deleted users included, so people who left are still found):
 * <ol>
 *   <li>a value equal to a user's id names that user;</li>
 *   <li>otherwise a value equal to a user's username (exactly, as the engine compares) names that user;</li>
 *   <li>any other value names no known user and stays as it is.</li>
 * </ol>
 */
@Repository
@RequiredArgsConstructor
public class EngineActorIdentities {

    private static final int BATCH = 500;
    private static final String BY_ID_OR_USERNAME_SQL =
            "SELECT id, username FROM sys_users WHERE id IN (%1$s) OR username IN (%1$s)";
    private static final String BY_ID_SQL = "SELECT id, username FROM sys_users WHERE id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    /** Each given value with the user id it names (itself when it names no known user), in the given order. */
    public Map<String, String> userIds(Collection<String> actors) {
        List<String> values = distinct(actors);
        Set<String> ids = new HashSet<>();
        Map<String, String> idByUsername = new HashMap<>();
        for (List<String> slice : slices(values)) {
            List<Object> args = new ArrayList<>(slice);
            args.addAll(slice);
            jdbcTemplate.query(String.format(BY_ID_OR_USERNAME_SQL, placeholders(slice)), (RowCallbackHandler) rs -> {
                ids.add(rs.getString(1));
                if (rs.getString(2) != null) {
                    idByUsername.put(rs.getString(2), rs.getString(1));
                }
            }, args.toArray());
        }
        Map<String, String> userIds = new LinkedHashMap<>();
        for (String value : values) {
            userIds.put(value, ids.contains(value) ? value : idByUsername.getOrDefault(value, value));
        }
        return userIds;
    }

    /** Every value that can name these users on a task: their ids and their usernames. */
    public Set<String> actorValues(Collection<String> userIds) {
        List<String> ids = distinct(userIds);
        Set<String> values = new LinkedHashSet<>(ids);
        for (List<String> slice : slices(ids)) {
            jdbcTemplate.query(String.format(BY_ID_SQL, placeholders(slice)), (RowCallbackHandler) rs -> {
                if (rs.getString(2) != null && !rs.getString(2).isBlank()) {
                    values.add(rs.getString(2));
                }
            }, slice.toArray());
        }
        return values;
    }

    private static List<String> distinct(Collection<String> values) {
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
    }

    private static List<List<String>> slices(List<String> values) {
        List<List<String>> slices = new ArrayList<>();
        for (int from = 0; from < values.size(); from += BATCH) {
            slices.add(values.subList(from, Math.min(from + BATCH, values.size())));
        }
        return slices;
    }

    private static String placeholders(List<String> slice) {
        return String.join(",", slice.stream().map(v -> "?").toList());
    }
}
