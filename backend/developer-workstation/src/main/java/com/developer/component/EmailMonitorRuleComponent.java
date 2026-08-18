package com.developer.component;

import com.developer.dto.EmailMonitorRuleRequest;
import com.developer.dto.EmailMonitorRuleResponse;
import com.developer.dto.EmailMonitorStartEventBindRequest;

import java.util.List;

public interface EmailMonitorRuleComponent {

    /** Monitor templates only (Email Monitors tab). */
    List<EmailMonitorRuleResponse> listTemplates(Long functionUnitId);

    List<EmailMonitorRuleResponse> listByFunctionUnitId(Long functionUnitId);

    EmailMonitorRuleResponse getById(Long functionUnitId, Long ruleId);

    EmailMonitorRuleResponse getByStartEventId(Long functionUnitId, String startEventId);

    EmailMonitorRuleResponse create(Long functionUnitId, EmailMonitorRuleRequest request);

    EmailMonitorRuleResponse update(Long functionUnitId, Long ruleId, EmailMonitorRuleRequest request);

    void delete(Long functionUnitId, Long ruleId);

    EmailMonitorRuleResponse bindStartEvent(Long functionUnitId, EmailMonitorStartEventBindRequest request);

    void unbindStartEvent(Long functionUnitId, String startEventId);

    /**
     * Enabled monitor templates must have a ready Start Event binding in the deploy ZIP.
     * The mailbox must be enabled, have a password, and be INBOUND or legacy BOTH.
     */
    void assertRuntimeBindingsForDeploy(Long functionUnitId);
}
