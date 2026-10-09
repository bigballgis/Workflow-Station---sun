package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.util.TextEnvironmentExpression;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code ${env:varKey}} against Admin Center TEXT variables at send time.
 */
@Component
@RequiredArgsConstructor
public class TextEnvironmentInterpolator {

    private final AdminCenterClient adminCenterClient;

    public String apply(String template, boolean htmlEscape) {
        if (!TextEnvironmentExpression.contains(template)) {
            return template;
        }
        return TextEnvironmentExpression.apply(template, adminCenterClient::resolveTextEnvironmentValue, htmlEscape);
    }
}
