package com.developer.component.impl;

import com.developer.component.EmailMonitorRuleComponent;
import com.developer.dto.EmailMonitorRuleRequest;
import com.developer.dto.EmailMonitorRuleResponse;
import com.developer.entity.EmailMonitorRule;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.EmailConnectionRepository;
import com.developer.repository.EmailMonitorRuleRepository;
import com.developer.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailMonitorRuleComponentImpl implements EmailMonitorRuleComponent {

    private final EmailMonitorRuleRepository emailMonitorRuleRepository;
    private final EmailConnectionRepository emailConnectionRepository;
    private final FunctionUnitRepository functionUnitRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EmailMonitorRuleResponse> listByFunctionUnitId(Long functionUnitId) {
        ensureFunctionUnitExists(functionUnitId);
        return emailMonitorRuleRepository.findByFunctionUnitIdOrderByNameAsc(functionUnitId).stream()
                .map(EmailMonitorRuleResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmailMonitorRuleResponse getById(Long functionUnitId, Long ruleId) {
        return EmailMonitorRuleResponse.fromEntity(getEntity(functionUnitId, ruleId));
    }

    @Override
    @Transactional(readOnly = true)
    public EmailMonitorRuleResponse getByStartEventId(Long functionUnitId, String startEventId) {
        ensureFunctionUnitExists(functionUnitId);
        if (!StringUtils.hasText(startEventId)) {
            throw new DeveloperBusinessException("VALIDATION_START_EVENT_REQUIRED", "Start event id is required");
        }
        EmailMonitorRule rule = emailMonitorRuleRepository
                .findByFunctionUnitIdAndStartEventId(functionUnitId, startEventId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("EmailMonitorRule", startEventId));
        return EmailMonitorRuleResponse.fromEntity(rule);
    }

    @Override
    @Transactional
    public EmailMonitorRuleResponse create(Long functionUnitId, EmailMonitorRuleRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        if (emailMonitorRuleRepository.existsByFunctionUnitIdAndName(functionUnitId, request.getName())) {
            throw new DeveloperBusinessException("CONFLICT_RULE_NAME", "监听规则名称已存在: " + request.getName());
        }
        validateConnection(functionUnitId, request.getConnectionUid());
        validateStartEventBinding(functionUnitId, request.getStartEventId(), null);

        EmailMonitorRule rule = EmailMonitorRule.builder()
                .ruleUid(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .build();
        apply(rule, request);
        return EmailMonitorRuleResponse.fromEntity(emailMonitorRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public EmailMonitorRuleResponse update(Long functionUnitId, Long ruleId, EmailMonitorRuleRequest request) {
        EmailMonitorRule rule = getEntity(functionUnitId, ruleId);

        if (emailMonitorRuleRepository.existsByFunctionUnitIdAndNameAndIdNot(
                functionUnitId, request.getName(), ruleId)) {
            throw new DeveloperBusinessException("CONFLICT_RULE_NAME", "监听规则名称已存在: " + request.getName());
        }
        validateConnection(functionUnitId, request.getConnectionUid());
        validateStartEventBinding(functionUnitId, request.getStartEventId(), ruleId);

        apply(rule, request);
        return EmailMonitorRuleResponse.fromEntity(emailMonitorRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public void delete(Long functionUnitId, Long ruleId) {
        emailMonitorRuleRepository.delete(getEntity(functionUnitId, ruleId));
    }

    /** Copies request fields onto the entity (shared by create/update). */
    private void apply(EmailMonitorRule rule, EmailMonitorRuleRequest request) {
        rule.setName(request.getName().trim());
        rule.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        rule.setConnectionUid(request.getConnectionUid());
        rule.setProcessDefinitionKey(request.getProcessDefinitionKey());
        rule.setStartEventId(request.getStartEventId());
        rule.setFolderLabel(StringUtils.hasText(request.getFolderLabel()) ? request.getFolderLabel() : "INBOX");
        rule.setFilterFrom(request.getFilterFrom());
        rule.setFilterSubject(request.getFilterSubject());
        if (request.getActionType() != null) {
            rule.setActionType(request.getActionType());
        }
        rule.setTargetFormId(request.getTargetFormId());
        rule.setTargetBindingId(request.getTargetBindingId());
        rule.setSystemInitiatorUserId(request.getSystemInitiatorUserId());
        rule.setExtractionRules(request.getExtractionRules());
        rule.setCorrelation(request.getCorrelation());
        if (request.getPollIntervalSeconds() != null && request.getPollIntervalSeconds() > 0) {
            rule.setPollIntervalSeconds(request.getPollIntervalSeconds());
        }
        if (request.getReviewOnMissing() != null) {
            rule.setReviewOnMissing(request.getReviewOnMissing());
        }
    }

    private void validateStartEventBinding(Long functionUnitId, String startEventId, Long excludeRuleId) {
        if (!StringUtils.hasText(startEventId)) {
            return;
        }
        String trimmed = startEventId.trim();
        boolean taken = excludeRuleId == null
                ? emailMonitorRuleRepository.findByFunctionUnitIdAndStartEventId(functionUnitId, trimmed).isPresent()
                : emailMonitorRuleRepository.existsByFunctionUnitIdAndStartEventIdAndIdNot(
                        functionUnitId, trimmed, excludeRuleId);
        if (taken) {
            throw new DeveloperBusinessException("CONFLICT_START_EVENT",
                    "该开始事件已绑定其他邮件监听规则: " + trimmed);
        }
    }

    private void validateConnection(Long functionUnitId, String connectionUid) {
        if (!StringUtils.hasText(connectionUid)) {
            throw new DeveloperBusinessException("VALIDATION_CONNECTION_REQUIRED", "必须选择入站邮箱连接");
        }
        boolean belongs = emailConnectionRepository.findByConnectionUid(connectionUid)
                .map(conn -> conn.getFunctionUnit().getId().equals(functionUnitId))
                .orElse(false);
        if (!belongs) {
            throw new DeveloperBusinessException("VALIDATION_CONNECTION_INVALID",
                    "连接不存在或不属于该功能单元: " + connectionUid);
        }
    }

    private void ensureFunctionUnitExists(Long functionUnitId) {
        if (!functionUnitRepository.existsById(functionUnitId)) {
            throw new ResourceNotFoundException("FunctionUnit", functionUnitId);
        }
    }

    private EmailMonitorRule getEntity(Long functionUnitId, Long ruleId) {
        EmailMonitorRule rule = emailMonitorRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailMonitorRule", ruleId));
        if (!rule.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new ResourceNotFoundException("EmailMonitorRule", ruleId);
        }
        return rule;
    }
}
