package com.platform.common.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentVariablePatchTest {
    final ObjectMapper json = new ObjectMapper();

    @Test void disjointCommittedFieldsArePreservedDuringRecovery() throws Exception {
        var patch = AssignmentVariablePatch.between(object("{\"data\":{\"a\":1,\"b\":1}}"), object("{\"data\":{\"a\":2,\"b\":1}}"));
        assertEquals(object("{\"data\":{\"a\":2,\"b\":3}}"), AssignmentVariablePatch.apply(object("{\"data\":{\"a\":1,\"b\":3}}"), patch));
    }

    @Test void sameFieldConflictRetainsBothTheAcceptedEffectAndCurrentData() throws Exception {
        var patch = AssignmentVariablePatch.between(object("{\"a\":1}"), object("{\"a\":2}"));
        var current = object("{\"a\":3}");
        assertThrows(AssignmentFailure.class, () -> AssignmentVariablePatch.apply(current, patch));
        assertEquals(object("{\"a\":3}"), current);
        assertEquals(2, patch.get(0).resultValue().intValue());
    }

    @Test void explicitNullIsDifferentFromAnAbsentField() throws Exception {
        var patch = AssignmentVariablePatch.between(object("{\"a\":null}"), object("{}"));
        assertEquals(object("{}"), AssignmentVariablePatch.apply(object("{\"a\":null}"), patch));
        assertThrows(AssignmentFailure.class, () -> AssignmentVariablePatch.apply(object("{\"a\":1}"), patch));
    }

    @Test void arraysCannotOverwriteIndependentlyModifiedRows() throws Exception {
        var patch = AssignmentVariablePatch.between(object("{\"rows\":[{\"id\":1,\"v\":1}]}"), object("{\"rows\":[{\"id\":1,\"v\":2}]}"));
        assertThrows(AssignmentFailure.class, () -> AssignmentVariablePatch.apply(object("{\"rows\":[{\"id\":1,\"v\":3}]}"), patch));
    }

    @Test void identicalCommittedValueIsSafeAndRequestDigestIgnoresOnlyObjectOrdering() throws Exception {
        var accepted = object("{\"a\":2}");
        assertEquals(accepted, AssignmentVariablePatch.apply(accepted, AssignmentVariablePatch.between(object("{\"a\":1}"), accepted)));
        assertEquals(AssignmentRequestDigest.of(object("{\"a\":1,\"b\":[2,3]}")), AssignmentRequestDigest.of(object("{\"b\":[2,3],\"a\":1}")));
        assertNotEquals(AssignmentRequestDigest.of(object("{\"b\":[2,3]}")), AssignmentRequestDigest.of(object("{\"b\":[3,2]}")));
    }

    private ObjectNode object(String value) throws Exception { return (ObjectNode) json.readTree(value); }
}
