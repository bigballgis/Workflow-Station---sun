package com.portal.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchResponsePrivacyTest {
    @Test
    void enhancedWriteContractIsAnExplicitBooleanWithoutExposingTheSnapshot() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(TaskFormData.builder()
                .subTableBatchRuntimeEnabled(true)
                .fieldValues(Map.of("__subTableBatchRuntime__", Map.of("hiddenStage", "private")))
                .build()));
        assertTrue(json.get("subTableBatchRuntimeEnabled").isBoolean());
        assertTrue(json.get("subTableBatchRuntimeEnabled").booleanValue());
        assertFalse(json.get("fieldValues").has("__subTableBatchRuntime__"));
    }
    @Test
    void ordinaryResponsesNeverSerializeTheInstanceWideBatchSnapshot() throws Exception {
        Map<String, Object> values = Map.of("business", "visible", "__subTableBatchRuntime__", Map.of("otherStage", "private"),
                "__subTableBatchDeletedRows__", Map.of("privateStore", "deletedIdentity"));
        ObjectMapper mapper = new ObjectMapper();
        for (Object dto : new Object[] {
                TaskInfo.builder().variables(values).build(), ProcessInfo.builder().variables(values).formData(values).build(),
                ProcessInstanceInfo.builder().variables(values).build(), TaskFormData.builder().fieldValues(values).build(),
                ProcessFormData.builder().fieldValues(values).build(), TaskFormSnapshot.builder().fieldValues(values).build(),
                CalledFunctionUnitInstance.builder().formData(values).build() }) {
            String serialized = mapper.writeValueAsString(dto);
            assertFalse(serialized.contains("__subTableBatchRuntime__"), dto.getClass().getSimpleName());
            assertFalse(serialized.contains("__subTableBatchDeletedRows__"), dto.getClass().getSimpleName());
            assertTrue(serialized.contains("visible"), dto.getClass().getSimpleName());
        }
        assertTrue(values.containsKey("__subTableBatchRuntime__"), "Serialization must not mutate stored variables");
    }
}
