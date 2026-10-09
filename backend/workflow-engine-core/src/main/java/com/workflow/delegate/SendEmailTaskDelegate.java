package com.workflow.delegate;

import com.workflow.service.ConfiguredEmailSender;
import com.workflow.service.EmailDeliveryException;
import com.workflow.util.BpmnExtensionUtils;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("sendEmailTaskDelegate")
@RequiredArgsConstructor
public class SendEmailTaskDelegate implements JavaDelegate {

    private final RepositoryService repositoryService;
    private final ConfiguredEmailSender configuredEmailSender;
    private final I18nService i18nService;

    @Override
    public void execute(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        log.info("SendEmailTaskDelegate executing for activity {} in process {}",
                activityId, execution.getProcessInstanceId());

        FlowElement flowElement = getFlowElement(execution);
        if (flowElement == null) {
            throw new BpmnError("EMAIL_SEND_FAILED",
                    i18nService.getMessage("email.send_task.flow_element_unresolved", activityId));
        }

        Map<String, String> config = new HashMap<>();
        for (String key : ConfiguredEmailSender.CONFIG_KEYS) {
            String value = BpmnExtensionUtils.getExtensionProperty(flowElement, key);
            if (value != null) {
                config.put(key, value);
            }
        }
        Map<String, Object> variables = new HashMap<>(execution.getVariables());
        try {
            String resolvedTo = configuredEmailSender.send("activity=" + activityId, config, variables);
            execution.setVariable("emailSendResult", Map.of(
                    "success", true,
                    "activityId", activityId,
                    "to", resolvedTo
            ));
        } catch (EmailDeliveryException e) {
            if (EmailDeliveryException.SEND_FAILED.equals(e.getCode())) {
                execution.setVariable("emailSendResult", Map.of(
                        "success", false,
                        "activityId", activityId,
                        "error", e.getRootCause(),
                        "causeChain", e.getCauseChain()
                ));
            }
            throw new BpmnError(e.getCode(), e.getMessage());
        }
    }

    private FlowElement getFlowElement(DelegateExecution execution) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }
        // Nested Send Tasks (e.g. inside Multi-Instance SubProcess) are not on the main process.
        return bpmnModel.getFlowElement(execution.getCurrentActivityId());
    }
}
