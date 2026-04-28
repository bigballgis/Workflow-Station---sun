package com.developer.component.impl;

import com.developer.component.DeploymentComponent;
import com.developer.component.ExportImportComponent;
import com.developer.component.FunctionUnitComponent;
import com.developer.component.ProcessDesignComponent;
import com.developer.dto.DeployRequest;
import com.developer.dto.DeployResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.service.DeploymentJobService;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 部署组件实现
 */
@Component
@Slf4j
public class DeploymentComponentImpl implements DeploymentComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final ExportImportComponent exportImportComponent;
    private final RestTemplate restTemplate;
    private final FunctionUnitComponent functionUnitComponent;
    private final ProcessDesignComponent processDesignComponent;
    private final I18nService i18nService;
    private final TaskExecutor taskExecutor;
    private final DeploymentJobService deploymentJobService;
    private final FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;

    @Value("${admin-center.url:http://localhost:8090}")
    private String defaultAdminCenterUrl;

    /**
     * 生产环境应由已登录用户携带 JWT；本地/自动化测试可设为 false。
     */
    @Value("${developer.deployment.require-admin-authorization:true}")
    private boolean requireAdminAuthorization;

    public DeploymentComponentImpl(
            FunctionUnitRepository functionUnitRepository,
            ExportImportComponent exportImportComponent,
            RestTemplate restTemplate,
            FunctionUnitComponent functionUnitComponent,
            ProcessDesignComponent processDesignComponent,
            I18nService i18nService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            @Qualifier("deploymentTaskExecutor")
            TaskExecutor taskExecutor,
            DeploymentJobService deploymentJobService,
            FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService) {
        this.functionUnitRepository = functionUnitRepository;
        this.exportImportComponent = exportImportComponent;
        this.restTemplate = restTemplate;
        this.functionUnitComponent = functionUnitComponent;
        this.processDesignComponent = processDesignComponent;
        this.i18nService = i18nService;
        this.taskExecutor = taskExecutor != null ? taskExecutor
                : new org.springframework.core.task.SimpleAsyncTaskExecutor("deploy-");
        this.deploymentJobService = deploymentJobService;
        this.functionUnitWorkspaceAccessService = functionUnitWorkspaceAccessService;
    }

    @Override
    public DeployResponse deployToAdminCenter(Long functionUnitId, DeployRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.MODIFY);

        Optional<String> outboundAuth = resolveOutboundAuthorizationHeader();
        Optional<String> adminUserId = SecurityContextUtils.getCurrentUserId();

        if (requireAdminAuthorization && outboundAuth.isEmpty()) {
            throw new DeveloperBusinessException(
                    "DEPLOY_ADMIN_AUTH_REQUIRED",
                    i18nService.getMessage("deploy.admin_authorization_required"),
                    i18nService.getMessage("deploy.admin_authorization_required_hint"));
        }

        String deploymentId = UUID.randomUUID().toString();
        String targetUrl = request.getTargetUrl() != null ? request.getTargetUrl() : defaultAdminCenterUrl;

        DeployResponse response = DeployResponse.builder()
                .deploymentId(deploymentId)
                .status(DeployResponse.DeployStatus.DEPLOYING)
                .message(i18nService.getMessage("deploy.started"))
                .progress(0)
                .steps(new ArrayList<>())
                .deployedAt(LocalDateTime.now())
                .build();

        deploymentJobService.persistNew(deploymentId, functionUnitId, targetUrl, response);

        SecurityContext securityContext = SecurityContextHolder.getContext();
        Locale currentLocale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        final String authHeader = outboundAuth.orElse(null);

        taskExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            org.springframework.context.i18n.LocaleContextHolder.setLocale(currentLocale);
            try {
                executeDeployment(functionUnitId, functionUnit, targetUrl, request, response, authHeader, adminUserId);
            } finally {
                deploymentJobService.persistUpdate(functionUnitId, targetUrl, response);
                SecurityContextHolder.clearContext();
                org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
            }
        });

        return response;
    }

    private void executeDeployment(Long functionUnitId,
                                   FunctionUnit functionUnit,
                                   String targetUrl,
                                   DeployRequest request,
                                   DeployResponse response,
                                   String authorizationHeader,
                                   Optional<String> adminUserId) {
        List<DeployResponse.DeployStep> steps = response.getSteps();

        try {
            updateStep(steps, i18nService.getMessage("deploy.step.create_version"), "RUNNING", null);
            response.setProgress(5);
            FunctionUnit updatedUnit = functionUnitComponent.publish(functionUnitId, request.getChangeLog());
            response.setVersionNumber(updatedUnit.getCurrentVersion());
            response.setChangeLog(request.getChangeLog());
            updateStep(steps, i18nService.getMessage("deploy.step.create_version"), "SUCCESS",
                    i18nService.getMessage("deploy.version_created", updatedUnit.getCurrentVersion()));
            response.setProgress(15);
            deploymentJobService.persistUpdate(functionUnitId, targetUrl, response);

            String stepMultiInstance = i18nService.getMessage("deploy.step.multi_instance_validate");
            updateStep(steps, stepMultiInstance, "RUNNING", null);
            ProcessDefinition pd = processDesignComponent.getByFunctionUnitId(functionUnitId);
            if (pd != null && pd.getBpmnXml() != null && !pd.getBpmnXml().trim().isEmpty()) {
                ValidationResult miResult = processDesignComponent.validateMultiInstance(
                        pd.getBpmnXml(), functionUnitId);
                if (!miResult.isValid()) {
                    throw new DeveloperBusinessException("MULTI_INSTANCE_VALIDATION_FAILED",
                            "Multi-instance configuration validation failed: " + miResult.getErrors());
                }
            }
            updateStep(steps, stepMultiInstance, "SUCCESS", i18nService.getMessage("deploy.multi_instance_validate_ok"));
            response.setProgress(18);

            String stepLastTaskTopo = i18nService.getMessage("deploy.step.last_task_assignee_topology");
            updateStep(steps, stepLastTaskTopo, "RUNNING", null);
            if (pd != null && pd.getBpmnXml() != null && !pd.getBpmnXml().trim().isEmpty()) {
                ValidationResult lastTaskTopo = processDesignComponent.validateLastTaskAssigneeTopology(pd.getBpmnXml());
                if (!lastTaskTopo.isValid()) {
                    throw new DeveloperBusinessException("LAST_TASK_ANCHOR_TOPOLOGY_FAILED",
                            "Last completed task anchor topology validation failed: " + lastTaskTopo.getErrors());
                }
            }
            updateStep(steps, stepLastTaskTopo, "SUCCESS", i18nService.getMessage("deploy.last_task_assignee_topology_ok"));
            response.setProgress(19);
            deploymentJobService.persistUpdate(functionUnitId, targetUrl, response);

            updateStep(steps, i18nService.getMessage("deploy.step.export"), "RUNNING", null);
            response.setProgress(20);
            byte[] exportData = exportImportComponent.exportFunctionUnit(functionUnitId);
            updateStep(steps, i18nService.getMessage("deploy.step.export"), "SUCCESS", i18nService.getMessage("deploy.export_success"));
            response.setProgress(30);
            deploymentJobService.persistUpdate(functionUnitId, targetUrl, response);

            updateStep(steps, i18nService.getMessage("deploy.step.upload"), "RUNNING", null);
            String importUrl = targetUrl + "/api/v1/admin/function-units-import/import";

            HttpHeaders importHeaders = new HttpHeaders();
            importHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            applyOutboundAdminHeaders(importHeaders, authorizationHeader, adminUserId);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(exportData) {
                @Override
                public String getFilename() {
                    return functionUnit.getName() + ".zip";
                }
            };
            body.add("file", resource);
            body.add("conflictStrategy", request.getConflictStrategy() != null
                    ? request.getConflictStrategy() : "OVERWRITE");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, importHeaders);

            ResponseEntity<Map> importResponse = restTemplate.exchange(
                    importUrl, HttpMethod.POST, requestEntity, Map.class);

            if (!importResponse.getStatusCode().is2xxSuccessful() || importResponse.getBody() == null) {
                throw new DeveloperBusinessException("DEPLOY_IMPORT_FAILED", i18nService.getMessage("deploy.import_failed"));
            }

            Map<String, Object> importResult = importResponse.getBody();
            Object idObj = importResult.get("functionUnitId");
            if (idObj == null) {
                throw new DeveloperBusinessException("DEPLOY_IMPORT_FAILED",
                        i18nService.getMessage("deploy.import_failed") + ": missing functionUnitId");
            }
            String importedId = idObj.toString();

            updateStep(steps, i18nService.getMessage("deploy.step.upload"), "SUCCESS", i18nService.getMessage("deploy.upload_success"));
            response.setProgress(60);
            deploymentJobService.persistUpdate(functionUnitId, targetUrl, response);

            updateStep(steps, i18nService.getMessage("deploy.step.deploy"), "RUNNING", null);
            String deployUrl = targetUrl + "/api/v1/admin/function-units-import/" + importedId + "/deploy";
            Map<String, Object> deployBody = new HashMap<>();
            deployBody.put("environment", request.getEnvironment() != null
                    ? request.getEnvironment().name() : "PRODUCTION");
            deployBody.put("autoEnable", request.getAutoEnable() != null
                    ? request.getAutoEnable() : true);

            HttpHeaders deployHeaders = new HttpHeaders();
            deployHeaders.setContentType(MediaType.APPLICATION_JSON);
            applyOutboundAdminHeaders(deployHeaders, authorizationHeader, adminUserId);
            HttpEntity<Map<String, Object>> deployRequestEntity = new HttpEntity<>(deployBody, deployHeaders);

            ResponseEntity<Map> deployResponse = restTemplate.exchange(
                    deployUrl, HttpMethod.POST, deployRequestEntity, Map.class);

            if (!deployResponse.getStatusCode().is2xxSuccessful()) {
                throw new DeveloperBusinessException("DEPLOY_FAILED", i18nService.getMessage("deploy.failed"));
            }

            updateStep(steps, i18nService.getMessage("deploy.step.deploy"), "SUCCESS", i18nService.getMessage("deploy.success"));
            response.setProgress(100);
            response.setStatus(DeployResponse.DeployStatus.SUCCESS);
            response.setMessage(i18nService.getMessage("deploy.success"));

        } catch (Exception e) {
            log.error("Deploy failed for functionUnitId={}: {}", functionUnitId, e.getMessage(), e);
            response.setStatus(DeployResponse.DeployStatus.FAILED);
            String errorMsg = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                errorMsg = e.getCause().getMessage();
            }
            response.setMessage(i18nService.getMessage("deploy.failed") + ": " + errorMsg);

            for (DeployResponse.DeployStep step : steps) {
                if ("RUNNING".equals(step.getStatus())) {
                    step.setStatus("FAILED");
                    step.setMessage(e.getMessage());
                    step.setCompletedAt(LocalDateTime.now());
                }
            }
        }
    }

    private void applyOutboundAdminHeaders(HttpHeaders headers, String authorizationHeader, Optional<String> adminUserId) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            String t = authorizationHeader.trim();
            String prefix = PlatformConstants.HEADER_BEARER_PREFIX;
            if (t.regionMatches(true, 0, prefix, 0, prefix.length())) {
                headers.set(PlatformConstants.HEADER_AUTHORIZATION, t);
            } else {
                headers.set(PlatformConstants.HEADER_AUTHORIZATION, prefix + t);
            }
        }
        adminUserId.filter(s -> !s.isBlank())
                .ifPresent(uid -> headers.set(PlatformConstants.HEADER_USER_ID, uid));
    }

    /**
     * 从当前 HTTP 请求解析出站 Authorization（异步线程中需在进入异步前由调用方传入，此处仅作兜底尝试）。
     */
    private Optional<String> resolveOutboundAuthorizationHeader() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest httpRequest = servletAttrs.getRequest();
            String auth = httpRequest.getHeader(PlatformConstants.HEADER_AUTHORIZATION);
            if (auth != null && !auth.isBlank()) {
                return Optional.of(auth.trim());
            }
        }
        return Optional.empty();
    }

    private void updateStep(List<DeployResponse.DeployStep> steps, String name, String status, String message) {
        Optional<DeployResponse.DeployStep> existingStep = steps.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst();

        if (existingStep.isPresent()) {
            DeployResponse.DeployStep step = existingStep.get();
            step.setStatus(status);
            step.setMessage(message);
            if (!"RUNNING".equals(status)) {
                step.setCompletedAt(LocalDateTime.now());
            }
        } else {
            steps.add(DeployResponse.DeployStep.builder()
                    .name(name)
                    .status(status)
                    .message(message)
                    .completedAt("RUNNING".equals(status) ? null : LocalDateTime.now())
                    .build());
        }
    }

    @Override
    public DeployResponse getDeploymentStatus(String deploymentId) {
        return deploymentJobService.findResponseById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment", deploymentId));
    }

    @Override
    public List<DeployResponse> getDeploymentHistory(Long functionUnitId) {
        return deploymentJobService.findResponsesByFunctionUnitId(functionUnitId);
    }
}
