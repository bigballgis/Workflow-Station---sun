package com.developer.component.impl;

import com.developer.component.DeploymentComponent;
import com.developer.component.ExportImportComponent;
import com.developer.dto.DeployRequest;
import com.developer.dto.DeployResponse;
import com.developer.entity.FunctionUnit;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 部署组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeploymentComponentImpl implements DeploymentComponent {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final ExportImportComponent exportImportComponent;
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String defaultAdminCenterUrl;
    
    // 存储部署状态（生产环境应使用数据库或Redis）
    private final Map<String, DeployResponse> deploymentStatusMap = new ConcurrentHashMap<>();
    private final Map<Long, List<DeployResponse>> deploymentHistoryMap = new ConcurrentHashMap<>();
    
    @Override
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
    public DeployResponse deployToAdminCenter(Long functionUnitId, DeployRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        String deploymentId = UUID.randomUUID().toString();
        String targetUrl = request.getTargetUrl() != null ? request.getTargetUrl() : defaultAdminCenterUrl;
        
        DeployResponse response = DeployResponse.builder()
                .deploymentId(deploymentId)
                .status(DeployResponse.DeployStatus.DEPLOYING)
                .message("部署已开始")
                .progress(0)
                .steps(new ArrayList<>())
                .deployedAt(LocalDateTime.now())
                .build();
        
        deploymentStatusMap.put(deploymentId, response);
        
        // 异步执行部署
        new Thread(() -> executeDeployment(functionUnitId, functionUnit, deploymentId, targetUrl, request)).start();
        
        return response;
    }
    
    private void executeDeployment(Long functionUnitId, FunctionUnit functionUnit, 
                                   String deploymentId, String targetUrl, DeployRequest request) {
        DeployResponse response = deploymentStatusMap.get(deploymentId);
        List<DeployResponse.DeployStep> steps = response.getSteps();
        
        try {
            // Step 1: 导出功能单元
            updateStep(steps, "导出功能单元", "RUNNING", null);
            response.setProgress(10);
            byte[] exportData = exportImportComponent.exportFunctionUnit(functionUnitId);
            updateStep(steps, "导出功能单元", "SUCCESS", "导出成功");
            response.setProgress(30);
            
            // Step 2: 上传到管理员中心
            updateStep(steps, "上传到管理员中心", "RUNNING", null);
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
                throw new BusinessException("DEPLOY_IMPORT_FAILED", "导入失败");
            }
            
            updateStep(steps, "上传到管理员中心", "SUCCESS", "上传成功");
            response.setProgress(60);
            
            // Step 3: 触发部署
            updateStep(steps, "部署功能单元", "RUNNING", null);
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
                throw new BusinessException("DEPLOY_FAILED", "部署失败");
            }
            
            updateStep(steps, "部署功能单元", "SUCCESS", "部署成功");
            response.setProgress(100);
            response.setStatus(DeployResponse.DeployStatus.SUCCESS);
            response.setMessage("部署成功");
            
        } catch (Exception e) {
            log.error("部署失败: {}", e.getMessage(), e);
            response.setStatus(DeployResponse.DeployStatus.FAILED);
            response.setMessage("部署失败: " + e.getMessage());
            
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
