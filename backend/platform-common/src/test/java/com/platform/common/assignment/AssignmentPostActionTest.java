package com.platform.common.assignment;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentPostActionTest {
    @Test void deliveryIdentityIsStableForRetriesAndDifferentForEachOperationAndRule() {
        var operation=UUID.randomUUID();
        assertEquals(AssignmentPostAction.eventId(operation,0),AssignmentPostAction.eventId(operation,0));
        assertNotEquals(AssignmentPostAction.eventId(operation,0),AssignmentPostAction.eventId(operation,1));
        assertNotEquals(AssignmentPostAction.eventId(operation,0),AssignmentPostAction.eventId(UUID.randomUUID(),0));
    }
    @Test void deliveryPayloadKeepsExistingMailFieldsWithoutSendingDesignerConditionState() {
        var value=AssignmentPostAction.payload(Map.of("id",7,"name","Rule","emailTo","${recipient}","condition",Map.of("logic","and")));
        assertEquals(Map.of("ruleId","7","ruleName","Rule","emailTo","${recipient}","enabled",true),value);
    }
}
