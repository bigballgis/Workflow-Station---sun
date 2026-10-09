package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The engine's deploy-time check on call activities.
 *
 * <p>The BPMN here is shaped exactly like what admin-center produces after resolving a
 * version pin — a definition id in {@code calledElement} plus
 * {@code flowable:calledElementType="id"}. Each side was once tested only on its own,
 * and the engine treated that id as a key and rejected every pinned call.
 */
class ProcessDeploymentManagerCalledElementTest {

    private final Set<String> deployedKeys = new HashSet<>();
    private final Set<String> deployedIds = new HashSet<>();
    private ProcessDeploymentManager manager;

    @BeforeEach
    void setUp() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.createProcessDefinitionQuery()).thenAnswer(inv -> {
            ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
            String[] asked = new String[2]; // [key, id]
            when(query.processDefinitionKey(anyString())).thenAnswer(a -> {
                asked[0] = a.getArgument(0);
                return query;
            });
            when(query.processDefinitionId(anyString())).thenAnswer(a -> {
                asked[1] = a.getArgument(0);
                return query;
            });
            when(query.count()).thenAnswer(a -> {
                if (asked[1] != null) return deployedIds.contains(asked[1]) ? 1L : 0L;
                return deployedKeys.contains(asked[0]) ? 1L : 0L;
            });
            return query;
        });
        manager = new ProcessDeploymentManager();
        ReflectionTestUtils.setField(manager, "repositoryService", repositoryService);
    }

    private static String callByKey(String key) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" calledElement="%s" />
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(key);
    }

    /** Exactly what admin-center writes for a call pinned to a published version. */
    private static String callPinnedById(String definitionId) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="fu-caller">
                    <bpmn:callActivity id="Call_1" name="Check" calledElement="%s" flowable:calledElementType="id">
                      <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows" />
                    </bpmn:callActivity>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(definitionId);
    }

    /** The regression: a resolved pin must be checked as an id, not looked up as a key. */
    @Test
    void acceptsACallPinnedToAnExistingDefinitionId() {
        deployedIds.add("fu-callee:2:b8ea173d");

        assertThatCode(() -> manager.validateCalledElementsAreDeployed(
                callPinnedById("fu-callee:2:b8ea173d"), "fu-caller"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsACallPinnedToADefinitionIdThatDoesNotExist() {
        assertThatThrownBy(() -> manager.validateCalledElementsAreDeployed(
                callPinnedById("fu-callee:9:gone"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("pinned to process definition 'fu-callee:9:gone'");
    }

    @Test
    void acceptsAnUnpinnedCallWhoseKeyIsDeployed() {
        deployedKeys.add("fu-callee");

        assertThatCode(() -> manager.validateCalledElementsAreDeployed(callByKey("fu-callee"), "fu-caller"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnpinnedCallWhoseKeyIsNotDeployed() {
        assertThatThrownBy(() -> manager.validateCalledElementsAreDeployed(callByKey("fu-callee"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("not deployed");
    }

    @Test
    void rejectsADirectSelfCall() {
        deployedKeys.add("fu-caller");

        assertThatThrownBy(() -> manager.validateCalledElementsAreDeployed(callByKey("fu-caller"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("calls itself");
    }

    /** A pin to a version of the process being deployed is still a self-call. */
    @Test
    void rejectsASelfCallPinnedById() {
        deployedIds.add("fu-caller:1:abc");

        assertThatThrownBy(() -> manager.validateCalledElementsAreDeployed(
                callPinnedById("fu-caller:1:abc"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("calls itself");
    }

    @Test
    void ignoresDiagramsWithoutCalls() {
        assertThatCode(() -> manager.validateCalledElementsAreDeployed(
                "<bpmn:definitions><bpmn:process id=\"p\"/></bpmn:definitions>", "p"))
                .doesNotThrowAnyException();
    }
}
