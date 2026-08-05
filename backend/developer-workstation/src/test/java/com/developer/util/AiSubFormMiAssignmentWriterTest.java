package com.developer.util;

import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI write path cannot emit {@code configJson.subForms} itself: the map is keyed by the
 * database-generated sub-table binding id. These tests pin the post-persist backfill that
 * {@code MiAssignmentFormGuard} checks at deploy time.
 */
class AiSubFormMiAssignmentWriterTest {

    private static final String ASSIGNEE_FIELD = "assignee_id";
    private static final String ROLE_FIELD = "role_id";
    private static final String BU_FIELD = "bu_id";

    @Test
    @DisplayName("Writes the assignment container under the persisted binding id")
    void writesContainerKeyedByBindingId() {
        FormDefinition form = form(bindingWithSubTable(77L));

        List<String> written = write(bpmn("both", ASSIGNEE_FIELD, ROLE_FIELD, BU_FIELD), form);

        assertEquals(1, written.size());
        Map<String, Object> entry = subFormEntry(form, "77");
        assertNotNull(entry, "sub-form entry must be keyed by the persisted binding id");

        Map<String, Object> container = findContainer(rule(entry));
        assertNotNull(container, "the miAssignment component must be present");
        assertEquals("miAssignment", container.get("type"));
        assertEquals(Boolean.TRUE, container.get("_miAdopted"),
                "adoption marker keeps the designer from re-capturing fields the author moves out");

        // Fixed reading order: assignee, BU, role — BU narrows the role list.
        assertEquals(List.of(ASSIGNEE_FIELD, BU_FIELD, ROLE_FIELD), childFields(container));
        assertNotNull(entry.get("options"), "a fresh sub-form needs form-create options");
    }

    @Test
    @DisplayName("Moves the assignment fields into the container instead of duplicating them")
    void movesExistingFieldRulesIntoContainer() {
        FormTableBinding binding = bindingWithSubTable(88L);
        FormDefinition form = form(binding);
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put("88", new LinkedHashMap<>(Map.of("rule", new ArrayList<>(List.of(
                fieldRule("item_name"), fieldRule(ASSIGNEE_FIELD), fieldRule("remark"))))));
        form.getConfigJson().put("subForms", subForms);

        write(bpmn("user", ASSIGNEE_FIELD, null, null), form);

        List<Object> rule = rule(subFormEntry(form, "88"));
        assertEquals(3, rule.size(), "the assignee field moves into the container, it is not copied");
        assertEquals("item_name", ((Map<?, ?>) rule.get(0)).get("field"));
        assertEquals("miAssignment", ((Map<?, ?>) rule.get(1)).get("type"),
                "the container takes the place the first assignment field sat in");
        assertEquals("remark", ((Map<?, ?>) rule.get(2)).get("field"));
        assertEquals(List.of(ASSIGNEE_FIELD), childFields((Map<String, Object>) rule.get(1)));
    }

    @Test
    @DisplayName("Leaves a sub-form that already carries the component untouched")
    void skipsSubFormThatAlreadyHasContainer() {
        FormDefinition form = form(bindingWithSubTable(99L));
        Map<String, Object> authored = new LinkedHashMap<>(Map.of(
                "rule", List.of(Map.of("type", "miAssignment", "children", List.of()))));
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put("99", authored);
        form.getConfigJson().put("subForms", subForms);

        List<String> written = write(bpmn("user", ASSIGNEE_FIELD, null, null), form);

        assertTrue(written.isEmpty());
        assertSame(authored, subFormEntry(form, "99"), "an authored sub-form must not be rewritten");
    }

    @Test
    @DisplayName("Ignores a user task that is not inside a multi-instance sub-process")
    void ignoresNonMultiInstanceTask() {
        FormDefinition form = form(bindingWithSubTable(11L));

        String plainTask = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:userTask id="Task_1">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="subTableName" value="order_items" />
                          <custom:property name="assigneeMode" value="user" />
                          <custom:property name="assigneeField" value="assignee_id" />
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertTrue(write(plainTask, form).isEmpty());
        assertNull(subFormEntry(form, "11"));
    }

    @Test
    @DisplayName("Writes nothing when the contract is incomplete for its mode")
    void skipsIncompleteContract() {
        FormDefinition form = form(bindingWithSubTable(12L));

        // mode=both but no roleField — this is the same completeness test MiAssignmentFormGuard
        // applies before it demands a component, so writing one here would mask the missing config.
        assertTrue(write(bpmn("both", ASSIGNEE_FIELD, null, BU_FIELD), form).isEmpty());
        assertNull(subFormEntry(form, "12"));
    }

    @Test
    @DisplayName("Writes nothing when two nodes disagree about the same sub table")
    void skipsConflictingContracts() {
        FormDefinition form = form(bindingWithSubTable(13L));

        String conflicting = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:subProcess id="Sub_1">
                      <bpmn:multiInstanceLoopCharacteristics isSequential="false" />
                      <bpmn:userTask id="MI_Task_1">
                        <bpmn:extensionElements>
                          <custom:properties>
                            <custom:property name="subTableName" value="order_items" />
                            <custom:property name="assigneeMode" value="user" />
                            <custom:property name="assigneeField" value="assignee_id" />
                          </custom:properties>
                        </bpmn:extensionElements>
                      </bpmn:userTask>
                      <bpmn:userTask id="MI_Task_2">
                        <bpmn:extensionElements>
                          <custom:properties>
                            <custom:property name="subTableName" value="order_items" />
                            <custom:property name="assigneeMode" value="role" />
                            <custom:property name="roleField" value="role_id" />
                          </custom:properties>
                        </bpmn:extensionElements>
                      </bpmn:userTask>
                    </bpmn:subProcess>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        assertTrue(write(conflicting, form).isEmpty());
        assertNull(subFormEntry(form, "13"));
    }

    @Test
    @DisplayName("Skips a binding that has no database id yet")
    void skipsBindingWithoutId() {
        FormTableBinding binding = bindingWithSubTable(14L);
        binding.setId(null);
        FormDefinition form = form(binding);

        assertTrue(write(bpmn("user", ASSIGNEE_FIELD, null, null), form).isEmpty());
        assertTrue(subForms(form).isEmpty());
    }

    @Test
    @DisplayName("Ignores bindings whose table no multi-instance node assigns from")
    void ignoresUnrelatedBinding() {
        FormTableBinding other = bindingWithSubTable(15L);
        other.getTable().setTableName("shipment_items");
        FormDefinition form = form(other);

        assertTrue(write(bpmn("user", ASSIGNEE_FIELD, null, null), form).isEmpty());
        assertNull(subFormEntry(form, "15"));
    }

    // ---------- helpers ----------

    private static List<String> write(String bpmnXml, FormDefinition form) {
        return AiSubFormMiAssignmentWriter.writeAssignmentContainers(
                bpmnXml,
                List.of(form),
                field -> fieldRule(field.getFieldName()),
                LinkedHashMap::new);
    }

    private static String bpmn(String mode, String assigneeField, String roleField, String buField) {
        StringBuilder properties = new StringBuilder()
                .append("<custom:property name=\"subTableName\" value=\"order_items\" />")
                .append("<custom:property name=\"assigneeMode\" value=\"").append(mode).append("\" />");
        if (assigneeField != null) {
            properties.append("<custom:property name=\"assigneeField\" value=\"")
                    .append(assigneeField).append("\" />");
        }
        if (roleField != null) {
            properties.append("<custom:property name=\"roleField\" value=\"")
                    .append(roleField).append("\" />");
        }
        if (buField != null) {
            properties.append("<custom:property name=\"buField\" value=\"")
                    .append(buField).append("\" />");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:subProcess id="Sub_1">
                      <bpmn:multiInstanceLoopCharacteristics isSequential="false" />
                      <bpmn:userTask id="MI_Task_1">
                        <bpmn:extensionElements>
                          <custom:properties>%s</custom:properties>
                        </bpmn:extensionElements>
                      </bpmn:userTask>
                    </bpmn:subProcess>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(properties);
    }

    private static FormDefinition form(FormTableBinding binding) {
        FormDefinition form = FormDefinition.builder()
                .id(1L)
                .formName("order_task_form")
                .formType(FormType.TASK)
                .configJson(new LinkedHashMap<>(Map.of("rule", new ArrayList<>())))
                .build();
        binding.setForm(form);
        form.getTableBindings().add(binding);
        return form;
    }

    private static FormTableBinding bindingWithSubTable(Long bindingId) {
        TableDefinition table = TableDefinition.builder()
                .id(500L)
                .tableName("order_items")
                .tableType(TableType.SUB)
                .fieldDefinitions(new ArrayList<>())
                .build();
        for (String fieldName : List.of("item_name", ASSIGNEE_FIELD, BU_FIELD, ROLE_FIELD)) {
            table.getFieldDefinitions().add(FieldDefinition.builder()
                    .fieldName(fieldName)
                    .dataType(DataType.VARCHAR)
                    .sortOrder(table.getFieldDefinitions().size())
                    .build());
        }
        return FormTableBinding.builder()
                .id(bindingId)
                .table(table)
                .bindingType(BindingType.SUB)
                .build();
    }

    private static Map<String, Object> fieldRule(String fieldName) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("field", fieldName);
        rule.put("type", "input");
        return rule;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> subForms(FormDefinition form) {
        Object subForms = form.getConfigJson().get("subForms");
        return subForms instanceof Map ? (Map<String, Object>) subForms : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> subFormEntry(FormDefinition form, String bindingId) {
        Object entry = subForms(form).get(bindingId);
        return entry instanceof Map ? (Map<String, Object>) entry : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> rule(Map<String, Object> entry) {
        assertInstanceOf(List.class, entry.get("rule"));
        return (List<Object>) entry.get("rule");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findContainer(List<Object> rule) {
        for (Object item : rule) {
            if (item instanceof Map<?, ?> map && "miAssignment".equals(map.get("type"))) {
                return (Map<String, Object>) map;
            }
        }
        return null;
    }

    private static List<String> childFields(Map<String, Object> container) {
        assertInstanceOf(List.class, container.get("children"));
        List<String> fields = new ArrayList<>();
        for (Object child : (List<?>) container.get("children")) {
            assertInstanceOf(Map.class, child);
            fields.add(String.valueOf(((Map<?, ?>) child).get("field")));
        }
        assertFalse(fields.isEmpty());
        return fields;
    }
}
