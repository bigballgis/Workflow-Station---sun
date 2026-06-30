package com.admin.component;

import com.admin.entity.EmailMonitorRule;
import com.admin.entity.FunctionUnit;
import com.admin.repository.EmailMonitorRuleRepository;
import com.admin.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailMonitorSyncComponentImpl implements EmailMonitorSyncComponent {

    private final EmailMonitorRuleRepository emailMonitorRuleRepository;
    private final FunctionUnitRepository functionUnitRepository;

    @Override
    @Transactional
    public void syncMonitorRules(String functionUnitId, List<Map<String, Object>> monitorRules) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Function unit not found: " + functionUnitId));

        emailMonitorRuleRepository.deleteByFunctionUnitId(functionUnitId);

        if (monitorRules == null || monitorRules.isEmpty()) {
            log.info("No email monitor rules to sync for function unit {}", functionUnitId);
            return;
        }

        for (Map<String, Object> rule : monitorRules) {
            emailMonitorRuleRepository.save(toEntity(functionUnit, rule));
        }
        log.info("Synced {} email monitor rules for function unit {}", monitorRules.size(), functionUnitId);
    }

    private EmailMonitorRule toEntity(FunctionUnit functionUnit, Map<String, Object> rule) {
        String ruleUid = rule.get("ruleUid") != null
                ? String.valueOf(rule.get("ruleUid")) : UUID.randomUUID().toString();
        return EmailMonitorRule.builder()
                .id(ruleUid)
                .functionUnit(functionUnit)
                .name((String) rule.get("name"))
                .enabled(rule.get("enabled") == null || Boolean.TRUE.equals(rule.get("enabled")))
                .connectionUid((String) rule.get("connectionUid"))
                .processDefinitionKey((String) rule.get("processDefinitionKey"))
                .startEventId((String) rule.get("startEventId"))
                .folderLabel(rule.get("folderLabel") != null ? (String) rule.get("folderLabel") : "INBOX")
                .filterFrom((String) rule.get("filterFrom"))
                .filterSubject((String) rule.get("filterSubject"))
                .actionType(rule.get("actionType") != null ? (String) rule.get("actionType") : "START_PROCESS")
                .targetFormId(rule.get("targetFormId") != null ? String.valueOf(rule.get("targetFormId")) : null)
                .targetBindingId((String) rule.get("targetBindingId"))
                .systemInitiatorUserId((String) rule.get("systemInitiatorUserId"))
                .extractionRules(asMap(rule.get("extractionRules")))
                .correlation(asMap(rule.get("correlation")))
                .pollIntervalSeconds(rule.get("pollIntervalSeconds") != null
                        ? ((Number) rule.get("pollIntervalSeconds")).intValue() : 60)
                .reviewOnMissing(rule.get("reviewOnMissing") == null || Boolean.TRUE.equals(rule.get("reviewOnMissing")))
                .syncedAt(Instant.now())
                .build();
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return null;
    }
}
