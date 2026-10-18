package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
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

    /** One deployed process definition, as Flowable's repository would hold it. */
    private record Deployed(String id, String key, int version) {
    }

    private final List<Deployed> deployed = new ArrayList<>();
    private ProcessDeploymentManager manager;

    private void deploy(String id, String key, int version) {
        deployed.add(new Deployed(id, key, version));
    }

    /** Every criterion set on the query must match, as in Flowable's ProcessDefinitionQuery. */
    @BeforeEach
    void setUp() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.createProcessDefinitionQuery()).thenAnswer(inv -> {
            ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
            String[] asked = new String[2]; // [key, id]
            Integer[] askedVersion = new Integer[1];
            when(query.processDefinitionKey(anyString())).thenAnswer(a -> {
                asked[0] = a.getArgument(0);
                return query;
            });
            when(query.processDefinitionId(anyString())).thenAnswer(a -> {
                asked[1] = a.getArgument(0);
                return query;
            });
            when(query.processDefinitionVersion(anyInt())).thenAnswer(a -> {
                askedVersion[0] = a.getArgument(0);
                return query;
            });
            when(query.count()).thenAnswer(a -> deployed.stream()
                    .filter(d -> asked[0] == null || d.key().equals(asked[0]))
                    .filter(d -> asked[1] == null || d.id().equals(asked[1]))
                    .filter(d -> askedVersion[0] == null || d.version() == askedVersion[0])
                    .count());
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
        deploy("fu-callee:2:b8ea173d", "fu-callee", 2);

        assertThatCode(() -> manager.resolveCalledElements(
                callPinnedById("fu-callee:2:b8ea173d"), "fu-caller"))
                .doesNotThrowAnyException();
    }

    /**
     * Flowable's real id is the bare uuid, but the engine reports (and admin-center stores)
     * {@code key:version:uuid}. The pin must deploy, pointing at the real id, or Flowable
     * cannot find the callee at runtime.
     */
    @Test
    void rewritesACompositePinToFlowablesRealId() {
        deploy("b8ea173d", "fu-callee", 2);

        String out = manager.resolveCalledElements(
                callPinnedById("fu-callee:2:b8ea173d"), "fu-caller");

        assertThat(out).contains("calledElement=\"b8ea173d\"")
                .contains("flowable:calledElementType=\"id\"")
                .doesNotContain("fu-callee:2:");
    }

    /** The uuid alone is not enough: it must be that process's definition, not another one's. */
    @Test
    void rejectsACompositePinWhoseUuidBelongsToAnotherProcess() {
        deploy("b8ea173d", "some-other-process", 2);

        assertThatThrownBy(() -> manager.resolveCalledElements(
                callPinnedById("fu-callee:2:b8ea173d"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("pinned to process definition 'fu-callee:2:b8ea173d'");
    }

    /** Nor may it quietly stand for a different version of the right process. */
    @Test
    void rejectsACompositePinWhoseVersionDoesNotMatch() {
        deploy("b8ea173d", "fu-callee", 3);

        assertThatThrownBy(() -> manager.resolveCalledElements(
                callPinnedById("fu-callee:2:b8ea173d"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void rejectsACallPinnedToADefinitionIdThatDoesNotExist() {
        assertThatThrownBy(() -> manager.resolveCalledElements(
                callPinnedById("fu-callee:9:gone"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("pinned to process definition 'fu-callee:9:gone'");
    }

    @Test
    void acceptsAnUnpinnedCallWhoseKeyIsDeployed() {
        deploy("fu-callee:1:u1", "fu-callee", 1);

        assertThatCode(() -> manager.resolveCalledElements(callByKey("fu-callee"), "fu-caller"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnpinnedCallWhoseKeyIsNotDeployed() {
        assertThatThrownBy(() -> manager.resolveCalledElements(callByKey("fu-callee"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("not deployed");
    }

    @Test
    void rejectsADirectSelfCall() {
        deploy("fu-caller:1:u2", "fu-caller", 1);

        assertThatThrownBy(() -> manager.resolveCalledElements(callByKey("fu-caller"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("calls itself");
    }

    /** A pin to a version of the process being deployed is still a self-call. */
    @Test
    void rejectsASelfCallPinnedById() {
        deploy("fu-caller:1:abc", "fu-caller", 1);

        assertThatThrownBy(() -> manager.resolveCalledElements(
                callPinnedById("fu-caller:1:abc"), "fu-caller"))
                .isInstanceOf(WorkflowBusinessException.class)
                .hasMessageContaining("calls itself");
    }

    @Test
    void ignoresDiagramsWithoutCalls() {
        assertThatCode(() -> manager.resolveCalledElements(
                "<bpmn:definitions><bpmn:process id=\"p\"/></bpmn:definitions>", "p"))
                .doesNotThrowAnyException();
    }
}
