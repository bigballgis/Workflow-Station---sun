package com.portal.component;

import com.portal.entity.ProcessDraft;
import com.portal.repository.ProcessDraftRepository;
import com.platform.common.util.ApiResponseBodyUnwrap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 草稿管理组件
 * 负责流程草稿的 CRUD 操作。
 * 
 * 从 ProcessComponent 中提取，降低该类的复杂度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessDraftComponent {

    private final ProcessDraftRepository processDraftRepository;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * 保存草稿
     */
    @Transactional
    public ProcessDraft saveDraft(String userId, String processKey, Map<String, Object> formData) {
        Optional<ProcessDraft> existing = processDraftRepository
                .findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey);
        ProcessDraft draft;
        if (existing.isPresent()) {
            draft = existing.get();
            draft.setFormData(formData);
            draft.setUpdatedAt(LocalDateTime.now());
        } else {
            draft = new ProcessDraft();
            draft.setUserId(userId);
            draft.setProcessDefinitionKey(processKey);
            draft.setFormData(formData);
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
        }
        return processDraftRepository.save(draft);
    }

    /**
     * 获取草稿
     */
    public Optional<ProcessDraft> getDraft(String userId, String processKey) {
        return processDraftRepository
                .findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey);
    }

    /**
     * 删除草稿
     */
    @Transactional
    public void deleteDraft(String userId, String processKey) {
        processDraftRepository
                .findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey)
                .ifPresent(processDraftRepository::delete);
    }

    /**
     * 获取用户的草稿列表
     */
    public List<Map<String, Object>> getDraftList(String userId) {
        log.info("Getting draft list for user: {}", userId);
        List<ProcessDraft> drafts = processDraftRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ProcessDraft draft : drafts) {
            Map<String, Object> draftInfo = new HashMap<>();
            draftInfo.put("id", draft.getId());
            draftInfo.put("processDefinitionKey", draft.getProcessDefinitionKey());
            draftInfo.put("formData", draft.getFormData());
            draftInfo.put("createdAt", draft.getCreatedAt());
            draftInfo.put("updatedAt", draft.getUpdatedAt());

            // 尝试获取功能单元名称
            try {
                String name = resolveFunctionUnitName(draft.getProcessDefinitionKey());
                draftInfo.put("processDefinitionName", name != null ? name : draft.getProcessDefinitionKey());
            } catch (Exception e) {
                log.warn("Failed to get function unit name for {}: {}", draft.getProcessDefinitionKey(), e.getMessage());
                draftInfo.put("processDefinitionName", draft.getProcessDefinitionKey());
            }

            result.add(draftInfo);
        }

        return result;
    }

    /**
     * 根据ID删除草稿
     */
    @Transactional
    public void deleteDraftById(String userId, Long draftId) {
        processDraftRepository.findById(draftId).ifPresent(draft -> {
            if (draft.getUserId().equals(userId)) {
                processDraftRepository.delete(draft);
            }
        });
    }

    /**
     * 从 admin-center 获取功能单元名称
     */
    private String resolveFunctionUnitName(String processDefinitionKey) {
        try {
            String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(processDefinitionKey);
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (!payload.isEmpty() && payload.get("name") != null) {
                return (String) payload.get("name");
            }
        } catch (Exception e) {
            log.debug("Failed to resolve function unit name for {}: {}", processDefinitionKey, e.getMessage());
        }
        return null;
    }
}
