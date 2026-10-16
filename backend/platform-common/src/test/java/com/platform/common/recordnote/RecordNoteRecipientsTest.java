package com.platform.common.recordnote;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RecordNoteRecipientsTest {
    private RecordNoteTaskTarget task(String mode,String state,String snapshot) {
        return new RecordNoteTaskTarget("task","node","scope","arbitrary_lines",Map.of("opaque_key","key-2"),
                "activation-2",mode,"reviewer_x7",snapshot,state,Map.of());
    }
    private Map<String,Object> variables() {
        return Map.of("__subTables__",Map.of("dw:arbitrary_lines",List.of(
                Map.of("opaque_key","key-1","reviewer_x7","other-user"),
                Map.of("opaque_key","key-2","reviewer_x7",Map.of("id","current-person")))));
    }
    @Test void activePersonUsesConfiguredFieldAndOnlyMatchingRow() {
        assertEquals("current-person",RecordNoteRecipients.resolve(variables(),task("PERSON","ACTIVE","stale-assignee")));
    }
    @Test void completedPersonKeepsThisRoundsSnapshotWhenLiveFieldChanges() {
        assertEquals("final-person",RecordNoteRecipients.resolve(variables(),task("PERSON","COMPLETED","final-person")));
    }
    @Test void claimedTeamUsesClaimerNotPersonnelFieldOrCandidateGroup() {
        assertEquals("claimer",RecordNoteRecipients.resolve(variables(),task("TEAM","ACTIVE","claimer")));
    }
    @Test void unclaimedTeamDoesNotExpandToAnotherPerson() {
        assertNull(RecordNoteRecipients.resolve(variables(),task("TEAM","WAITING_CLAIM",null)));
    }
    @Test void invalidatedRoundsCannotNotify() {
        for(String state:List.of("CANCELLED","SUPERSEDED","UNRESOLVED")) {
            assertNull(RecordNoteRecipients.resolve(variables(),task("PERSON",state,"old-user")));
        }
    }
    @Test void missingRowsDoNotFallBackToTaskAssignee() {
        assertNull(RecordNoteRecipients.resolve(Map.of(),task("PERSON","ACTIVE","old-user")));
    }
    @Test void duplicateMatchingRowsAreUnresolvedRatherThanArbitrarilyChosen() {
        var row=Map.of("opaque_key","key-2","reviewer_x7","person");
        var vars=Map.<String,Object>of("__subTables__",Map.of("dw:arbitrary_lines",List.of(row,row)));
        assertNull(RecordNoteRecipients.resolve(vars,task("PERSON","ACTIVE","old-user")));
    }
    @Test void identityUsesAllCompositeKeyParts() {
        assertFalse(RecordNoteRows.matches(Map.of("part_a",1,"part_b","other"),Map.of("part_a",1,"part_b","expected")));
        assertTrue(RecordNoteRows.matches(Map.of("part_a",1,"part_b","expected"),Map.of("part_a",1,"part_b","expected")));
    }
    @Test void typedUserReferencesMatchEngineTransportWithoutGuessingConflictingIdentities() {
        for(String key:List.of("id","userId","user_id","value")) {
            assertEquals("person",RecordNoteRecipients.userId(Map.of(key,"person")));
        }
        assertEquals("42",RecordNoteRecipients.userId(42.0));
        assertNull(RecordNoteRecipients.userId(Map.of("id","person-a","userId","person-b")));
    }
    @Test void approvalSnapshotsCannotDuplicateCurrentRowsOrSupplyAStalePerson() {
        var current=new java.util.HashMap<>(variables());
        current.put("_snapshot_previous_task",Map.of("__subTables__",Map.of("dw:arbitrary_lines",List.of(
                Map.of("opaque_key","key-2","reviewer_x7","old-person")))));
        assertEquals("current-person",RecordNoteRecipients.resolve(current,task("PERSON","ACTIVE","unused")));
    }
}
