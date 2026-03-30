package com.portal.component;

import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.ProcessFormData;
import com.portal.dto.SubTableBindingData;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Process Form 组件
 * 负责 Process Form 数据的获取、提交更新和存在性校验
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessFormComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ChangeHistoryComponent changeHistoryComponent;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    private static final String RETURN_TO_REQUESTER = "RETURN_TO_REQUESTER";

    /**
     * 获取 Process Form 布局 + 当前流程变量值
     *
     * @param processInstanceId 流程实例 ID
     * @return ProcessFormData DTO
     */
    public ProcessFormData getProcessFormData(String processInstanceId) {
        log.debug("Getting process form data for process instance: {}", processInstanceId);

        ProcessInstance processInstance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new PortalException("404", "Process instance not found: " + processInstanceId));

        Map<String, Object> variables = processInstance.getVariables() != null
                ? processInstance.getVariables()
                : Collections.emptyMap();

        String processDefinitionKey = processInstance.getProcessDefinitionKey();

        // Retrieve the PROCESS form definition for this function unit
        Map<String, Object> formDefinition = fetchProcessFormDefinition(processDefinitionKey);

        Map<String, Object> configJson = Collections.emptyMap();
        String formName = "Process Form";
        List<SubTableBindingData> subTableBindings = Collections.emptyList();

        if (formDefinition != null) {
            configJson = extractMapField(formDefinition, "configJson");
            formName = formDefinition.get("name") != null
                    ? (String) formDefinition.get("name")
                    : "Process Form";
            subTableBindings = extractSubTableBindings(formDefinition);
        }

        boolean editable = RETURN_TO_REQUESTER.equals(processInstance.getStatus());

        return ProcessFormData.builder()
                .processInstanceId(processInstanceId)
                .formName(formName)
                .formType("PROCESS")
                .configJson(configJson)
                .fieldValues(new HashMap<>(variables))
                .subTableBindings(subTableBindings)
                .editable(editable)
                .processState(processInstance.getStatus())
                .build();
    }

    /**
     * 提交 Process Form 更新（仅 Return_To_Requester 状态）
     *
     * @param processInstanceId 流程实例 ID
     * @param userId            操作用户 ID
     * @param formData          表单数据
     */
    @Transactional
    public void submitProcessFormUpdate(String processInstanceId, String userId, Map<String, Object> formData) {
        log.info("Submitting process form update for process: {}, user: {}", processInstanceId, userId);

        ProcessInstance processInstance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new PortalException("404", "Process instance not found: " + processInstanceId));

        // Verify process is in Return_To_Requester state
        if (!RETURN_TO_REQUESTER.equals(processInstance.getStatus())) {
            throw new PortalException("403", "Process form can only be updated in Return_To_Requester state. Current state: " + processInstance.getStatus());
        }

        // Get current process variables (old values)
        Map<String, Object> oldValues = processInstance.getVariables() != null
                ? new HashMap<>(processInstance.getVariables())
                : new HashMap<>();

        // Update process variables with new form data
        Map<String, Object> updatedVariables = new HashMap<>(oldValues);
        updatedVariables.putAll(formData);
        processInstance.setVariables(updatedVariables);
        processInstanceRepository.save(processInstance);

        log.info("Process variables updated for process: {}", processInstanceId);

        // Record Change_History via ChangeHistoryComponent (best-effort)
        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(processInstanceId)
                .taskInstanceId(null) // Process Form changes have no task
                .stageId(RETURN_TO_REQUESTER)
                .userId(userId)
                .build();

        changeHistoryComponent.recordFieldChanges(context, oldValues, formData);
    }

    /**
     * 校验 FunctionUnit 是否有 PROCESS 类型表单
     *
     * @param functionUnitId 功能单元 ID
     * @throws PortalException 如果没有 PROCESS form 则抛出 400 异常
     */
    public void validateProcessFormExists(String functionUnitId) {
        log.debug("Validating PROCESS form exists for function unit: {}", functionUnitId);

        boolean exists = checkProcessFormExists(functionUnitId);
        if (!exists) {
            throw new PortalException("400", "PROCESS form not found for function unit: " + functionUnitId
                    + ". A PROCESS form must be configured before starting a process.");
        }
    }

    /**
     * 判断流程是否处于 Return_To_Requester 状态
     */
    public boolean isInReturnToRequesterState(String processInstanceId) {
        return processInstanceRepository.findById(processInstanceId)
                .map(pi -> RETURN_TO_REQUESTER.equals(pi.getStatus()))
                .orElse(false);
    }

    // ==================== Private Helper Methods ====================

    /**
     * 从 admin-center 获取 PROCESS 类型的表单定义
     * TODO: 实际集成时需要通过 admin-center 或 developer-workstation API 获取表单定义
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchProcessFormDefinition(String processDefinitionKey) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + processDefinitionKey + "/forms?formType=PROCESS";
            log.debug("Fetching PROCESS form definition from: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("content")) {
                List<Map<String, Object>> forms = (List<Map<String, Object>>) response.get("content");
                if (forms != null && !forms.isEmpty()) {
                    return forms.get(0);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch PROCESS form definition for {}: {}", processDefinitionKey, e.getMessage());
        }
        return null;
    }

    /**
     * 检查 FunctionUnit 是否存在 PROCESS 类型表单
     * TODO: 实际集成时需要通过 admin-center 或 developer-workstation API 查询
     */
    @SuppressWarnings("unchecked")
    private boolean checkProcessFormExists(String functionUnitId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/forms?formType=PROCESS";
            log.debug("Checking PROCESS form existence from: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("content")) {
                List<Map<String, Object>> forms = (List<Map<String, Object>>) response.get("content");
                return forms != null && !forms.isEmpty();
            }
        } catch (Exception e) {
            log.warn("Failed to check PROCESS form existence for {}: {}", functionUnitId, e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMapField(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<SubTableBindingData> extractSubTableBindings(Map<String, Object> formDefinition) {
        Object bindings = formDefinition.get("subTableBindings");
        if (bindings instanceof List) {
            List<Map<String, Object>> bindingList = (List<Map<String, Object>>) bindings;
            return bindingList.stream()
                    .map(b -> SubTableBindingData.builder()
                            .bindingId(b.get("bindingId") != null ? ((Number) b.get("bindingId")).longValue() : null)
                            .tableName((String) b.get("tableName"))
                            .bindingType((String) b.get("bindingType"))
                            .bindingMode((String) b.get("bindingMode"))
                            .columns((List<Map<String, Object>>) b.get("columns"))
                            .data((List<Map<String, Object>>) b.get("data"))
                            .build())
                    .toList();
        }
        return Collections.emptyList();
    }
}
