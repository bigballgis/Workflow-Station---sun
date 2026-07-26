package com.workflow.delegate;

import com.platform.common.i18n.I18nService;
import com.workflow.client.AdminCenterClient;
import com.workflow.client.DeveloperWorkstationEmailTemplateClient;
import com.workflow.service.EmailSenderService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendEmailTaskDelegateFlowElementTest {

    private static final String NESTED_SEND_TASK_ID = "Activity_00auiyo";

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private DeveloperWorkstationEmailTemplateClient emailTemplateClient;
    @Mock
    private EmailSenderService emailSenderService;
    @Mock
    private I18nService i18nService;

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private SendEmailTaskDelegate sendEmailTaskDelegate;

    @Test
    void bpmnModel_getFlowElement_findsNestedSendTask() {
        BpmnModel model = multiInstanceSubProcessModel();

        assertNull(model.getMainProcess().getFlowElement(NESTED_SEND_TASK_ID),
                "main process lookup must not find nested MI send task");
        assertNotNull(model.getFlowElement(NESTED_SEND_TASK_ID),
                "model-wide lookup must find nested MI send task");
    }

    @Test
    void getFlowElement_resolvesNestedMultiInstanceSendTask() throws Exception {
        BpmnModel model = multiInstanceSubProcessModel();
        when(execution.getProcessDefinitionId()).thenReturn("pd:mi-email");
        when(execution.getCurrentActivityId()).thenReturn(NESTED_SEND_TASK_ID);
        when(repositoryService.getBpmnModel("pd:mi-email")).thenReturn(model);

        FlowElement element = invokeGetFlowElement(execution);

        assertNotNull(element);
        assertEquals(NESTED_SEND_TASK_ID, element.getId());
    }

    private static BpmnModel multiInstanceSubProcessModel() {
        ServiceTask sendTask = new ServiceTask();
        sendTask.setId(NESTED_SEND_TASK_ID);

        SubProcess subProcess = new SubProcess();
        subProcess.setId("Activity_MI");
        subProcess.addFlowElement(sendTask);

        Process process = new Process();
        process.setId("Process_MI");
        process.addFlowElement(subProcess);

        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private FlowElement invokeGetFlowElement(DelegateExecution exec) throws Exception {
        Method method = SendEmailTaskDelegate.class.getDeclaredMethod("getFlowElement", DelegateExecution.class);
        method.setAccessible(true);
        return (FlowElement) method.invoke(sendEmailTaskDelegate, exec);
    }
}
