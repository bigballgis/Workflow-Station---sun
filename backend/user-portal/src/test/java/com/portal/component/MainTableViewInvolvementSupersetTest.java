package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Row scoping for Main Table Views is pushed into SQL as a substring pre-filter over the JSON
 * text of {@code __subTables__}, and only the survivors are rechecked with
 * {@link MainTableViewInvolvementChecker}. That is sound for exactly one reason, and it is the
 * reason pinned here: the checker accepts a row only when some participant-hint key holds the
 * user id <em>exactly</em>, so the id is necessarily present in the JSON text. The pre-filter
 * therefore yields a superset — it can admit rows the checker later drops, but it can never
 * hide a row the checker would have accepted.
 *
 * <p>A change to the checker that broke this (matching a prefix, a numeric id, a hash) would
 * silently start hiding rows from the users entitled to see them, which is why the invariant is
 * tested rather than trusted.
 */
class MainTableViewInvolvementSupersetTest {

    private final MainTableViewInvolvementChecker checker = new MainTableViewInvolvementChecker(null);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> variables(Object subTables) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("__subTables__", subTables);
        return vars;
    }

    private Map<String, Object> oneRow(Map<String, Object> row) {
        return Map.of("50533", List.of(row));
    }

    /** The SQL pre-filter, evaluated in Java: does the JSON text contain the id at all? */
    private boolean coarseWouldMatch(String userId, Map<String, Object> variables) {
        try {
            String json = objectMapper.writeValueAsString(variables.get("__subTables__"));
            return json.toLowerCase(Locale.ROOT).contains(userId.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalStateException("variables must be serialisable", e);
        }
    }

    private void assertCoarseIsSuperset(String userId, Map<String, Object> variables) {
        assertThat(checker.isMiParticipant(userId, variables))
                .as("checker accepts the row")
                .isTrue();
        assertThat(coarseWouldMatch(userId, variables))
                .as("so the SQL pre-filter must also admit it")
                .isTrue();
    }

    @Test
    void everyShapeTheCheckerAcceptsIsAlsoAdmittedByTheCoarseFilter() {
        String uid = "user-test-44027893";

        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("assignee", uid))));
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("assignee_user_id", uid))));
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("participant_id", uid))));
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("id_idw", uid))));
        // hint keys match as substrings of the key name, and case-insensitively
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("Primary_Assignee_Name", uid))));
        // nested map and nested list of maps
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("detail", Map.of("assignee", uid)))));
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("people", List.of(Map.of("assignee", uid))))));
    }

    @Test
    void aValueThatOnlyContainsTheIdIsNotInvolvement() {
        String uid = "user-1";
        Map<String, Object> vars = variables(oneRow(Map.of("assignee", "user-12345")));

        assertThat(checker.isMiParticipant(uid, vars))
                .as("the checker compares whole values, so a longer id is a different user")
                .isFalse();
        assertThat(coarseWouldMatch(uid, vars))
                .as("the coarse filter does admit it — which is precisely why a recheck is required")
                .isTrue();
    }

    @Test
    void theIdAppearingOutsideAParticipantKeyIsNotInvolvement() {
        String uid = "user-test-44027893";
        Map<String, Object> vars = variables(oneRow(Map.of(
                "dispute_reason", "escalated by " + uid,
                "attachment_path", "/files/" + uid + "/scan.pdf")));

        assertThat(checker.isMiParticipant(uid, vars)).isFalse();
        assertThat(coarseWouldMatch(uid, vars)).isTrue();
    }

    @Test
    void surroundingWhitespaceOnTheStoredValueStillCounts() {
        String uid = "user-dev";
        assertCoarseIsSuperset(uid, variables(oneRow(Map.of("assignee", "  " + uid + "  "))));
    }

    @Test
    void instancesWithoutSubTablesAreNotInvolvement() {
        assertThat(checker.isMiParticipant("user-dev", Map.of())).isFalse();
        assertThat(checker.isMiParticipant("user-dev", null)).isFalse();
        assertThat(checker.isMiParticipant("", variables(oneRow(Map.of("assignee", ""))))).isFalse();
    }
}
