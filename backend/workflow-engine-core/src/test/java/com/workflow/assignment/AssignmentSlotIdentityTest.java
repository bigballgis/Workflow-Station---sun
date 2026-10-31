package com.workflow.assignment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentSlotIdentityTest {
    @Test void compoundKeysDoNotCollapseRowsSharingTheirFirstComponent() {
        var a = AssignmentSlotIdentity.rowKey(Map.of("tenant", "T", "sequence", 1), List.of("tenant", "sequence"));
        var b = AssignmentSlotIdentity.rowKey(Map.of("tenant", "T", "sequence", 2), List.of("tenant", "sequence"));
        assertNotEquals(slot(a), slot(b));
        assertEquals(slot(a), slot(Map.of("sequence", 1, "tenant", "T")));
    }

    @Test void displayChangesDoNotChangeTheCapturedRowIdentity() {
        assertEquals(AssignmentSlotIdentity.rowKey(Map.of("platformRowUuid", "row", "name", "old"), List.of()),
                AssignmentSlotIdentity.rowKey(Map.of("platformRowUuid", "row", "name", "new"), List.of()));
    }

    @Test void absentOrPartialKeysAreRejectedInsteadOfUsingAnOffsetOrContentHash() {
        assertThrows(IllegalStateException.class, () -> AssignmentSlotIdentity.rowKey(Map.of("name", "same"), List.of()));
        assertThrows(IllegalStateException.class, () -> AssignmentSlotIdentity.rowKey(Map.of("tenant", "T"), List.of("tenant", "sequence")));
    }

    @Test void actualPortalCollectionEnvelopeRetainsAllConfiguredKeyComponents() {
        var item = Map.of("rowKey",Map.of("region","HK","number",7),"reviewer","a");
        assertEquals(Map.of("region","HK","number",7),AssignmentSlotIdentity.rowKey(item,List.of("region","number")));
        assertThrows(IllegalStateException.class,()->AssignmentSlotIdentity.rowKey(
                Map.of("rowKey",Map.of("region","HK")),List.of("region","number")));
        assertThrows(IllegalStateException.class,()->AssignmentSlotIdentity.rowKey(
                Map.of("rowKey",Map.of("region","HK","number",7),"region","OTHER"),List.of("region","number")));
    }

    @Test void differentCallPositionsAndAncestorRowsCannotOverwriteEachOther() {
        var a = AssignmentSlotIdentity.position("definition", "review", List.of(Map.of("callNode", "first")), List.of());
        var b = AssignmentSlotIdentity.position("definition", "review", List.of(Map.of("callNode", "second")), List.of());
        assertNotEquals(a.key(), b.key());
        assertNotEquals(slot(Map.of("outer", "a", "inner", "same")), slot(Map.of("outer", "b", "inner", "same")));
    }

    private String slot(Map<String,Object> key) {
        return AssignmentSlotIdentity.position("definition", "review", List.of(), List.of(key)).key();
    }
}
