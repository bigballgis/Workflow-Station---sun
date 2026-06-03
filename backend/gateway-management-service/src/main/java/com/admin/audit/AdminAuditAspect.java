package com.admin.audit;

import com.admin.repository.gateway.ApiDefinitionRepository;
import com.admin.repository.gateway.ApplicationRepository;
import com.admin.repository.gateway.DriftReportRepository;
import com.admin.repository.gateway.GatewayReleaseRepository;
import com.admin.repository.gateway.ReleaseApprovalRepository;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Gateway audit aspect (GMS — audit disabled by default).
 * GMS does not bundle admin-center's SecurityAuditComponent.
 * Enable via: gateway.audit.enabled=true in GMS config.
 */
@Slf4j
@Aspect
@Component
@ConditionalOnExpression("${gateway.audit.enabled:false}")
public class AdminAuditAspect {

    @SuppressWarnings("unused")
    private final ApiDefinitionRepository apiDefinitionRepository;
    @SuppressWarnings("unused")
    private final ApplicationRepository applicationRepository;
    @SuppressWarnings("unused")
    private final GatewayReleaseRepository gatewayReleaseRepository;
    @SuppressWarnings("unused")
    private final DriftReportRepository driftReportRepository;
    @SuppressWarnings("unused")
    private final ReleaseApprovalRepository releaseApprovalRepository;

    public AdminAuditAspect(ApiDefinitionRepository apiDefinitionRepository,
                            ApplicationRepository applicationRepository,
                            GatewayReleaseRepository gatewayReleaseRepository,
                            DriftReportRepository driftReportRepository,
                            ReleaseApprovalRepository releaseApprovalRepository) {
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.applicationRepository = applicationRepository;
        this.gatewayReleaseRepository = gatewayReleaseRepository;
        this.driftReportRepository = driftReportRepository;
        this.releaseApprovalRepository = releaseApprovalRepository;
    }

    @Around("within(com.admin.controller.gateway.*)")
    public Object passthrough(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
