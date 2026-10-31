package com.workflow.component.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.i18n.I18nService;
import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.assignment.AssignmentRootStarter;
import com.workflow.component.ProcessDeploymentManager;
import com.workflow.component.ProcessStartComponent;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.exception.WorkflowBusinessException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Start orchestration remains outside the write transaction so protected qualification reads cannot invert locks. */
@Component
public class ProcessStartComponentImpl implements ProcessStartComponent {
    private final RuntimeService runtime;
    private final com.workflow.component.ProcessStartDefinitionResolver definitions;
    private final AssignmentRootStarter protectedStarts;
    private final TransactionTemplate transaction;
    @Autowired private NotificationDispatchHelper notificationDispatchHelper;
    @Autowired private I18nService i18nService;

    public ProcessStartComponentImpl(RepositoryService repository, RuntimeService runtime, ProcessDeploymentManager deployment,
            AssignmentRootStarter protectedStarts, PlatformTransactionManager manager) {
        this.runtime=runtime; this.definitions=new com.workflow.component.ProcessStartDefinitionResolver(repository,deployment); this.protectedStarts=protectedStarts;
        transaction = new TransactionTemplate(manager);
    }

    @Override public ProcessInstanceResult startProcess(StartProcessRequest request) {
        try {
            ProcessDefinition definition = definitions.resolve(request.getProcessDefinitionKey(),request.getExpectedProcessDefinitionId());
            com.workflow.assignment.AssignmentStartPinContext.requireDefinition(definition.getId());
            ProcessInstance instance = protectedStarts.supports(definition.getId()) ? protectedStarts.start(request, definition.getId(),
                    started -> notifyStarted(request, started)) : transaction.execute(status -> {
                        var started = legacy(request, definition);
                        notifyStarted(request, started);
                        return started;
                    });
            return result(request, instance);
        } catch (AssignmentFailure failure) { throw failure; }
        catch (Exception failure) {
            throw new WorkflowBusinessException("PROCESS_START_ERROR", "Failed to start process instance: " + failure.getMessage(), failure);
        }
    }

    private ProcessInstance legacy(StartProcessRequest request, ProcessDefinition definition) {
        String previous = org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId();
        try {
            if (StringUtils.hasText(request.getStartUserId())) org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(request.getStartUserId());
            return request.getExpectedProcessDefinitionId() == null
                    ? runtime.startProcessInstanceByKey(request.getProcessDefinitionKey(), request.getBusinessKey(), request.getVariables())
                    : runtime.startProcessInstanceById(definition.getId(), request.getBusinessKey(), request.getVariables());
        } finally { org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(previous); }
    }

    private ProcessInstanceResult result(StartProcessRequest request, ProcessInstance instance) {
        return ProcessInstanceResult.builder().processInstanceId(instance.getId()).processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey()).businessKey(instance.getBusinessKey()).name(instance.getName())
                .startTime(instance.getStartTime() == null ? null : java.time.LocalDateTime.ofInstant(instance.getStartTime().toInstant(), java.time.ZoneId.systemDefault()))
                .startUserId(request.getStartUserId()).variables(request.getVariables()).success(true).message("Process instance started successfully").build();
    }

    private void notifyStarted(StartProcessRequest request, ProcessInstance instance) {
        if (!StringUtils.hasText(request.getStartUserId())) return;
        notificationDispatchHelper.publishToUserAfterCommit(request.getStartUserId(), "PROCESS",
                i18nService.getMessage("workflow.notification.process_started_title"),
                i18nService.getMessage("workflow.notification.process_started_body", request.getProcessDefinitionKey(), instance.getId()), "/tasks", "workflow-engine");
    }
}
