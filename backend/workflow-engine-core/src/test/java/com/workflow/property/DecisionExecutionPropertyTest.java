package com.workflow.property;

import com.platform.common.exception.ResourceNotFoundException;
import com.workflow.component.impl.DecisionExecutionComponentImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.ExecuteDecisionBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DecisionExecutionPropertyTest {

    private DmnDecisionService dmnDecisionService;
    private ExecuteDecisionBuilder executeDecisionBuilder;
    private DecisionExecutionComponentImpl component;

    @BeforeTry
    void setUp() {
        dmnDecisionService = mock(DmnDecisionService.class);
        executeDecisionBuilder = mock(ExecuteDecisionBuilder.class);
        component = new DecisionExecutionComponentImpl(dmnDecisionService);
        when(dmnDecisionService.createExecuteDecisionBuilder()).thenReturn(executeDecisionBuilder);
        when(executeDecisionBuilder.decisionKey(anyString())).thenReturn(executeDecisionBuilder);
        when(executeDecisionBuilder.variables(anyMap())).thenReturn(executeDecisionBuilder);
    }

    @Property(tries = 100)
    @Label("Property 10: Output entry keys match decision table output column names")
    void outputEntryKeysMatchOutputColumnNames(
            @ForAll("validDecisionKeys") String decisionKey,
            @ForAll("inputVariableMaps") Map<String, Object> inputVariables,
            @ForAll("outputColumnNameSets") Set<String> outputColumnNames,
            @ForAll("resultRowCounts") int resultRowCount) {
        List<Map<String, Object>> mockResults = IntStream.range(0, resultRowCount)
                .mapToObj(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String colName : outputColumnNames) row.put(colName, "v" + i);
                    return row;
                }).collect(Collectors.toList());
        when(executeDecisionBuilder.execute()).thenReturn(mockResults);
        List<Map<String, Object>> results = component.evaluate(decisionKey, inputVariables);
        assertThat(results).hasSize(resultRowCount);
        for (int i = 0; i < results.size(); i++) {
            assertThat(results.get(i).keySet())
                    .containsExactlyInAnyOrderElementsOf(outputColumnNames);
        }
    }

    @Property(tries = 100)
    @Label("Property 10: Empty result for no matching rules")
    void emptyResultForNoMatchingRules(
            @ForAll("validDecisionKeys") String decisionKey,
            @ForAll("inputVariableMaps") Map<String, Object> inputVariables) {
        when(executeDecisionBuilder.execute()).thenReturn(Collections.emptyList());
        assertThat(component.evaluate(decisionKey, inputVariables)).isEmpty();
    }

    @Property(tries = 100)
    @Label("Property 10: ResourceNotFoundException for unknown decision key")
    void resourceNotFoundForUnknownDecisionKey(
            @ForAll("validDecisionKeys") String decisionKey,
            @ForAll("inputVariableMaps") Map<String, Object> inputVariables) {
        when(executeDecisionBuilder.execute()).thenAnswer(inv -> { throw new FlowableObjectNotFoundException("nf"); });
        assertThatThrownBy(() -> component.evaluate(decisionKey, inputVariables))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Provide Arbitrary<String> validDecisionKeys() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10).map(s -> s + "_decision");
    }

    @Provide Arbitrary<Map<String, Object>> inputVariableMaps() {
        return Arbitraries.integers().between(0, 5).flatMap(size -> {
            if (size == 0) return Arbitraries.just(Collections.emptyMap());
            Arbitrary<List<String>> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12).list().ofSize(size).uniqueElements();
            Arbitrary<List<Object>> values = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                Arbitraries.integers().between(-100, 100).map(i -> (Object) i)
            ).list().ofSize(size);
            return Combinators.combine(keys, values).as((ks, vs) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(ks.size(), vs.size()); i++) m.put(ks.get(i), vs.get(i));
                return m;
            });
        });
    }

    @Provide Arbitrary<Set<String>> outputColumnNameSets() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15).set().ofMinSize(1).ofMaxSize(5);
    }

    @Provide Arbitrary<Integer> resultRowCounts() {
        return Arbitraries.integers().between(1, 10);
    }
}