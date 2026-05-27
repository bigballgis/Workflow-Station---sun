package com.admin.component;

import com.admin.client.WorkflowEngineClient;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deploys BPMN process definitions from a function unit onto the Flowable-powered workflow engine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessDeploymentComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitContentRepository contentRepository;
    private final I18nService i18nService;

    /**
     * Deploys process definitions stored on the function unit to the Flowable engine.
     *
     * @param functionUnitId function unit identifier
     * @return deployment outcome
     */
    @Transactional
    public ProcessDeploymentResult deployFunctionUnitProcess(String functionUnitId) {
        log.info("Deploying process for function unit: {}", functionUnitId);

        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AdminBusinessException("FUNCTION_UNIT_NOT_FOUND",
                        i18nService.getMessage("admin.fu.not_found_by_id", functionUnitId)));

        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);

        if (processContents.isEmpty()) {
            log.warn("No process definition found for function unit: {}", functionUnitId);
            return noProcessResult(functionUnitId);
        }

        if (!workflowEngineClient.isAvailable()) {
            log.error("Workflow engine is not available");
            return engineUnavailableResult(functionUnitId);
        }

        Map<String, String> deployedProcesses = new HashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        String itemFailedFallback = i18nService.getMessage("admin.deploy.process.item_deploy_failed_fallback");

        for (FunctionUnitContent processContent : processContents) {
            try {
                String bpmnXml = processContent.getContentData();
                String processKey = extractProcessKey(bpmnXml, functionUnit.getCode());
                String processName = functionUnit.getName() + " - " + processContent.getContentName();

                Optional<WorkflowEngineClient.ProcessDeploymentResult> result =
                        workflowEngineClient.deployProcess(processKey, bpmnXml, processName);

                if (result.isPresent() && result.get().isSuccess()) {
                    deployedProcesses.put(processContent.getContentName(),
                            result.get().getProcessDefinitionId());

                    processContent.setFlowableProcessDefinitionId(result.get().getProcessDefinitionId());
                    processContent.setFlowableDeploymentId(result.get().getDeploymentId());
                    contentRepository.save(processContent);

                    log.info("Process deployed: {} -> {}",
                            processContent.getContentName(), result.get().getProcessDefinitionId());
                } else {
                    String errorMsg = result.map(WorkflowEngineClient.ProcessDeploymentResult::getMessage)
                            .orElse(itemFailedFallback);
                    errors.add(processContent.getContentName() + ": " + errorMsg);
                    log.error("Failed to deploy process: {}", processContent.getContentName());
                }

            } catch (Exception e) {
                errors.add(processContent.getContentName() + ": " + safeDetail(e));
                log.error("Error deploying process: {}", processContent.getContentName(), e);
            }
        }

        if (!deployedProcesses.isEmpty()) {
            functionUnit.setProcessDeployed(true);
            functionUnit.setProcessDeploymentCount(deployedProcesses.size());
            functionUnitRepository.save(functionUnit);
        }

        if (errors.isEmpty()) {
            return successResult(functionUnitId, deployedProcesses);
        } else if (!deployedProcesses.isEmpty()) {
            return partialSuccessResult(functionUnitId, deployedProcesses, errors);
        } else {
            return failureResult(functionUnitId, errors);
        }
    }

    /**
     * Dry-run deployment to Flowable to validate BPMN; cleans deployment records afterward and does not persist FU flags.
     */
    public ProcessDeploymentResult dryRunDeployFunctionUnitProcess(String functionUnitId) {
        log.info("Dry-run deploying process for function unit: {}", functionUnitId);

        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new AdminBusinessException("FUNCTION_UNIT_NOT_FOUND",
                        i18nService.getMessage("admin.fu.not_found_by_id", functionUnitId)));

        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);

        if (processContents.isEmpty()) {
            return noProcessResult(functionUnitId);
        }

        if (!workflowEngineClient.isAvailable()) {
            return engineUnavailableResult(functionUnitId);
        }

        List<String> errors = new java.util.ArrayList<>();
        List<String> deploymentIdsToCleanup = new java.util.ArrayList<>();
        String itemFailedFallback = i18nService.getMessage("admin.deploy.process.item_deploy_failed_fallback");

        for (FunctionUnitContent processContent : processContents) {
            try {
                String bpmnXml = processContent.getContentData();
                String processKey = extractProcessKey(bpmnXml, functionUnit.getCode());
                String processName = functionUnit.getName() + " - " + processContent.getContentName() + " (validate)";

                Optional<WorkflowEngineClient.ProcessDeploymentResult> result =
                        workflowEngineClient.deployProcess(processKey, bpmnXml, processName);

                if (result.isPresent() && result.get().isSuccess()) {
                    if (result.get().getDeploymentId() != null) {
                        deploymentIdsToCleanup.add(result.get().getDeploymentId());
                    }
                    log.info("Dry-run process deploy OK: {}", processContent.getContentName());
                } else {
                    String errorMsg = result.map(WorkflowEngineClient.ProcessDeploymentResult::getMessage)
                            .orElse(itemFailedFallback);
                    errors.add(processContent.getContentName() + ": " + errorMsg);
                    log.error("Dry-run failed for process: {}", processContent.getContentName());
                }
            } catch (Exception e) {
                errors.add(processContent.getContentName() + ": " + safeDetail(e));
                log.error("Dry-run error for process: {}", processContent.getContentName(), e);
            }
        }

        for (String deploymentId : deploymentIdsToCleanup) {
            try {
                workflowEngineClient.deleteProcessDefinition(deploymentId, true);
            } catch (Exception e) {
                log.warn("Failed to cleanup dry-run deployment {}: {}", deploymentId, e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return successResult(functionUnitId, Map.of());
        }
        return failureResult(functionUnitId, errors);
    }

    private static String safeDetail(Exception e) {
        return e.getClass().getSimpleName();
    }

    private ProcessDeploymentResult successResult(String functionUnitId, Map<String, String> deployedProcesses) {
        return ProcessDeploymentResult.builder()
                .functionUnitId(functionUnitId)
                .success(true)
                .partialSuccess(false)
                .engineUnavailable(false)
                .message(i18nService.getMessage("admin.deploy.process.all_deployed_success"))
                .deployedProcesses(deployedProcesses)
                .errors(List.of())
                .build();
    }

    private ProcessDeploymentResult partialSuccessResult(String functionUnitId,
                                                         Map<String, String> deployedProcesses,
                                                         List<String> errors) {
        return ProcessDeploymentResult.builder()
                .functionUnitId(functionUnitId)
                .success(false)
                .partialSuccess(true)
                .engineUnavailable(false)
                .message(i18nService.getMessage("admin.deploy.process.partial_deployed_success"))
                .deployedProcesses(deployedProcesses)
                .errors(errors)
                .build();
    }

    private ProcessDeploymentResult failureResult(String functionUnitId, List<String> errors) {
        return ProcessDeploymentResult.builder()
                .functionUnitId(functionUnitId)
                .success(false)
                .partialSuccess(false)
                .engineUnavailable(false)
                .message(i18nService.getMessage("admin.deploy.process.deploy_failed_summary"))
                .deployedProcesses(Map.of())
                .errors(errors)
                .build();
    }

    private ProcessDeploymentResult noProcessResult(String functionUnitId) {
        return ProcessDeploymentResult.builder()
                .functionUnitId(functionUnitId)
                .success(true)
                .partialSuccess(false)
                .engineUnavailable(false)
                .message(i18nService.getMessage("admin.deploy.process.no_definition_in_unit"))
                .deployedProcesses(Map.of())
                .errors(List.of())
                .build();
    }

    private ProcessDeploymentResult engineUnavailableResult(String functionUnitId) {
        return ProcessDeploymentResult.builder()
                .functionUnitId(functionUnitId)
                .success(false)
                .partialSuccess(false)
                .engineUnavailable(true)
                .message(i18nService.getMessage("admin.deploy.workflow_engine_unavailable_hint"))
                .deployedProcesses(Map.of())
                .errors(List.of(i18nService.getMessage("admin.deploy.workflow_engine_client_unavailable")))
                .build();
    }

    private String extractProcessKey(String bpmnXml, String defaultKey) {
        String extracted = extractProcessKeyFromXml(bpmnXml);
        return extracted != null ? extracted : defaultKey;
    }

    /**
     * Parses {@code id} from {@code &lt;bpmn:process&gt;} or {@code &lt;process&gt;}.
     */
    private String extractProcessKeyFromXml(String bpmnXml) {
        try {
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
            log.debug("Failed to extract process key from BPMN XML: {}", e.getMessage());
        }
        return null;
    }

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

    public boolean isProcessDeployed(String functionUnitId) {
        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(functionUnitId, ContentType.PROCESS);

        return processContents.stream()
                .anyMatch(c -> c.getFlowableProcessDefinitionId() != null);
    }

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

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProcessDeploymentResult {
        private String functionUnitId;
        private boolean success;
        private boolean partialSuccess;
        private boolean engineUnavailable;
        private String message;
        private Map<String, String> deployedProcesses;
        private List<String> errors;
    }
}
