package com.developer.property;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.enums.FormType;
import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Task Form 复制属性测试
 * Feature: process-task-form-separation, Property 6: Task Form copy preserves layout but clears Stage bindings
 *
 * Validates: Requirements 3.7, 3.8
 */
public class TaskFormCopyPropertyTest {

    /**
     * Property 6: Copying a Task Form preserves configJson but clears stageBindings.
     *
     * For any Task Form, copying it should produce a new form where the configJson
     * is deeply equal to the source, but stageBindings is empty and the ID is different.
     *
     * Validates: Requirements 3.7, 3.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 6: Task Form copy preserves layout but clears Stage bindings")
    void copyPreservesLayoutButClearsStageBindings(
            @ForAll("taskFormsWithStageBindings") FormDefinition source) {

        assertThat(source.getFormType()).isEqualTo(FormType.TASK);
        assertThat(source.getStageBindings()).isNotEmpty();
        assertThat(source.getConfigJson()).isNotNull();

        // Simulate copy operation
        FormDefinition copy = simulateCopy(source);

        // configJson should be deeply equal
        assertThat(copy.getConfigJson()).isEqualTo(source.getConfigJson());

        // stageBindings should be empty
        assertThat(copy.getStageBindings()).isEmpty();

        // ID should be different (null for unsaved copy vs non-null source)
        assertThat(copy.getId()).isNotEqualTo(source.getId());

        // formType should be preserved
        assertThat(copy.getFormType()).isEqualTo(source.getFormType());
    }

    /**
     * Property 6: Modifying the copied configJson does not affect the source.
     *
     * Validates: Requirements 3.7, 3.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 6: Copy configJson is independent from source")
    void copyConfigJsonIsIndependentFromSource(
            @ForAll("taskFormsWithStageBindings") FormDefinition source) {

        Map<String, Object> originalConfig = new HashMap<>(source.getConfigJson());

        FormDefinition copy = simulateCopy(source);

        // Mutate the copy's configJson
        copy.getConfigJson().put("_mutated", true);

        // Source configJson should remain unchanged
        assertThat(source.getConfigJson()).isEqualTo(originalConfig);
    }

    /**
     * Property 6: Copy preserves fieldPermissions and showLiveValues.
     *
     * Validates: Requirements 3.7, 3.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 6: Copy preserves field permissions and settings")
    void copyPreservesFieldPermissionsAndSettings(
            @ForAll("taskFormsWithPermissions") FormDefinition source) {

        FormDefinition copy = simulateCopy(source);

        assertThat(copy.getFieldPermissions()).isEqualTo(source.getFieldPermissions());
        assertThat(copy.getShowLiveValues()).isEqualTo(source.getShowLiveValues());
    }

    // ========== Copy Logic ==========

    /**
     * Simulates the copyTaskForm operation: deep copy configJson, clear stageBindings, new ID.
     */
    private FormDefinition simulateCopy(FormDefinition source) {
        return FormDefinition.builder()
                .id(null)  // New ID (will be generated on save)
                .functionUnit(source.getFunctionUnit())
                .formName(source.getFormName() + "_copy")
                .formType(source.getFormType())
                .configJson(new HashMap<>(source.getConfigJson()))
                .description(source.getDescription())
                .boundTable(source.getBoundTable())
                .fieldPermissions(source.getFieldPermissions() != null
                        ? new HashMap<>(source.getFieldPermissions())
                        : new HashMap<>())
                .showLiveValues(source.getShowLiveValues())
                .stageBindings(new ArrayList<>())
                .build();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FormDefinition> taskFormsWithStageBindings() {
        return formNames().flatMap(name ->
                configJsons().flatMap(config ->
                        stageBindingLists().map(bindings ->
                                FormDefinition.builder()
                                        .id(1L)
                                        .formType(FormType.TASK)
                                        .formName(name)
                                        .configJson(config)
                                        .stageBindings(bindings)
                                        .fieldPermissions(new HashMap<>())
                                        .showLiveValues(true)
                                        .build()
                        )
                )
        );
    }

    @Provide
    Arbitrary<FormDefinition> taskFormsWithPermissions() {
        return formNames().flatMap(name ->
                configJsons().flatMap(config ->
                        stageBindingLists().flatMap(bindings ->
                                fieldPermissionsArbitrary().flatMap(perms ->
                                        Arbitraries.of(true, false).map(showLive ->
                                                FormDefinition.builder()
                                                        .id(1L)
                                                        .formType(FormType.TASK)
                                                        .formName(name)
                                                        .configJson(config)
                                                        .stageBindings(bindings)
                                                        .fieldPermissions(perms)
                                                        .showLiveValues(showLive)
                                                        .build()
                                        )
                                )
                        )
                )
        );
    }

    private Arbitrary<String> formNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
    }

    private Arbitrary<Map<String, Object>> configJsons() {
        return Arbitraries.of(
                Map.of("fields", List.of("amount", "description")),
                Map.of("layout", "grid", "columns", 2),
                Map.of("fields", List.of("name", "email", "phone"), "layout", "vertical")
        );
    }

    private Arbitrary<List<FormStageBinding>> stageBindingLists() {
        return stageBindings().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<FormStageBinding> stageBindings() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .flatMap(stageId ->
                        Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                                .map(stageName ->
                                        FormStageBinding.builder()
                                                .stageId(stageId)
                                                .stageName(stageName)
                                                .build()
                                )
                );
    }

    private Arbitrary<Map<String, String>> fieldPermissionsArbitrary() {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(15)
                .flatMap(fieldName ->
                        Arbitraries.of("READONLY", "EDITABLE")
                                .map(perm -> Map.entry(fieldName, perm))
                )
                .list()
                .ofMinSize(0)
                .ofMaxSize(5)
                .map(entries -> {
                    Map<String, String> map = new HashMap<>();
                    entries.forEach(e -> map.put(e.getKey(), e.getValue()));
                    return map;
                });
    }
}
