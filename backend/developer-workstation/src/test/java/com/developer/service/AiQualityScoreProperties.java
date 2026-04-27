package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiQualityScore;
import com.developer.service.impl.AiValidationServiceImpl;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AI generation quality score computation.
 *
 * <p>Verifies that {@code computeQualityScore()} produces scores where
 * totalScore equals the sum of four dimensions, each dimension is in [0,25],
 * and totalScore is in [0,100].</p>
 *
 * <p><b>Validates: Requirements 41.1, 41.2, 41.3</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 19: quality score invariant")
class AiQualityScoreProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    /**
     * Property 19a: totalScore must equal the sum of all four dimensions.
     *
     * <p><b>Validates: Requirements 41.1</b></p>
     */
    @Property(tries = 100)
    @Label("Property 19a: totalScore == sum of 4 dimensions")
    void totalScoreEqualsSumOfDimensions(@ForAll("arbitraryGeneratedData") AiGeneratedData data) {
        AiQualityScore score = validationService.computeQualityScore(data);

        int dimensionSum = score.getDimensions().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(score.getTotalScore())
                .as("totalScore should equal sum of dimensions")
                .isEqualTo(dimensionSum);
    }

    /**
     * Property 19b: Each dimension must be in [0, 25].
     *
     * <p><b>Validates: Requirements 41.2</b></p>
     */
    @Property(tries = 100)
    @Label("Property 19b: each dimension in [0, 25]")
    void eachDimensionInRange(@ForAll("arbitraryGeneratedData") AiGeneratedData data) {
        AiQualityScore score = validationService.computeQualityScore(data);

        assertThat(score.getDimensions()).containsKeys("completeness", "consistency", "complexity", "naming");
        for (Map.Entry<String, Integer> entry : score.getDimensions().entrySet()) {
            assertThat(entry.getValue())
                    .as("Dimension '%s' should be in [0, 25]", entry.getKey())
                    .isBetween(0, 25);
        }
    }

    /**
     * Property 19c: totalScore must be in [0, 100].
     *
     * <p><b>Validates: Requirements 41.3</b></p>
     */
    @Property(tries = 100)
    @Label("Property 19c: totalScore in [0, 100]")
    void totalScoreInRange(@ForAll("arbitraryGeneratedData") AiGeneratedData data) {
        AiQualityScore score = validationService.computeQualityScore(data);

        assertThat(score.getTotalScore())
                .as("totalScore should be in [0, 100]")
                .isBetween(0, 100);
    }

    /**
     * Property 19d: suggestions list is never null.
     *
     * <p><b>Validates: Requirements 41.1</b></p>
     */
    @Property(tries = 100)
    @Label("Property 19d: suggestions list is never null")
    void suggestionsNeverNull(@ForAll("arbitraryGeneratedData") AiGeneratedData data) {
        AiQualityScore score = validationService.computeQualityScore(data);

        assertThat(score.getSuggestions()).isNotNull();
    }

    @Provide
    Arbitrary<AiGeneratedData> arbitraryGeneratedData() {
        Arbitrary<List<Map<String, Object>>> tables = arbitraryTableDefinitions();
        Arbitrary<List<Map<String, Object>>> forms = arbitraryFormDefinitions();
        Arbitrary<List<Map<String, Object>>> actions = arbitraryActionDefinitions();
        Arbitrary<List<Map<String, Object>>> decisions = arbitraryDecisionDefinitions();
        Arbitrary<List<Map<String, Object>>> relations = arbitraryTableRelations();
        Arbitrary<Map<String, Object>> process = Arbitraries.of(
                null,
                Map.of("bpmnXml", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>"));

        return Combinators.combine(tables, forms, actions, decisions, relations, process)
                .as((t, f, a, d, r, p) -> AiGeneratedData.builder()
                        .tableDefinitions(t)
                        .formDefinitions(f)
                        .actionDefinitions(a)
                        .decisionDefinitions(d)
                        .tableRelations(r)
                        .processDefinition(p)
                        .build());
    }

    private Arbitrary<List<Map<String, Object>>> arbitraryTableDefinitions() {
        return Arbitraries.integers().between(0, 3).flatMap(count -> {
            if (count == 0) return Arbitraries.just(List.of());
            List<Arbitrary<Map<String, Object>>> tableArbs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int idx = i;
                tableArbs.add(arbitraryTableName().map(name -> {
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("tableName", name);
                    table.put("tableType", "MAIN");
                    List<Map<String, Object>> fields = new ArrayList<>();
                    Map<String, Object> pkField = new LinkedHashMap<>();
                    pkField.put("fieldName", "id");
                    pkField.put("dataType", "BIGINT");
                    pkField.put("isPrimaryKey", true);
                    fields.add(pkField);
                    Map<String, Object> nameField = new LinkedHashMap<>();
                    nameField.put("fieldName", "name_" + idx);
                    nameField.put("dataType", "VARCHAR");
                    nameField.put("length", 100);
                    fields.add(nameField);
                    table.put("fieldDefinitions", fields);
                    return table;
                }));
            }
            return Combinators.combine(tableArbs).as(ArrayList::new);
        });
    }

    private Arbitrary<String> arbitraryTableName() {
        return Arbitraries.of("order_table", "user_info", "product_data", "OrderTable", "UserInfo", "PRODUCT");
    }

    private Arbitrary<List<Map<String, Object>>> arbitraryFormDefinitions() {
        return Arbitraries.integers().between(0, 2).map(count -> {
            List<Map<String, Object>> forms = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> form = new LinkedHashMap<>();
                form.put("formName", "form_" + i);
                form.put("formType", "PROCESS");
                forms.add(form);
            }
            return forms;
        });
    }

    private Arbitrary<List<Map<String, Object>>> arbitraryActionDefinitions() {
        return Arbitraries.integers().between(0, 2).map(count -> {
            List<Map<String, Object>> actions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("actionName", "action_" + i);
                action.put("actionType", "SUBMIT");
                actions.add(action);
            }
            return actions;
        });
    }

    private Arbitrary<List<Map<String, Object>>> arbitraryDecisionDefinitions() {
        return Arbitraries.integers().between(0, 2).map(count -> {
            List<Map<String, Object>> decisions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("decisionKey", "decision_" + i);
                decision.put("decisionName", "Decision " + i);
                decision.put("hitPolicy", "FIRST");
                decisions.add(decision);
            }
            return decisions;
        });
    }

    private Arbitrary<List<Map<String, Object>>> arbitraryTableRelations() {
        return Arbitraries.integers().between(0, 2).map(count -> {
            List<Map<String, Object>> relations = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> relation = new LinkedHashMap<>();
                relation.put("sourceTableName", "order_table");
                relation.put("sourceFieldName", "user_id");
                relation.put("relationType", "ONE_TO_MANY");
                relation.put("targetTableName", "user_info");
                relation.put("targetFieldName", "id");
                relations.add(relation);
            }
            return relations;
        });
    }
}
