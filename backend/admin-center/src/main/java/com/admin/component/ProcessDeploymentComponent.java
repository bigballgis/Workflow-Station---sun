package com.admin.component;

import com.admin.client.WorkflowEngineClient;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 流程部署组件
 * 负责将功能单元中的流程定义部署到 Flowable 引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessDeploymentComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitContentRepository contentRepository;

    /**
     * 部署功能单元的流程定义到 Flowable 引擎
     * 
     * @param functionUnitId 功能单元ID
     * @return 部署结果
     */
    @Transactional
    public ProcessDeploymentResult deployFunctionUnitProcess(String functionUnitId) {
        log.info("Deploying process for function unit: {}", functionUnitId);
        
        // 获取功能单元
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AdminBusinessException("FUNCTION_UNIT_NOT_FOUND", 
                        "功能单元不存在: " + functionUnitId));
        
        // 获取流程定义内容
        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);
        
        if (processContents.isEmpty()) {
            log.warn("No process definition found for function unit: {}", functionUnitId);
            return ProcessDeploymentResult.noProcess(functionUnitId);
        }
        
        // 检查 workflow-engine 是否可用
        if (!workflowEngineClient.isAvailable()) {
            log.error("Workflow engine is not available");
            return ProcessDeploymentResult.engineUnavailable(functionUnitId);
        }
        
        // 部署每个流程定义
        Map<String, String> deployedProcesses = new HashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        
        for (FunctionUnitContent processContent : processContents) {
            try {
                String bpmnXml = processContent.getContentData();
                String processKey = extractProcessKey(bpmnXml, functionUnit.getCode());
                String processName = functionUnit.getName() + " - " + processContent.getContentName();
                
                // 调用 workflow-engine-core 部署流程
                Optional<WorkflowEngineClient.ProcessDeploymentResult> result = 
                        workflowEngineClient.deployProcess(processKey, bpmnXml, processName);
                
                if (result.isPresent() && result.get().isSuccess()) {
                    deployedProcesses.put(processContent.getContentName(), 
                            result.get().getProcessDefinitionId());
                    
                    // 更新功能单元内容，记录 Flowable 中的流程定义ID
                    processContent.setFlowableProcessDefinitionId(result.get().getProcessDefinitionId());
                    processContent.setFlowableDeploymentId(result.get().getDeploymentId());
                    contentRepository.save(processContent);
                    
                    log.info("Process deployed: {} -> {}", 
                            processContent.getContentName(), result.get().getProcessDefinitionId());
                } else {
                    String errorMsg = result.map(WorkflowEngineClient.ProcessDeploymentResult::getMessage)
                            .orElse("部署失败");
                    errors.add(processContent.getContentName() + ": " + errorMsg);
                    log.error("Failed to deploy process: {}", processContent.getContentName());
                }
                
            } catch (Exception e) {
                errors.add(processContent.getContentName() + ": " + e.getMessage());
                log.error("Error deploying process: {}", processContent.getContentName(), e);
            }
        }
        
        // 更新功能单元的流程部署状态
        if (!deployedProcesses.isEmpty()) {
            functionUnit.setProcessDeployed(true);
            functionUnit.setProcessDeploymentCount(deployedProcesses.size());
            functionUnitRepository.save(functionUnit);
        }
        
        if (errors.isEmpty()) {
            return ProcessDeploymentResult.success(functionUnitId, deployedProcesses);
        } else if (!deployedProcesses.isEmpty()) {
            return ProcessDeploymentResult.partialSuccess(functionUnitId, deployedProcesses, errors);
        } else {
            return ProcessDeploymentResult.failure(functionUnitId, errors);
        }
    }

    /**
     * 从 BPMN XML 中提取流程 Key
     */
    private String extractProcessKey(String bpmnXml, String defaultKey) {
        try {
            // 简单的 XML 解析，提取 process id
            int processStart = bpmnXml.indexOf("<bpmn:process");
            if (processStart == -1) {
                processStart = bpmnXml.indexOf("<process");
            }
            
            if (processStart != -1) {
                int idStart = bpmnXml.indexOf("id=\"", processStart);
                if (idStart != -1) {
                    idStart += 4;
                    int idEnd = bpmnXml.indexOf("\"", idStart);
                    if (idEnd != -1) {
                        return bpmnXml.substring(idStart, idEnd);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract process key from BPMN XML, using default: {}", defaultKey);
        }
        
        return defaultKey;
    }

    /**
     * 取消部署功能单元的流程
     */
    @Transactional
    public boolean undeployFunctionUnitProcess(String functionUnitId, boolean cascade) {
        log.info("Undeploying process for function unit: {}", functionUnitId);
        
        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);
        
        boolean allSuccess = true;
        
        for (FunctionUnitContent processContent : processContents) {
            if (processContent.getFlowableDeploymentId() != null) {
                boolean deleted = workflowEngineClient.deleteProcessDefinition(
                        processContent.getFlowableDeploymentId(), cascade);
                
                if (deleted) {
                    processContent.setFlowableProcessDefinitionId(null);
                    processContent.setFlowableDeploymentId(null);
                    contentRepository.save(processContent);
                } else {
                    allSuccess = false;
                }
            }
        }
        
        if (allSuccess) {
            FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId).orElse(null);
            if (functionUnit != null) {
                functionUnit.setProcessDeployed(false);
                functionUnit.setProcessDeploymentCount(0);
                functionUnitRepository.save(functionUnit);
            }
        }
        
        return allSuccess;
    }

    /**
     * 检查功能单元的流程是否已部署
     */
    public boolean isProcessDeployed(String functionUnitId) {
        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);
        
        return processContents.stream()
                .anyMatch(c -> c.getFlowableProcessDefinitionId() != null);
    }

    /**
     * 获取功能单元的流程部署信息
     */
    public Map<String, Object> getProcessDeploymentInfo(String functionUnitId) {
        Map<String, Object> info = new HashMap<>();
        
        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);
        
        List<Map<String, String>> processes = processContents.stream()
                .filter(c -> c.getFlowableProcessDefinitionId() != null)
                .map(c -> {
                    Map<String, String> processInfo = new HashMap<>();
                    processInfo.put("contentName", c.getContentName());
                    processInfo.put("processDefinitionId", c.getFlowableProcessDefinitionId());
                    processInfo.put("deploymentId", c.getFlowableDeploymentId());
                    return processInfo;
                })
                .toList();
        
        info.put("functionUnitId", functionUnitId);
        info.put("deployed", !processes.isEmpty());
        info.put("processCount", processes.size());
        info.put("processes", processes);
        info.put("workflowEngineAvailable", workflowEngineClient.isAvailable());
        
        return info;
    }

    /**
     * 流程部署结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProcessDeploymentResult {
        private String functionUnitId;
        private boolean success;
        private boolean partialSuccess;
        private String message;
        private Map<String, String> deployedProcesses;
        private List<String> errors;

        public static ProcessDeploymentResult success(String functionUnitId, Map<String, String> deployedProcesses) {
            return ProcessDeploymentResult.builder()
                    .functionUnitId(functionUnitId)
                    .success(true)
                    .partialSuccess(false)
                    .message("所有流程部署成功")
                    .deployedProcesses(deployedProcesses)
                    .errors(List.of())
                    .build();
        }

        public static ProcessDeploymentResult partialSuccess(String functionUnitId, 
                Map<String, String> deployedProcesses, List<String> errors) {
            return ProcessDeploymentResult.builder()
                    .functionUnitId(functionUnitId)
                    .success(false)
                    .partialSuccess(true)
                    .message("部分流程部署成功")
                    .deployedProcesses(deployedProcesses)
                    .errors(errors)
                    .build();
        }

        public static ProcessDeploymentResult failure(String functionUnitId, List<String> errors) {
            return ProcessDeploymentResult.builder()
                    .functionUnitId(functionUnitId)
                    .success(false)
                    .partialSuccess(false)
                    .message("流程部署失败")
                    .deployedProcesses(Map.of())
                    .errors(errors)
                    .build();
        }

        public static ProcessDeploymentResult noProcess(String functionUnitId) {
            return ProcessDeploymentResult.builder()
                    .functionUnitId(functionUnitId)
                    .success(true)
                    .partialSuccess(false)
                    .message("功能单元没有流程定义")
                    .deployedProcesses(Map.of())
                    .errors(List.of())
                    .build();
        }

        public static ProcessDeploymentResult engineUnavailable(String functionUnitId) {
            return ProcessDeploymentResult.builder()
                    .functionUnitId(functionUnitId)
                    .success(false)
                    .partialSuccess(false)
                    .message("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动")
                    .deployedProcesses(Map.of())
                    .errors(List.of("Workflow engine is not available"))
                    .build();
        }
    }
}
