package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An MI sub-process that comes after a Function Unit call.
 *
 * <p>The call runs in its own process instance, which cannot set this process' variables — so the
 * MI collection has to be injected with the task completed before the call. The look-ahead once
 * stopped at call activities; the request then failed with "Variable '...collection' was not
 * found" the moment the called unit finished.
 */
class MiCollectionAfterCallActivityTest {

    private static final String BPMN = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.bpmn.io/schema">
              <bpmn:process id="fu-purchase">
                <bpmn:userTask id="Raise"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:userTask>
                <bpmn:callActivity id="Call" calledElement="fu-vendor">
                  <bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing>
                </bpmn:callActivity>
                <bpmn:subProcess id="Lines">
                  <bpmn:incoming>f2</bpmn:incoming>
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false"
                      flowable:collection="multiInstance_budget_lines_collection" flowable:elementVariable="currentItem"/>
                  <bpmn:userTask id="Confirm">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeMode" value="user"/>
                        <custom:property name="subTableName" value="budget_lines"/>
                        <custom:property name="subTableId" value="7"/>
                        <custom:property name="assigneeField" value="owner_user_id"/>
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
                <bpmn:sequenceFlow id="f1" sourceRef="Raise" targetRef="Call"/>
                <bpmn:sequenceFlow id="f2" sourceRef="Call" targetRef="Lines"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Test
    @SuppressWarnings("unchecked")
    void injectsTheCollectionOfAnMiSubProcessReachedThroughACall() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getBpmnXml("fu-purchase")).thenReturn(Optional.of(BPMN));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT table_name"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of("budget_lines"));
        when(jdbc.query(contains("SELECT table_name"), any(RowMapper.class), anyLong()))
                .thenReturn(List.of("budget_lines"));
        when(jdbc.query(contains("is_primary_key"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of("id"));
        when(jdbc.query(contains("is_primary_key"), any(RowMapper.class), anyLong()))
                .thenReturn(List.of("id"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", Map.of("dw:budget_lines", List.of(
                Map.of("id", "L1", "owner_user_id", "user-dev"))));

        new MiCollectionVariableBuilder(engine, jdbc)
                .injectMiCollectionFromBpmn("fu-purchase", "Raise", "pi-1", variables);

        assertThat(variables).containsKey("multiInstance_budget_lines_collection");
        assertThat((List<Object>) variables.get("multiInstance_budget_lines_collection")).hasSize(1);
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
