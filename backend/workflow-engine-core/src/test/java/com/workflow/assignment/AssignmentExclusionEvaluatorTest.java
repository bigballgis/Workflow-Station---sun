package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionPolicyCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentExclusionEvaluatorTest {
    private String json(boolean initiator, boolean previous, boolean selected, String backup) {
        return """
                {"schemaVersion":1,"excludeInitiator":%s,"excludePreviousHandlers":%s,
                 "excludeSelectedNodeHandlers":%s,"selectedNodeKeys":%s,"backup":%s}
                """.formatted(initiator, previous, selected, selected ? "[\"review\"]" : "[]", backup);
    }

    @Test void policiesUseAUnionAndCountBothRepresentedAndActualCompleter() {
        var policy = AssignmentExclusionPolicyCodec.decode(json(true, false, true, "{\"enabled\":false}"));
        var history = List.of(new AssignmentExclusionEvaluator.Responsibility("fu-a", "review", Set.of("b", "c")),
                new AssignmentExclusionEvaluator.Responsibility("fu-b", "review", Set.of("d")));
        assertEquals(Set.of("a", "b", "c"), AssignmentExclusionEvaluator.excluded(policy, "a", "fu-a", history));
    }

    @Test void allHandlersIncludesUnrestrictedAndOtherFunctionUnitNodes() {
        var policy = AssignmentExclusionPolicyCodec.decode(json(false, true, false, "{\"enabled\":false}"));
        var history = List.of(new AssignmentExclusionEvaluator.Responsibility("other-fu", "unrestricted", Set.of("b")));
        assertEquals(Set.of("b"), AssignmentExclusionEvaluator.excluded(policy, "a", "fu-a", history));
    }

    @Test void backupMustNotRescueAnOriginallyEmptyPrimaryPoolOrReintroduceExcludedUsers() {
        var policy = AssignmentExclusionPolicyCodec.decode(json(true, false, false,
                "{\"enabled\":true,\"mode\":\"USERS\",\"userRefs\":[\"b\"]}"));
        var empty = AssignmentExclusionEvaluator.filter(policy, List.of(), List.of("b"), Set.of("a"));
        assertEquals(AssignmentExclusionEvaluator.Source.PRIMARY, empty.source());
        assertTrue(empty.users().isEmpty());
        var backup = AssignmentExclusionEvaluator.filter(policy, List.of("a"), List.of("a", "b"), Set.of("a"));
        assertEquals(AssignmentExclusionEvaluator.Source.BACKUP, backup.source());
        assertEquals(List.of("b"), backup.users());
    }

    @Test void disabledPolicyDoesNotAffectExistingPrimaryAssignment() {
        var policy = AssignmentExclusionPolicyCodec.decode(json(false, false, false, "{\"enabled\":false}"));
        assertEquals(Set.of(), AssignmentExclusionEvaluator.excluded(policy, "a", "fu-a",
                List.of(new AssignmentExclusionEvaluator.Responsibility("fu-a", "review", Set.of("a")))));
        assertEquals(List.of("a", "b"), AssignmentExclusionEvaluator.filter(policy, List.of("a", "b"), List.of(), Set.of()).users());
    }
}
