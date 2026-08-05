package com.developer.util;

import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code subTableId} is database-generated, so the model can only name the table. Without this
 * backfill, deploy validation rejects the function unit with {@code MISSING_SUBTABLE_ID}.
 */
class AiBpmnMiSubTableWriterTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:custom="http://custom.bpmn.io/schema">
              <bpmn:process id="p1" isExecutable="true">
                <bpmn:subProcess id="Sub_1">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false" />
                  <bpmn:userTask id="MI_Task_1">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableName" value="order_items" />
                        <custom:property name="assigneeMode" value="user" />
                        <custom:property name="assigneeField" value="assignee_id" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
                <bpmn:userTask id="Task_Plain">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="subTableName" value="order_items" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Test
    @DisplayName("Writes the persisted sub-table id onto the multi-instance task")
    void bindsSubTableIdByName() {
        String result = AiBpmnMiSubTableWriter.bindMiSubTables(BPMN, List.of(subTable("order_items", 42L)));

        assertTrue(result.contains("name=\"subTableId\" value=\"42\""));
        assertEquals(1, countOccurrences(result, "name=\"subTableId\""),
                "only the multi-instance task carries the id");
        assertTrue(result.contains("name=\"assigneeField\" value=\"assignee_id\""),
                "existing properties survive the rewrite");
    }

    @Test
    @DisplayName("Replaces a sub-table id the model invented")
    void overwritesModelSuppliedId() {
        // Anchored on assigneeMode, which only the multi-instance task carries.
        String withStaleId = BPMN.replace(
                "<custom:property name=\"assigneeMode\" value=\"user\" />",
                "<custom:property name=\"assigneeMode\" value=\"user\" />"
                        + "<custom:property name=\"subTableId\" value=\"1\" />");

        String result = AiBpmnMiSubTableWriter.bindMiSubTables(withStaleId, List.of(subTable("order_items", 42L)));

        assertFalse(result.contains("name=\"subTableId\" value=\"1\""));
        assertTrue(result.contains("name=\"subTableId\" value=\"42\""));
    }

    @Test
    @DisplayName("Leaves the BPMN alone when the named table was not generated")
    void keepsXmlWhenTableUnknown() {
        String result = AiBpmnMiSubTableWriter.bindMiSubTables(BPMN, List.of(subTable("shipment_items", 42L)));

        assertEquals(BPMN, result);
    }

    private static TableDefinition subTable(String tableName, Long id) {
        return TableDefinition.builder()
                .id(id)
                .tableName(tableName)
                .tableType(TableType.SUB)
                .build();
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
