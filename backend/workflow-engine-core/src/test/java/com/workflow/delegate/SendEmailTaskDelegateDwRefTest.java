package com.workflow.delegate;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SendEmailTaskDelegateDwRefTest {

    @Test
    void resolveDwFunctionUnitRef_prefersFunctionUnitCode() throws Exception {
        assertEquals("fu-20260505-thwmut", invoke(Map.of(
                "functionUnitId", "26f58e33-9d20-4aa4-8ee3-db32de999b15",
                "functionUnitCode", "fu-20260505-thwmut")));
    }

    @Test
    void resolveDwFunctionUnitRef_acceptsNumericDwId() throws Exception {
        assertEquals("48", invoke(Map.of("functionUnitId", "48")));
    }

    @Test
    void resolveDwFunctionUnitRef_rejectsAdminUuidWithoutCode() throws Exception {
        assertNull(invoke(Map.of("functionUnitId", "26f58e33-9d20-4aa4-8ee3-db32de999b15")));
    }

    @SuppressWarnings("unchecked")
    private static String invoke(Map<String, Object> variables) throws Exception {
        Method method = SendEmailTaskDelegate.class.getDeclaredMethod("resolveDwFunctionUnitRef", Map.class);
        method.setAccessible(true);
        return (String) method.invoke(null, variables);
    }
}
