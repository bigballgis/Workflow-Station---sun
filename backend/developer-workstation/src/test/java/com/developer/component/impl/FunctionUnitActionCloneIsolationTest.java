package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.enums.ActionType;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.util.ActionConfigIdRewriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionUnitActionCloneIsolationTest {
    @ParameterizedTest
    @ValueSource(strings = {"postEmailRules", "postEmail"})
    void clonedEmailReferencesDoNotModifySource(String format) {
        var rule = new HashMap<String, Object>(Map.of("emailTemplateId", "10", "connectionId", "source-connection"));
        var config = new HashMap<String, Object>();
        config.put(format, format.equals("postEmailRules") ? new ArrayList<>(List.of(rule)) : rule);
        var source = ActionDefinition.builder().id(12L).actionName("Approve").actionType(ActionType.APPROVE)
                .configJson(config).build();
        var mapper = new ObjectMapper();
        var original = mapper.valueToTree(config);
        var repository = mock(ActionDefinitionRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var cloner = mock(FunctionUnitCloner.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(cloner, "actionDefinitionRepository", repository);
        ReflectionTestUtils.setField(cloner, "objectMapper", mapper);
        ActionDefinition cloned = ReflectionTestUtils.invokeMethod(cloner, "cloneAction", source,
                FunctionUnit.builder().id(2L).build());
        ActionConfigIdRewriter.rewritePersistedActions(List.of(cloned), Map.of(), Map.of(10L, 20L),
                Map.of("source-connection", "target-connection"), repository::save);
        com.fasterxml.jackson.databind.JsonNode sourceAfter = mapper.valueToTree(source.getConfigJson());
        assertThat(sourceAfter).isEqualTo(original);
        var actual = mapper.valueToTree(cloned.getConfigJson()).get(format);
        var mapped = actual.isArray() ? actual.get(0) : actual;
        assertThat(mapped.get("emailTemplateId").asText()).isEqualTo("20");
        assertThat(mapped.get("connectionId").asText()).isEqualTo("target-connection");
    }
}
