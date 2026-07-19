package com.workflow.component;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 「当前步骤」名的 MI 感知解析：
 * <ul>
 *   <li>普通节点 → 返回任务名本身；</li>
 *   <li>多实例子任务（userTask 处于带 multiInstanceLoopCharacteristics 的 subProcess 内）→ 返回外层
 *       多实例 subProcess 的 name（如 "multi"）；</li>
 *   <li>多实例 subProcess 无 name 时回退其 id；无 BpmnModel / 无 element 时回退任务名。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskInfoAssembler.resolveCurrentStepName (MI-aware current step)")
class TaskInfoAssemblerCurrentStepTest {

    private static final String PD_ID = "proc:1:def";

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private TaskInfoAssembler assembler;

    private BpmnModel model;

    @BeforeEach
    void setUp() {
        model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");

        // 普通主流程节点
        UserTask plain = new UserTask();
        plain.setId("assignmentTask");
        plain.setName("assignment");
        process.addFlowElement(plain);

        // 多实例 subProcess，name="multi"，内含内层 userTask "sub form1"
        SubProcess mi = new SubProcess();
        mi.setId("MultiInstance_1");
        mi.setName("multi");
        mi.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        UserTask inner = new UserTask();
        inner.setId("subForm1Task");
        inner.setName("sub form1");
        mi.addFlowElement(inner); // Flowable 在此设置 inner.getSubProcess() = mi

        // 无 name 的多实例 subProcess，内含内层 userTask
        SubProcess miNoName = new SubProcess();
        miNoName.setId("MultiInstance_NoName");
        miNoName.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        UserTask innerNoName = new UserTask();
        innerNoName.setId("subForm2Task");
        innerNoName.setName("sub form2");
        miNoName.addFlowElement(innerNoName);

        process.addFlowElement(mi);
        process.addFlowElement(miNoName);
        model.addProcess(process);

        lenient().when(repositoryService.getBpmnModel(PD_ID)).thenReturn(model);
    }

    @Test
    @DisplayName("Plain (non-MI) node returns its own task name")
    void plainNodeReturnsTaskName() {
        assertThat(assembler.resolveCurrentStepName(PD_ID, "assignmentTask", "assignment"))
                .isEqualTo("assignment");
    }

    @Test
    @DisplayName("MI inner task returns the outer multi-instance subProcess name (\"multi\"), not the inner task name")
    void miInnerTaskReturnsSubProcessName() {
        assertThat(assembler.resolveCurrentStepName(PD_ID, "subForm1Task", "sub form1"))
                .isEqualTo("multi");
    }

    @Test
    @DisplayName("MI subProcess without a name falls back to the subProcess id")
    void miWithoutNameFallsBackToSubProcessId() {
        assertThat(assembler.resolveCurrentStepName(PD_ID, "subForm2Task", "sub form2"))
                .isEqualTo("MultiInstance_NoName");
    }

    @Test
    @DisplayName("Unknown element id falls back to the given task name")
    void unknownElementFallsBackToTaskName() {
        assertThat(assembler.resolveCurrentStepName(PD_ID, "no-such-element", "some task"))
                .isEqualTo("some task");
    }

    @Test
    @DisplayName("Blank process definition id falls back to the task name without touching the repository")
    void blankProcessDefinitionFallsBackToTaskName() {
        assertThat(assembler.resolveCurrentStepName("", "assignmentTask", "assignment"))
                .isEqualTo("assignment");
    }

    @Test
    @DisplayName("Result is cached: a second lookup does not re-fetch the BpmnModel")
    void resultIsCached() {
        assembler.resolveCurrentStepName(PD_ID, "subForm1Task", "sub form1");
        assembler.resolveCurrentStepName(PD_ID, "subForm1Task", "sub form1");
        // getBpmnModel 只应被调用一次（第二次命中缓存）。
        org.mockito.Mockito.verify(repositoryService, org.mockito.Mockito.times(1)).getBpmnModel(PD_ID);
    }
}
