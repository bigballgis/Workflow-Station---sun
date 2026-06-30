package com.developer.component;

import com.developer.dto.EmailMonitorRuleRequest;
import com.developer.dto.EmailMonitorRuleResponse;

import java.util.List;

public interface EmailMonitorRuleComponent {

    List<EmailMonitorRuleResponse> listByFunctionUnitId(Long functionUnitId);

    EmailMonitorRuleResponse getById(Long functionUnitId, Long ruleId);

    EmailMonitorRuleResponse getByStartEventId(Long functionUnitId, String startEventId);

    EmailMonitorRuleResponse create(Long functionUnitId, EmailMonitorRuleRequest request);

    EmailMonitorRuleResponse update(Long functionUnitId, Long ruleId, EmailMonitorRuleRequest request);

    void delete(Long functionUnitId, Long ruleId);
}
