package com.portal.component;

import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBindingScopeGuardTest {

    private static final String FU = "fu-test";

    @Test
    void emptyScopesLeaveTheStoreUntouched() {
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Z", "P-B")));
        new SubTableBindingScopeGuard().assertAndApply(List.of(), FU, Map.of(), submitted, Map.of(), frozen);
        assertThat(submitted.get("dw:attachment")).isEqualTo(List.of(row("Z", "P-B")));
    }

    @Test
    void participantBindingCannotClaimAnotherParticipantsRow() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Z", "P-B")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Z")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void participantBindingMayUpdateItsOwnRowAndBumpsVersion() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(row("Y", "P-A"))));
        Map<String, Object> baseline = Map.of("dw:attachment", List.of(row("Y", "P-A")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:attachment");
        assertThat(rows.get(0).get("_wsRowVersion")).isEqualTo(1);
    }

    @Test
    void bumpingAFlatRowAlsoBumpsTheNestedCopyOnItsParent() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> nestedCopy = new HashMap<>();
        nestedCopy.put("id", "Y");
        nestedCopy.put("participant_id", "P-A");
        nestedCopy.put("_wsRowVersion", 1);
        Map<String, Object> otherTableCopy = new HashMap<>();
        otherTableCopy.put("id", "Y");
        otherTableCopy.put("_wsRowVersion", 1);
        Map<String, Object> parent = new HashMap<>();
        parent.put("id", "P-A");
        parent.put("__subTables__", new HashMap<>(Map.of(
                "dw:attachment", new ArrayList<>(List.of(nestedCopy)),
                "dw:note", new ArrayList<>(List.of(otherTableCopy)))));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(versioned("Y", "P-A", 4))));
        submitted.put("dw:party", new ArrayList<>(List.of(parent)));
        Map<String, Object> baseline = Map.of("dw:attachment", List.of(versioned("Y", "P-A", 4)));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen);

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) parent.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copies = (List<Map<String, Object>>) nested.get("dw:attachment");
        assertThat(copies.get(0).get("_wsRowVersion")).isEqualTo(5);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> other = (List<Map<String, Object>>) nested.get("dw:note");
        assertThat(other.get(0).get("_wsRowVersion")).isEqualTo(1);
    }

    @Test
    void emptiedParticipantScopeRemovesOnlyMatchingRows() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(row("X", null), row("Y", "P-A"), row("Z", "P-B"))));
        Map<String, Object> baseline = Map.of("dw:attachment",
                List.of(row("X", null), row("Y", "P-A"), row("Z", "P-B")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(), true);
        scope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 0)));

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:attachment");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("X", "Z");
    }

    @Test
    void staleRowVersionIsConflict() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> submitted = new HashMap<>();
        Map<String, Object> incoming = row("Y", "P-A");
        incoming.put("_wsRowVersion", 1);
        submitted.put("dw:attachment", new ArrayList<>(List.of(incoming)));
        Map<String, Object> persisted = row("Y", "P-A");
        persisted.put("_wsRowVersion", 2);
        Map<String, Object> baseline = Map.of("dw:attachment", List.of(persisted));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("modified by another save")
                .extracting(ex -> ((PortalException) ex).getCode())
                .isEqualTo("409");
    }

    @Test
    void changedRowWithoutAClaimingScopeIsRejected() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Y", "P-A"), Map.of("id", "Z", "participant_id", "P-B", "title", "forged")));
        Map<String, Object> baseline = Map.of("dw:attachment",
                List.of(row("Y", "P-A"), row("Z", "P-B")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("without a binding scope");
    }

    @Test
    void twoBindingsOnTheSameStoreKeepEachOthersClaimedRows() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(
                fileRow("X", "C1", null),
                fileRow("Y", "C1", "P-A"))));
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(
                fileRow("X", "C1", null),
                fileRow("Y", "C1", "P-A")));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false);

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(caseScope, partyScope), FU, currentItem("P-A"), submitted, baseline, frozen);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:p0_dual_file");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("X", "Y");
        assertThat(rows).extracting(r -> r.get("_wsRowVersion")).containsExactly(1, 1);
    }

    @Test
    void processFormSubFilterDoesNotRequireCurrentItem() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
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

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(caseScope, partyScope), FU, Map.of(), submitted, baseline, frozen);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:p0_dual_file");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("X", "Y");
        assertThat(rows).extracting(r -> r.get("_wsRowVersion")).containsExactly(1, 1);
    }

    @Test
    void processFormPartyBindingRejectsWhenParentRowsAreMissing() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("Y", "C1", "P-A"))));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(partyScope), FU, Map.of(), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void partyBindingMayDeleteChildOfParentRemovedInTheSameRequest() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
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

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(partyScope), FU, Map.of("id", "C1"), submitted, baseline, frozen);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) submitted.get("dw:p0_dual_file");
        assertThat(rows).extracting(r -> r.get("id")).containsExactly("Z");
    }

    @Test
    void partyBindingCannotDeleteARowWhoseParentStillExists() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> caseFile = fileRow("X", "C1", null);
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of()));
        submitted.put("dw:p0_dual_party", new ArrayList<>(List.of(Map.of("id", "P-A"))));
        Map<String, Object> baseline = Map.of(
                "dw:p0_dual_file", List.of(caseFile),
                "dw:p0_dual_party", List.of(Map.of("id", "P-A")));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(), false);
        partyScope.setDeletedRows(List.of(Map.of("id", "X", "_wsRowVersion", 0)));

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(partyScope), FU, Map.of("id", "C1"), submitted, baseline, frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void processFormPartyBindingCannotClaimACaseFileRow() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("X", "C1", null))));
        submitted.put("dw:p0_dual_party", List.of(Map.of("id", "P-A")));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(partyScope), FU, Map.of(), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void unreadableCurrentItemStillFailsLoud() {
        Map<String, SubTableWriteDesign.Binding> frozen = participantBinding();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", List.of(row("Y", "P-A")));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, Map.of("_currentItem", Map.of()), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("current row context");
    }

    @Test
    void processFormMainBindingCannotClaimARowForAnotherMainRecord() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("Z", "C2", null))));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "Z")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(caseScope), FU, Map.of("id", "C1"), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }

    @Test
    void processFormMainBindingMayClaimRowsMatchingTheMainRecord() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("X", "C1", null))));
        SubTableBindingScope caseScope = scope("101", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(caseScope), FU, Map.of("id", "C1"), submitted, Map.of(), frozen);
    }

    @Test
    void partyBindingCannotClaimACaseFileRowOnTheSharedStore() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", new ArrayList<>(List.of(fileRow("X", "C1", null))));
        SubTableBindingScope partyScope = scope("202", "dw:p0_dual_file", List.of(Map.of("id", "X")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(partyScope), FU, currentItem("P-A"), submitted, Map.of(), frozen))
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
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("202",
                new SubTableWriteDesign.Binding("202", "dw:p0_dual_file", "party_id", "SUB",
                        "p0_dual_party", List.of("id"), List.of("id"), "structuralFk",
                        List.of("case_id", "party_id"), List.of()));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", List.of(fileRow("Y", "C1", "P-A")));

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false)),
                FU, currentItem("P-A"), submitted, Map.of(), frozen))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("not owned by this binding");
    }

    @Test
    void explicitFillSourceAllowsAnotherFkOnANewRow() {
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("202",
                new SubTableWriteDesign.Binding("202", "dw:p0_dual_file", "party_id", "SUB",
                        "p0_dual_party", List.of("id"), List.of("id"), "structuralFk",
                        List.of("case_id", "party_id"), List.of("case_id")));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:p0_dual_file", List.of(fileRow("Y", "C1", "P-A")));

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false)),
                FU, currentItem("P-A"), submitted, Map.of(), frozen);
    }

    @Test
    void historicalIntersectionMayStillBeEdited() {
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

        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope("202", "dw:p0_dual_file", List.of(Map.of("id", "Y")), false)),
                FU, currentItem("P-A"), submitted,
                Map.of("dw:p0_dual_file", List.of(persisted)), frozen);
    }

    @Test
    void overlappingBindingsValidateOriginalVersionAndIncrementOnce() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(new HashMap<>(row)));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of(row)));
        List<Map<String, Object>> keys = List.of(Map.of("id", "Y"));
        new SubTableBindingScopeGuard().assertAndApply(List.of(
                scope("101", "dw:p0_dual_file", keys, false),
                scope("202", "dw:p0_dual_file", keys, false)), FU,
                Map.of("id", "C1", "_currentItem", Map.of("rowId", "P-A")), submitted, baseline, frozen);
        assertThat((List<?>) submitted.get("dw:p0_dual_file"))
                .singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("_wsRowVersion", 8);
    }

    private static Map<String, Object> versioned(String id, String participantId, int version) {
        Map<String, Object> r = row(id, participantId);
        r.put("_wsRowVersion", version);
        return r;
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
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(row));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of()));
        SubTableBindingScope scope = scope("202", "dw:p0_dual_file", List.of(), false);
        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen))
                .isInstanceOf(PortalException.class).hasMessageContaining("deletion");
    }

    @Test
    void nullRowKeysCannotBypassExplicitDeletionClaim() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(row));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of()));
        SubTableBindingScope scope = scope("202", "dw:p0_dual_file", null, false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen))
                .isInstanceOf(PortalException.class).hasMessageContaining("deletion");
    }

    @Test
    void staleDeletionIsRejectedAndCurrentDeletionSucceeds() {
        Map<String, SubTableWriteDesign.Binding> frozen = caseAndPartyBindings();
        Map<String, Object> row = fileRow("Y", "C1", "P-A");
        row.put("_wsRowVersion", 7);
        Map<String, Object> baseline = Map.of("dw:p0_dual_file", List.of(row));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:p0_dual_file", List.of()));
        SubTableBindingScope scope = scope("202", "dw:p0_dual_file", List.of(), false);
        scope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 6)));
        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen))
                .isInstanceOf(PortalException.class).hasMessageContaining("modified by another save");
        scope.setDeletedRows(List.of(Map.of("id", "Y", "_wsRowVersion", 7)));
        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, baseline, frozen);
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
        Map<String, SubTableWriteDesign.Binding> frozen = Map.of("101",
                new SubTableWriteDesign.Binding("101", "dw:attachment", "case_id", "MAIN",
                        "cases", List.of("id"), List.of("id"), "structuralFk"));
        Map<String, Object> submitted = new HashMap<>(Map.of("dw:attachment",
                List.of(Map.of("id", "Y", "case_id", "C1"))));
        new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope("101", "dw:attachment", List.of(Map.of("id", "Y")), false)),
                FU, Map.of("id", "C1"), submitted, Map.of(), frozen);
    }

    private static Map<String, Object> currentItem(String participantId) {
        return Map.of("_currentItem", Map.of("rowKey", Map.of("id", participantId), "rowId", participantId));
    }

    /** One participant-scoped binding, as the request's release publishes it. */
    private static Map<String, SubTableWriteDesign.Binding> participantBinding() {
        return Map.of("202", new SubTableWriteDesign.Binding("202", "dw:attachment", "participant_id", "SUB",
                null, List.of("id"), List.of(), "structuralFk", List.of("participant_id"), List.of()));
    }

    /** A case-file binding (filter FK to MAIN) and a party binding (filter FK to SUB) on one store. */
    private static Map<String, SubTableWriteDesign.Binding> caseAndPartyBindings() {
        return Map.of(
                "101", new SubTableWriteDesign.Binding("101", "dw:p0_dual_file", "case_id", "MAIN",
                        "p0_dual_case", List.of("id"), List.of("id"), "structuralFk", List.of("case_id"), List.of()),
                "202", new SubTableWriteDesign.Binding("202", "dw:p0_dual_file", "party_id", "SUB",
                        "p0_dual_party", List.of("id"), List.of("id"), "structuralFk", List.of("party_id"), List.of()));
    }

    /** Scoped writes are authorized against the request's release only: without it, a version error. */
    @Test
    void scopesWithoutAPinnedReleaseAreAVersionError() {
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("dw:attachment", new ArrayList<>(List.of(row("Y", "P-A"))));
        SubTableBindingScope scope = scope("202", "dw:attachment", List.of(Map.of("id", "Y")), false);

        assertThatThrownBy(() -> new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope), FU, currentItem("P-A"), submitted, Map.of(), null))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
    }
}
