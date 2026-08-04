package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves process-instance IDs for Admin User Portal audit keyword search
 * (instance id / title / business key / definition name / computed Request ID).
 *
 * <p>Runs once per audit query (not inside a JPA {@code Specification}) so count +
 * content queries do not double-hit the database.
 *
 * <p><b>Caps apply to distinct process instances, not audit log rows.</b>
 * Field-level change history can be huge; the audit list stays paginated on
 * {@code up_change_history}. These limits only bound the {@code processInstanceId IN (...)}
 * filter produced by the keyword box.
 *
 * <p>Balance (admin audit, typically with a date window):
 * <ul>
 *   <li>{@link #MAX_MATCHING_IDS} — final IN-list size (PG handles a few thousand well)</li>
 *   <li>{@link #MAX_REQUEST_ID_CANDIDATES} — PIs whose Request ID we compute; prefer
 *       recently active ones in the audit window via {@code MAX(timestamp) DESC}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPortalAuditProcessInstanceMatcher {

    /** Final matched process-instance IDs fed into change-history {@code IN (...)}. */
    static final int MAX_MATCHING_IDS = 3_000;

    /**
     * Distinct process instances considered for Request ID computation.
     * Prefer recent audit activity; not a row cap on {@code up_change_history}.
     */
    static final int MAX_REQUEST_ID_CANDIDATES = 10_000;

    /** Skip Request ID scan for ultra-short tokens (too broad / low signal). */
    static final int MIN_REQUEST_ID_KEYWORD_LENGTH = 2;

    private final JdbcTemplate jdbcTemplate;
    private final ProcessInstanceRepository processInstanceRepository;
    private final RequestIdEnricher requestIdEnricher;
    private final ObjectMapper objectMapper;

    /**
     * @param rawKeyword user input (process instance id / title / Request ID fragment)
     * @param startTime  optional audit window start (scopes Request ID candidate set)
     * @param endTime    optional audit window end
     * @return matching process instance ids (may be empty); never null
     */
    public List<String> resolveMatchingProcessInstanceIds(
            String rawKeyword, Instant startTime, Instant endTime) {
        if (rawKeyword == null || rawKeyword.isBlank()) {
            return List.of();
        }
        String keyword = rawKeyword.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(matchByIndexedColumns(keyword));
        if (ids.size() < MAX_MATCHING_IDS
                && keyword.length() >= MIN_REQUEST_ID_KEYWORD_LENGTH) {
            ids.addAll(matchByComputedRequestId(keyword, startTime, endTime, ids));
        }
        if (ids.size() > MAX_MATCHING_IDS) {
            log.debug(
                    "Audit PI keyword matched {} instances; truncating to {}",
                    ids.size(), MAX_MATCHING_IDS);
            return ids.stream().limit(MAX_MATCHING_IDS).toList();
        }
        return List.copyOf(ids);
    }

    private List<String> matchByIndexedColumns(String keywordLower) {
        String pattern = "%" + escapeLike(keywordLower) + "%";
        try {
            return jdbcTemplate.query(
                    """
                            SELECT id FROM up_process_instance
                            WHERE LOWER(id) LIKE ? ESCAPE '\\'
                               OR LOWER(COALESCE(title, '')) LIKE ? ESCAPE '\\'
                               OR LOWER(COALESCE(business_key, '')) LIKE ? ESCAPE '\\'
                               OR LOWER(COALESCE(process_definition_name, '')) LIKE ? ESCAPE '\\'
                            LIMIT ?
                            """,
                    (rs, rowNum) -> rs.getString("id"),
                    pattern, pattern, pattern, pattern, MAX_MATCHING_IDS);
        } catch (RuntimeException e) {
            log.warn("Audit process-instance column search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> matchByComputedRequestId(
            String keywordLower,
            Instant startTime,
            Instant endTime,
            Set<String> alreadyMatched) {
        List<String> candidateIds = loadRequestIdCandidateIds(startTime, endTime);
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        List<ProcessInstance> instances = processInstanceRepository.findAllById(candidateIds);
        Set<String> codes = instances.stream()
                .map(ProcessInstance::getFunctionUnitCode)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            return List.of();
        }
        RequestIdEnricher.SpecCache specs = requestIdEnricher.resolveSpecs(codes);
        List<String> matched = new ArrayList<>();
        for (ProcessInstance pi : instances) {
            if (alreadyMatched.contains(pi.getId())) {
                continue;
            }
            Map<String, Object> variables = resolveVariables(pi);
            if (variables.isEmpty()) {
                continue;
            }
            String requestId = requestIdEnricher.buildRequestId(
                    specs, pi.getFunctionUnitCode(), variables);
            if (requestId != null && requestId.toLowerCase(Locale.ROOT).contains(keywordLower)) {
                matched.add(pi.getId());
                if (alreadyMatched.size() + matched.size() >= MAX_MATCHING_IDS) {
                    break;
                }
            }
        }
        return matched;
    }

    private List<String> loadRequestIdCandidateIds(Instant startTime, Instant endTime) {
        try {
            // GROUP BY process_instance_id — one row per PI even if thousands of field changes.
            // ORDER BY MAX(timestamp) — prefer recently active processes when capping.
            if (startTime != null && endTime != null) {
                return jdbcTemplate.query(
                        """
                                SELECT process_instance_id AS id
                                FROM up_change_history
                                WHERE process_instance_id IS NOT NULL
                                  AND timestamp >= ?
                                  AND timestamp <= ?
                                GROUP BY process_instance_id
                                ORDER BY MAX(timestamp) DESC
                                LIMIT ?
                                """,
                        (rs, rowNum) -> rs.getString("id"),
                        java.sql.Timestamp.from(startTime),
                        java.sql.Timestamp.from(endTime),
                        MAX_REQUEST_ID_CANDIDATES);
            }
            if (startTime != null) {
                return jdbcTemplate.query(
                        """
                                SELECT process_instance_id AS id
                                FROM up_change_history
                                WHERE process_instance_id IS NOT NULL
                                  AND timestamp >= ?
                                GROUP BY process_instance_id
                                ORDER BY MAX(timestamp) DESC
                                LIMIT ?
                                """,
                        (rs, rowNum) -> rs.getString("id"),
                        java.sql.Timestamp.from(startTime),
                        MAX_REQUEST_ID_CANDIDATES);
            }
            if (endTime != null) {
                return jdbcTemplate.query(
                        """
                                SELECT process_instance_id AS id
                                FROM up_change_history
                                WHERE process_instance_id IS NOT NULL
                                  AND timestamp <= ?
                                GROUP BY process_instance_id
                                ORDER BY MAX(timestamp) DESC
                                LIMIT ?
                                """,
                        (rs, rowNum) -> rs.getString("id"),
                        java.sql.Timestamp.from(endTime),
                        MAX_REQUEST_ID_CANDIDATES);
            }
            // No audit window: newest process instances only (admin audit default is 7 days).
            return jdbcTemplate.query(
                    """
                            SELECT id FROM up_process_instance
                            WHERE function_unit_code IS NOT NULL
                            ORDER BY started_at DESC NULLS LAST
                            LIMIT ?
                            """,
                    (rs, rowNum) -> rs.getString("id"),
                    MAX_REQUEST_ID_CANDIDATES);
        } catch (RuntimeException e) {
            log.warn("Audit Request ID candidate load failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> resolveVariables(ProcessInstance pi) {
        if (pi.getVariables() != null && !pi.getVariables().isEmpty()) {
            return pi.getVariables();
        }
        String json = pi.getVariablesJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() {});
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (Exception e) {
            log.debug("Could not parse variables_json for {}: {}", pi.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Escape {@code %}, {@code _} and {@code \} for SQL LIKE ... ESCAPE '\\'. */
    static String escapeLike(String literal) {
        if (literal == null || literal.isEmpty()) {
            return "";
        }
        return literal
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
