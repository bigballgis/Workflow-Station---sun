package com.platform.common.assignment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentExclusionPolicyCodecTest {
    private static final String DEFAULT = """
            {"schemaVersion":1,"excludeInitiator":false,"excludePreviousHandlers":false,
             "excludeSelectedNodeHandlers":false,"selectedNodeKeys":[],"backup":{"enabled":false}}
            """;

    @Test void defaultPolicyIsAnIdentityConstraint() {
        var policy = AssignmentExclusionPolicyCodec.decode(DEFAULT);
        assertFalse(policy.enabled());
        assertFalse(policy.backup().enabled());
    }

    @Test void damagedOrUnknownPoliciesCannotBecomeDisabled() {
        for (String value : new String[] {"", "null", "[]", "{", DEFAULT.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                DEFAULT.replace("\"excludeInitiator\":false", "\"excludeInitiator\":\"false\""),
                DEFAULT.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                DEFAULT.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"runtimeUserId\":\"forged\"")}) {
            assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionPolicyCodec.decode(value));
        }
    }

    @Test void selectedNodesRequireExplicitUniqueReferences() {
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionPolicyCodec.decode(
                DEFAULT.replace("\"excludeSelectedNodeHandlers\":false", "\"excludeSelectedNodeHandlers\":true")));
        var selected = DEFAULT.replace("\"excludeSelectedNodeHandlers\":false", "\"excludeSelectedNodeHandlers\":true")
                .replace("\"selectedNodeKeys\":[]", "\"selectedNodeKeys\":[\"review\"]");
        assertEquals(java.util.List.of("review"), AssignmentExclusionPolicyCodec.decode(selected).selectedNodeKeys());
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionPolicyCodec.decode(
                selected.replace("[\"review\"]", "[\"review\",\"review\"]")));
    }

    @Test void backupDoesNotEnableItselfWithoutAnExclusionRule() {
        String users = DEFAULT.replace("{\"enabled\":false}",
                "{\"enabled\":true,\"mode\":\"USERS\",\"userRefs\":[\"approver\"]}");
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionPolicyCodec.decode(users));
        var policy = AssignmentExclusionPolicyCodec.decode(users.replace("\"excludeInitiator\":false", "\"excludeInitiator\":true"));
        assertEquals(java.util.List.of("approver"), policy.backup().userRefs());
    }

    @Test void enabledBackupCannotSilentlyChooseBetweenMixedSources() {
        String value = DEFAULT.replace("\"excludeInitiator\":false", "\"excludeInitiator\":true")
                .replace("{\"enabled\":false}", """
                        {"enabled":true,"mode":"BU_ROLE","buSource":"FIXED_BU","businessUnitCode":"FIN",
                         "roleCodes":["REVIEW"],"userRefs":["extra"]}
                        """);
        assertThrows(IllegalArgumentException.class, () -> AssignmentExclusionPolicyCodec.decode(value));
    }
}
