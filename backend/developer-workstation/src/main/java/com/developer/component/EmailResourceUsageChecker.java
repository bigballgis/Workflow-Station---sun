package com.developer.component;

import com.developer.entity.ActionDefinition;
import com.developer.entity.EmailConnection;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.EmailMonitorRuleRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.util.EmailResourceUsageScanner;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Blocks delete of mailbox connections and email templates that the current Function Unit still
 * references (Email Monitor, Action post-email, or BPMN Send Task).
 */
@Component
@RequiredArgsConstructor
public class EmailResourceUsageChecker {

    private final ActionDefinitionRepository actionDefinitionRepository;
    private final EmailMonitorRuleRepository emailMonitorRuleRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final I18nService i18nService;

    public void assertConnectionUnused(Long functionUnitId, EmailConnection connection) {
        if (isConnectionUsed(functionUnitId, connection)) {
            throw new DeveloperBusinessException("CONFLICT_CONNECTION_IN_USE",
                    i18nService.getMessage("email.connection.in_use"));
        }
    }

    public void assertTemplateUnused(Long functionUnitId, Long templateId) {
        if (isTemplateUsed(functionUnitId, templateId)) {
            throw new DeveloperBusinessException("CONFLICT_TEMPLATE_IN_USE",
                    i18nService.getMessage("email.template.in_use"));
        }
    }

    boolean isConnectionUsed(Long functionUnitId, EmailConnection connection) {
        String uid = connection.getConnectionUid();
        Long id = connection.getId();
        if (StringUtils.hasText(uid)
                && emailMonitorRuleRepository.existsByFunctionUnitIdAndConnectionUid(functionUnitId, uid)) {
            return true;
        }
        if (anyActionMatches(functionUnitId,
                config -> EmailResourceUsageScanner.configReferencesConnection(config, uid, id))) {
            return true;
        }
        return EmailResourceUsageScanner.bpmnReferencesConnection(loadBpmn(functionUnitId), uid, id);
    }

    boolean isTemplateUsed(Long functionUnitId, Long templateId) {
        if (anyActionMatches(functionUnitId,
                config -> EmailResourceUsageScanner.configReferencesTemplate(config, templateId))) {
            return true;
        }
        return EmailResourceUsageScanner.bpmnReferencesTemplate(loadBpmn(functionUnitId), templateId);
    }

    private boolean anyActionMatches(Long functionUnitId, Predicate<Map<String, Object>> matches) {
        for (ActionDefinition action : actionDefinitionRepository.findByFunctionUnitId(functionUnitId)) {
            if (matches.test(action.getConfigJson())) {
                return true;
            }
        }
        return false;
    }

    private String loadBpmn(Long functionUnitId) {
        return processDefinitionRepository.findByFunctionUnitId(functionUnitId)
                .map(ProcessDefinition::getBpmnXml)
                .orElse(null);
    }
}
