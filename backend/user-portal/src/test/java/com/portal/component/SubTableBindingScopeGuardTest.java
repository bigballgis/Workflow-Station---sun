package com.portal.component;

import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubTableBindingScopeGuardTest {

    private static final String FU = "fu-test";

    @Test
    void emptyScopesLeaveTheStoreUntouched() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Z", "P-B")));
        new SubTableBindingScopeGuard(jdbc).assertAndApply(List.of(), FU, Map.of(), submitted, Map.of());
        assertThat(submitted.get("dw:attachment")).isEqualTo(List.of(row("Z", "P-B")));
    }

    @Test
    void participantBindingCannotClaimAnotherParticipantsRow() {
        JdbcTemplate jdbc = stubBinding(202L, "attachment", "participant_id", "SUB", List.of("id"));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Z", "P-B")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Z")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, Map.of()))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void participantBindingMayUpdateItsOwnRowAndBumpsVersion() {
        JdbcTemplate jdbc = stubBinding(202L, "attachment", "participant_id", "SUB", List.of("id"));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(row("Y", "P-A"))));
        Map<String, Object> baseline = Map.of("dw:attachment", List.of(row("Y", "P-A")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:attachment");
        assertThat(rows.get(0).get("_wsRowVersion")).isEqualTo(1);
    }

    @Test
    void emptiedParticipantScopeRemovesOnlyMatchingRows() {
        JdbcTemplate jdbc = stubBinding(202L, "attachment", "participant_id", "SUB", List.of("id"));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(row("X", null), row("Y", "P-A"), row("Z", "P-B"))));
        Map<String, Object> baseline = Map.of("dw:attachment",
                List.of(row("X", null), row("Y", "P-A"), row("Z", "P-B")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(), true);
        scope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 0)));

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:attachment");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("X", "Z");
    }

    @Test
    void staleRowVersionIsConflict() {
        JdbcTemplate jdbc = stubBinding(202L, "attachment", "participant_id", "SUB", List.of("id"));
        Map<String, Object> submitted = new HashMap<>();
        Map<String, Object> incoming = row("Y", "P-A");
        incoming.put("_wsRowVersion", 1);
        submitted.put("dw:attachment", new ArrayList<>(List.of(incoming)));
        Map<String, Object> persisted = row("Y", "P-A");
        persisted.put("_wsRowVersion", 2);
        Map<String, Object> baseline = Map.of("dw:attachment", List.of(persisted));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("modified by another save")
                .extracting(ex -> ((PortalException) ex).getCode())
                .isEqualTo("409");
    }

    @Test
    void changedRowWithoutAClaimingScopeIsRejected() {
        JdbcTemplate jdbc = stubBinding(202L, "attachment", "participant_id", "SUB", List.of("id"));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Y", "P-A"), Map.of("id", "Z", "participant_id", "P-B", "title", "forged")));
        Map<String, Object> baseline = Map.of("dw:attachment",
                List.of(row("Y", "P-A"), row("Z", "P-B")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("without a binding scope");
    }

    @Test
    void twoBindingsOnTheSameStoreKeepEachOthersClaimedRows() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(
                fileRow("X", "C1", null),
                fileRow("Y", "C1", "P-A"))));
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(
                fileRow("X", "C1", null),
                fileRow("Y", "C1", "P-A")));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false);

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(caseScope, partyScope), FU, currentItem("P-A"), submitted, baseline);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:p0_dual_file");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("X", "Y");
        assertThat(rows).extracting(r -> r.get("_wsRowVersion")).containsExactly(1, 1);
    }

    @Test
    void processFormSubFilterDoesNotRequireCurrentItem() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(
                fileRow("X", "C1", null),
                fileRow("Y", "C1", "P-A"))));
        submitted.put("dw:p0_dual_party", List.of(Map.of("id", "P-A")));
        Map<String, Object> baseline = Map.of(
                "dw:p0_dual_file", List.of(fileRow("X", "C1", null), fileRow("Y", "C1", "P-A")),
                "dw:p0_dual_party", List.of(Map.of("id", "P-A")));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false);

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(caseScope, partyScope), FU, Map.of(), submitted, baseline);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:p0_dual_file");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("X", "Y");
        assertThat(rows).extracting(r -> r.get("_wsRowVersion")).containsExactly(1, 1);
    }

    @Test
    void processFormPartyBindingRejectsWhenParentRowsAreMissing() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("Y", "C1", "P-A"))));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(partyScope), FU, Map.of(), submitted, Map.of()))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void partyBindingMayDeleteChildOfParentRemovedInTheSameRequest() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> aliceFile = fileRow("Y", "C1", "P-A");
        Map<String, Object> bobFile = fileRow("Z", "C1", "P-B");
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(bobFile)));
        submitted.put("dw:p0_dual_party", new ArrayList<>(List.of(Map.of("id", "P-B"))));
        Map<String, Object> baseline = Map.of(
                "dw:p0_dual_file", List.of(aliceFile, bobFile),
                "dw:p0_dual_party", List.of(Map.of("id", "P-A"), Map.of("id", "P-B")));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Z")), false);
        partyScope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 0)));

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(partyScope), FU, Map.of("id", "C1"), submitted, baseline);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:p0_dual_file");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("Z");
    }

    @Test
    void partyBindingCannotDeleteARowWhoseParentStillExists() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> caseFile = fileRow("X", "C1", null);
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of()));
        submitted.put("dw:p0_dual_party", new ArrayList<>(List.of(Map.of("id", "P-A"))));
        Map<String, Object> baseline = Map.of(
                "dw:p0_dual_file", List.of(caseFile),
                "dw:p0_dual_party", List.of(Map.of("id", "P-A")));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(), false);
        partyScope.setDeletedRows(List.of(Map.of("id", "X", "_wsRowVersion", 0)));

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(partyScope), FU, Map.of("id", "C1"), submitted, baseline))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void processFormPartyBindingCannotClaimACaseFileRow() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("X", "C1", null))));
        submitted.put("dw:p0_dual_party", List.of(Map.of("id", "P-A")));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(partyScope), FU, Map.of(), submitted, Map.of()))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void unreadableCurrentItemStillFailsLoud() {
        JdbcTemplate jdbc = stubBinding(202L, "attachment", "participant_id", "SUB", List.of("id"));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Y", "P-A")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, Map.of("_currentItem", Map.of()), submitted, Map.of()))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("current row context");
    }

    @Test
    void processFormMainBindingCannotClaimARowForAnotherMainRecord() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("Z", "C2", null))));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "Z")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(caseScope), FU, Map.of("id", "C1"), submitted, Map.of()))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void processFormMainBindingMayClaimRowsMatchingTheMainRecord() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("X", "C1", null))));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(caseScope), FU, Map.of("id", "C1"), submitted, Map.of());
    }

    @Test
    void partyBindingCannotClaimACaseFileRowOnTheSharedStore() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("X", "C1", null))));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(partyScope), FU, currentItem("P-A"), submitted, Map.of()))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    private static SubTableBindingScope scope(String bindingId, String storeKey,
                                              List<Map<String, Object>> rowKeys, boolean emptied) {
        SubTableBindingScope s = new SubTableBindingScope();
        s.setBindingId(bindingId);
        s.setStoreKey(storeKey);
        s.setRowKeys(rowKeys);
        s.setEmptied(emptied);
        return s;
    }

    @Test
    void newRowMayOnlyUseItsFilterFkByDefault() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("202",
                new SubTableWriteDesign.Binding("202", "dw:p0_dual_file", "party_id", "SUB",
                        "p0_dual_party", List.of("id"), List.of("id"), "structuralFk",
                        List.of("case_id", "party_id"), List.of()));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", List.of(fileRow("Y", "C1", "P-A")));

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false)),
                FU, currentItem("P-A"), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not owned by this binding");
    }

    @Test
    void explicitFillSourceAllowsAnotherFkOnANewRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("202",
                new SubTableWriteDesign.Binding("202", "dw:p0_dual_file", "party_id", "SUB",
                        "p0_dual_party", List.of("id"), List.of("id"), "structuralFk",
                        List.of("case_id", "party_id"), List.of("case_id")));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", List.of(fileRow("Y", "C1", "P-A")));

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false)),
                FU, currentItem("P-A"), submitted, Map.of(), frozen);
    }

    @Test
    void historicalIntersectionMayStillBeEdited() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("202",
                new SubTableWriteDesign.Binding("202", "dw:p0_dual_file", "party_id", "SUB",
                        "p0_dual_party", List.of("id"), List.of("id"), "structuralFk",
                        List.of("case_id", "party_id"), List.of()));
        Map<String, Object> persisted = fileRow("Y", "C1", "P-A");
        persisted.put("_wsRowVersion", 0);
        Map<String, Object> submittedRow = new HashMap<>(persisted);
        submittedRow.put("title", "renamed");
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", List.of(submittedRow));

        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false)),
                FU, currentItem("P-A"), submitted,
                Map.of("dw:p0_dual_file", List.of(persisted)), frozen);
    }

    @Test
    void overlappingBindingsValidateOriginalVersionAndIncrementOnce() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(new HashMap<>(row)));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of(row)));
        List<Map<String, Object>> keys = List.of(Map.of("id", "Y"));
        new SubTableBindingScopeGuard(jdbc).assertAndApply(List.of(
                scope("101", "dw:p0_dual_file", keys, false),
                scope("202", "dw:p0_dual_file", keys, false)), FU,
                Map.of("id", "C1", "_currentItem", Map.of("rowId", "P-A")), submitted, baseline);
        assertThat((List<?>) submitted.get("dw:p0_dual_file"))
                .singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("_wsRowVersion", 8);
    }

    private static Map<String, Object> row(String id, String participantId) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        if (participantId != null) {
            r.put("participant_id", participantId);
        }
        return r;
    }

    @Test
    void removingRowsRequiresExplicitVersionedDeletion() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(row));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of()));
        SubTableBindingScope scope = scope("202", "dw:p0_dual_file", List.of(), false);
        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline))
                .isInstanceOf(PortalException.class).hasMessageContaining("deletion");
    }

    @Test
    void nullRowKeysCannotBypassExplicitDeletionClaim() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(row));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of()));
        SubTableBindingScope scope = scope("202", "dw:p0_dual_file", null, false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline))
                .isInstanceOf(PortalException.class).hasMessageContaining("deletion");
    }

    @Test
    void staleDeletionIsRejectedAndCurrentDeletionSucceeds() {
        JdbcTemplate jdbc = stubTwoBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(row));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of()));
        SubTableBindingScope scope = scope("202", "dw:p0_dual_file", List.of(), false);
        scope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 6)));
        assertThatThrownBy(() -> new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline))
                .isInstanceOf(PortalException.class).hasMessageContaining("modified by another save");
        scope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 7)));
        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline);
        assertThat((List<?>) submitted.get("dw:p0_dual_file")).isEmpty();
    }

    private static Map<String, Object> fileRow(String id, String caseId, String partyId) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        if (caseId != null) {
            r.put("case_id", caseId);
        }
        if (partyId != null) {
            r.put("party_id", partyId);
        }
        return r;
    }

    @Test
    void pinnedWriteUsesSnapshotAfterLiveBindingWasRemoved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("101",
                new SubTableWriteDesign.Binding("101", "dw:attachment", "case_id", "MAIN",
                        "cases", List.of("id"), List.of("id"), "structuralFk"));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:attachment",
                List.of(Map.of("id", "Y", "case_id", "C1"))));
        new SubTableBindingScopeGuard(jdbc).assertAndApply(
                List.of(scope("101", "dw:attachment", List.of(Map.of("id", "Y")), false)),
                FU, Map.of("id", "C1"), submitted, Map.of(), frozen);
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    private static Map<String, Object> currentItem(String participantId) {
        return Map.of("_currentItem", Map.of("rowKey", Map.of("id", participantId), "rowId", participantId));
    }

    private JdbcTemplate stubBinding(long bindingId, String tableName, String filterField,
                                     String filterRefType, List<String> pkColumns) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubOneBinding(jdbc, bindingId, tableName, filterField, filterRefType, pkColumns, null, null);
        return jdbc;
    }

    private JdbcTemplate stubTwoBindings() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubOneBinding(jdbc, 101L, "p0_dual_file", "case_id", "MAIN", List.of("id"),
                "p0_dual_case", 11L);
        stubOneBinding(jdbc, 202L, "p0_dual_file", "party_id", "SUB", List.of("id"),
                "p0_dual_party", 22L);
        return jdbc;
    }

    @SuppressWarnings("unchecked")
    private void stubOneBinding(JdbcTemplate jdbc, long bindingId, String tableName, String filterField,
                                String filterRefType, List<String> pkColumns,
                                String filterRefTableName, Long filterRefTableId) {
        when(jdbc.query(contains("dw_form_table_bindings b"), any(RowMapper.class), eq(bindingId), eq(FU)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getLong("id")).thenReturn(bindingId);
                    when(rs.getString("table_name")).thenReturn(tableName);
                    when(rs.getString("filter_field")).thenReturn(filterField);
                    when(rs.getString("filter_ref_type")).thenReturn(filterRefType);
                    when(rs.getString("filter_ref_table_name")).thenReturn(filterRefTableName);
                    when(rs.getLong("filter_ref_table_id"))
                            .thenReturn(filterRefTableId == null ? 0L : filterRefTableId);
                    when(rs.wasNull()).thenReturn(filterRefTableId == null);
                    return List.of(mapper.mapRow(rs, 0));
                });
        when(jdbc.queryForList(contains("is_primary_key"), eq(String.class), eq(bindingId)))
                .thenReturn(pkColumns);
        when(jdbc.queryForList(contains("is_foreign_key"), eq(String.class), eq(bindingId)))
                .thenReturn(List.of(filterField));
        if (filterRefTableId != null) {
            when(jdbc.queryForList(contains("f.table_id ="), eq(String.class), eq(filterRefTableId)))
                    .thenReturn(List.of("id"));
        }
    }
}
