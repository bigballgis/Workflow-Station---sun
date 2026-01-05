package com.portal.component;

import com.portal.dto.ProcessDefinitionInfo;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import com.portal.entity.FavoriteProcess;
import com.portal.entity.ProcessDraft;
import com.portal.repository.FavoriteProcessRepository;
import com.portal.repository.ProcessDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ProcessComponent {

    private final FavoriteProcessRepository favoriteProcessRepository;
    private final ProcessDraftRepository processDraftRepository;

    /**
     * 获取可发起的流程定义列表
     */
    public List<ProcessDefinitionInfo> getAvailableProcessDefinitions(String userId, String category, String keyword) {
        // 模拟流程定义数据
        List<ProcessDefinitionInfo> definitions = new ArrayList<>();
        definitions.add(ProcessDefinitionInfo.builder()
                .id("def-1").key("leave-request").name("请假申请")
                .description("员工请假申请流程").category("人事").version(1).build());
        definitions.add(ProcessDefinitionInfo.builder()
                .id("def-2").key("expense-claim").name("费用报销")
                .description("费用报销申请流程").category("财务").version(1).build());
        definitions.add(ProcessDefinitionInfo.builder()
                .id("def-3").key("purchase-request").name("采购申请")
                .description("物资采购申请流程").category("采购").version(1).build());

        // 过滤
        if (category != null && !category.isEmpty()) {
            definitions.removeIf(d -> !d.getCategory().equals(category));
        }
        if (keyword != null && !keyword.isEmpty()) {
            definitions.removeIf(d -> !d.getName().contains(keyword) && !d.getDescription().contains(keyword));
        }

        // 标记收藏
        List<FavoriteProcess> favorites = favoriteProcessRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        Set<String> favoriteKeys = new HashSet<>();
        favorites.forEach(f -> favoriteKeys.add(f.getProcessDefinitionKey()));
        definitions.forEach(d -> d.setIsFavorite(favoriteKeys.contains(d.getKey())));

        return definitions;
    }

    /**
     * 发起流程
     */
    public ProcessInstanceInfo startProcess(String userId, String processKey, ProcessStartRequest request) {
        if (processKey == null || processKey.isEmpty()) {
            throw new IllegalArgumentException("流程Key不能为空");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        return ProcessInstanceInfo.builder()
                .id(UUID.randomUUID().toString())
                .processDefinitionId("def-" + processKey)
                .processDefinitionName(processKey)
                .businessKey(request.getBusinessKey())
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .startUserId(userId)
                .startUserName(userId)
                .currentNode("审批节点")
                .build();
    }

    /**
     * 获取我的申请列表
     */
    public Page<ProcessInstanceInfo> getMyApplications(String userId, String status, Pageable pageable) {
        // 模拟数据
        List<ProcessInstanceInfo> instances = new ArrayList<>();
        instances.add(ProcessInstanceInfo.builder()
                .id("inst-1").processDefinitionName("请假申请")
                .businessKey("LEAVE-2024-001").startTime(LocalDateTime.now().minusDays(1))
                .status("RUNNING").startUserId(userId).currentNode("部门经理审批").build());
        instances.add(ProcessInstanceInfo.builder()
                .id("inst-2").processDefinitionName("费用报销")
                .businessKey("EXPENSE-2024-001").startTime(LocalDateTime.now().minusDays(3))
                .status("COMPLETED").startUserId(userId).endTime(LocalDateTime.now().minusDays(1)).build());

        if (status != null && !status.isEmpty()) {
            instances.removeIf(i -> !i.getStatus().equals(status));
        }

        return new PageImpl<>(instances, pageable, instances.size());
    }

    /**
     * 获取流程详情
     */
    public ProcessInstanceInfo getProcessDetail(String processId) {
        return ProcessInstanceInfo.builder()
                .id(processId)
                .processDefinitionName("请假申请")
                .businessKey("LEAVE-2024-001")
                .startTime(LocalDateTime.now().minusDays(1))
                .status("RUNNING")
                .startUserId("user-1")
                .startUserName("张三")
                .currentNode("部门经理审批")
                .currentAssignee("李四")
                .build();
    }

    /**
     * 撤回流程
     */
    public boolean withdrawProcess(String userId, String processId, String reason) {
        // 验证流程是否可撤回
        ProcessInstanceInfo process = getProcessDetail(processId);
        if (process == null) {
            return false;
        }
        if (!process.getStartUserId().equals(userId)) {
            return false;
        }
        if (!"RUNNING".equals(process.getStatus())) {
            return false;
        }
        return true;
    }

    /**
     * 催办流程
     */
    public boolean urgeProcess(String userId, String processId) {
        ProcessInstanceInfo process = getProcessDetail(processId);
        if (process == null) {
            return false;
        }
        if (!process.getStartUserId().equals(userId)) {
            return false;
        }
        if (!"RUNNING".equals(process.getStatus())) {
            return false;
        }
        // 发送催办通知
        return true;
    }

    /**
     * 切换收藏状态
     */
    public boolean toggleFavorite(String userId, String processKey) {
        Optional<FavoriteProcess> existing = favoriteProcessRepository.findByUserIdAndProcessDefinitionKey(userId, processKey);
        if (existing.isPresent()) {
            favoriteProcessRepository.delete(existing.get());
            return false;
        } else {
            FavoriteProcess favorite = new FavoriteProcess();
            favorite.setUserId(userId);
            favorite.setProcessDefinitionKey(processKey);
            favorite.setCreatedAt(LocalDateTime.now());
            favoriteProcessRepository.save(favorite);
            return true;
        }
    }

    /**
     * 保存草稿
     */
    public ProcessDraft saveDraft(String userId, String processKey, Map<String, Object> formData) {
        Optional<ProcessDraft> existing = processDraftRepository.findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey);
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
        return processDraftRepository.findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey);
    }

    /**
     * 删除草稿
     */
    public void deleteDraft(String userId, String processKey) {
        processDraftRepository.findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey)
                .ifPresent(processDraftRepository::delete);
    }
}
