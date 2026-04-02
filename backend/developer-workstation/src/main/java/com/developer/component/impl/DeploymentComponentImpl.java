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
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

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
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String defaultAdminCenterUrl;
    
    // 存储部署状态（生产环境应使用数据库或Redis）
    private final Map<String, DeployResponse> deploymentStatusMap = new ConcurrentHashMap<>();
    private final Map<Long, List<DeployResponse>> deploymentHistoryMap = new ConcurrentHashMap<>();
    
    public DeploymentComponentImpl(
            FunctionUnitRepository functionUnitRepository,
            ExportImportComponent exportImportComponent,
            RestTemplate restTemplate,
            FunctionUnitComponent functionUnitComponent,
            ProcessDesignComponent processDesignComponent,
            I18nService i18nService,
            @org.springframework.beans.factory.annotation.Qualifier("deploymentTaskExecutor") 
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TaskExecutor taskExecutor) {
        this.functionUnitRepository = functionUnitRepository;
        this.exportImportComponent = exportImportComponent;
        this.restTemplate = restTemplate;
        this.functionUnitComponent = functionUnitComponent;
        this.processDesignComponent = processDesignComponent;
        this.i18nService = i18nService;
        // Fallback to SimpleAsyncTaskExecutor if no dedicated executor is configured
        this.taskExecutor = taskExecutor != null ? taskExecutor 
                : new org.springframework.core.task.SimpleAsyncTaskExecutor("deploy-");
    }
    
    @Override
    public DeployResponse deployToAdminCenter(Long functionUnitId, DeployRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
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
        
        deploymentStatusMap.put(deploymentId, response);
        
        // 捕获当前线程的 SecurityContext，传递到异步线程
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Locale currentLocale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        
        // 异步执行部署（使用 Spring 管理的 TaskExecutor 替代裸线程）
        taskExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            org.springframework.context.i18n.LocaleContextHolder.setLocale(currentLocale);
            try {
                executeDeployment(functionUnitId, functionUnit, deploymentId, targetUrl, request);
            } finally {
                SecurityContextHolder.clearContext();
                org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
            }
        });
        
        return response;
    }
    
    private void executeDeployment(Long functionUnitId, FunctionUnit functionUnit, 
                                   String deploymentId, String targetUrl, DeployRequest request) {
        DeployResponse response = deploymentStatusMap.get(deploymentId);
        List<DeployResponse.DeployStep> steps = response.getSteps();
        
        try {
            // Step 0: Auto create version
            updateStep(steps, i18nService.getMessage("deploy.step.create_version"), "RUNNING", null);
            response.setProgress(5);
            FunctionUnit updatedUnit = functionUnitComponent.publish(functionUnitId, request.getChangeLog());
            response.setVersionNumber(updatedUnit.getCurrentVersion());
            response.setChangeLog(request.getChangeLog());
            updateStep(steps, i18nService.getMessage("deploy.step.create_version"), "SUCCESS", i18nService.getMessage("deploy.version_created", updatedUnit.getCurrentVersion()));
            response.setProgress(15);
            
            // Step 0.5: Validate multi-instance configuration
            updateStep(steps, "验证多实例配置", "RUNNING", null);
            ProcessDefinition pd = processDesignComponent.getByFunctionUnitId(functionUnitId);
            if (pd != null && pd.getBpmnXml() != null && !pd.getBpmnXml().trim().isEmpty()) {
                ValidationResult miResult = processDesignComponent.validateMultiInstance(
                    pd.getBpmnXml(), functionUnitId);
                if (!miResult.isValid()) {
                    throw new BusinessException("MULTI_INSTANCE_VALIDATION_FAILED", 
                        "多实例配置验证失败: " + miResult.getErrors().toString());
                }
            }
            updateStep(steps, "验证多实例配置", "SUCCESS", "多实例配置验证通过");
            response.setProgress(18);
            
            // Step 1: Export function unit
            updateStep(steps, i18nService.getMessage("deploy.step.export"), "RUNNING", null);
            response.setProgress(20);
            byte[] exportData = exportImportComponent.exportFunctionUnit(functionUnitId);
            updateStep(steps, i18nService.getMessage("deploy.step.export"), "SUCCESS", i18nService.getMessage("deploy.export_success"));
            response.setProgress(30);
            
            // Step 2: Upload to admin center
            updateStep(steps, i18nService.getMessage("deploy.step.upload"), "RUNNING", null);
            // 使用 function-units-import 控制器的 API
            String importUrl = targetUrl + "/api/v1/admin/function-units-import/import";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(exportData) {
                @Override
                public String getFilename() {
                    return functionUnit.getName() + ".zip";
                }
            };
            body.add("file", resource);
            body.add("conflictStrategy", request.getConflictStrategy() != null ? 
                    request.getConflictStrategy() : "OVERWRITE");
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> importResponse = restTemplate.exchange(
                    importUrl, HttpMethod.POST, requestEntity, Map.class);
            
            if (!importResponse.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("DEPLOY_IMPORT_FAILED", i18nService.getMessage("deploy.import_failed"));
            }
            
            updateStep(steps, i18nService.getMessage("deploy.step.upload"), "SUCCESS", i18nService.getMessage("deploy.upload_success"));
            response.setProgress(60);
            
            // Step 3: Trigger deploy
            updateStep(steps, i18nService.getMessage("deploy.step.deploy"), "RUNNING", null);
            Map<String, Object> importResult = importResponse.getBody();
            String importedId = (String) importResult.get("functionUnitId");
            
            // 使用 function-units-import 控制器的部署 API
            String deployUrl = targetUrl + "/api/v1/admin/function-units-import/" + importedId + "/deploy";
            Map<String, Object> deployBody = new HashMap<>();
            deployBody.put("environment", request.getEnvironment() != null ? 
                    request.getEnvironment().name() : "PRODUCTION");
            deployBody.put("autoEnable", request.getAutoEnable() != null ? 
                    request.getAutoEnable() : true);
            
            HttpHeaders deployHeaders = new HttpHeaders();
            deployHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> deployRequest = new HttpEntity<>(deployBody, deployHeaders);
            
            ResponseEntity<Map> deployResponse = restTemplate.exchange(
                    deployUrl, HttpMethod.POST, deployRequest, Map.class);
            
            if (!deployResponse.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("DEPLOY_FAILED", i18nService.getMessage("deploy.failed"));
            }
            
            updateStep(steps, i18nService.getMessage("deploy.step.deploy"), "SUCCESS", i18nService.getMessage("deploy.success"));
            response.setProgress(100);
            response.setStatus(DeployResponse.DeployStatus.SUCCESS);
            response.setMessage(i18nService.getMessage("deploy.success"));
            
        } catch (Exception e) {
            log.error("Deploy failed for functionUnitId={}: {}", functionUnitId, e.getMessage(), e);
            response.setStatus(DeployResponse.DeployStatus.FAILED);
            // 提取最有意义的错误信息
            String errorMsg = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                errorMsg = e.getCause().getMessage();
            }
            response.setMessage(i18nService.getMessage("deploy.failed") + ": " + errorMsg);
            
            // 标记当前步骤失败
            for (DeployResponse.DeployStep step : steps) {
                if ("RUNNING".equals(step.getStatus())) {
                    step.setStatus("FAILED");
                    step.setMessage(e.getMessage());
                    step.setCompletedAt(LocalDateTime.now());
                }
            }
        }
        
        // 保存到历史记录
        deploymentHistoryMap.computeIfAbsent(functionUnitId, k -> new ArrayList<>()).add(response);
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
        DeployResponse response = deploymentStatusMap.get(deploymentId);
        if (response == null) {
            throw new ResourceNotFoundException("Deployment", deploymentId);
        }
        return response;
    }
    
    @Override
    public List<DeployResponse> getDeploymentHistory(Long functionUnitId) {
        return deploymentHistoryMap.getOrDefault(functionUnitId, new ArrayList<>());
    }
}
