package com.portal.component;

import com.portal.dto.DelegationRuleRequest;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import com.portal.enums.DelegationStatus;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.DelegationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 委托管理组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegationComponent {

    private final DelegationRuleRepository delegationRuleRepository;
    private final DelegationAuditRepository delegationAuditRepository;

    /**
     * 创建委托规则
     */
    @Transactional
    public DelegationRule createDelegationRule(String delegatorId, DelegationRuleRequest request) {
        // 验证不能委托给自己
        if (delegatorId.equals(request.getDelegateId())) {
            throw new PortalException("400", "不能委托给自己");
        }

        // 检查是否存在循环委托
        if (hasCircularDelegation(delegatorId, request.getDelegateId())) {
            throw new PortalException("400", "存在循环委托");
        }

        DelegationRule rule = DelegationRule.builder()
                .delegatorId(delegatorId)
                .delegateId(request.getDelegateId())
                .delegationType(request.getDelegationType())
                .processTypes(request.getProcessTypes())
                .priorityFilter(request.getPriorityFilter())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason(request.getReason())
                .status(DelegationStatus.ACTIVE)
                .build();

        rule = delegationRuleRepository.save(rule);

        // 记录审计日志
        recordAudit(delegatorId, request.getDelegateId(), null, "CREATE_DELEGATION", "SUCCESS", request.getReason());

        log.info("用户 {} 创建了委托规则给 {}", delegatorId, request.getDelegateId());
        return rule;
    }

    /**
     * 更新委托规则
     */
    @Transactional
    public DelegationRule updateDelegationRule(Long ruleId, String delegatorId, DelegationRuleRequest request) {
        DelegationRule rule = delegationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new PortalException("404", "委托规则不存在"));

        // 验证是否是委托人
        if (!delegatorId.equals(rule.getDelegatorId())) {
            throw new PortalException("403", "只有委托人才能修改委托规则");
        }

        rule.setDelegateId(request.getDelegateId());
        rule.setDelegationType(request.getDelegationType());
        rule.setProcessTypes(request.getProcessTypes());
        rule.setPriorityFilter(request.getPriorityFilter());
        rule.setStartTime(request.getStartTime());
        rule.setEndTime(request.getEndTime());
        rule.setReason(request.getReason());

        rule = delegationRuleRepository.save(rule);

        // 记录审计日志
        recordAudit(delegatorId, request.getDelegateId(), null, "UPDATE_DELEGATION", "SUCCESS", request.getReason());

        log.info("用户 {} 更新了委托规则 {}", delegatorId, ruleId);
        return rule;
    }

    /**
     * 删除委托规则
     */
    @Transactional
    public void deleteDelegationRule(Long ruleId, String delegatorId) {
        DelegationRule rule = delegationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new PortalException("404", "委托规则不存在"));

        // 验证是否是委托人
        if (!delegatorId.equals(rule.getDelegatorId())) {
            throw new PortalException("403", "只有委托人才能删除委托规则");
        }

        delegationRuleRepository.delete(rule);

        // 记录审计日志
        recordAudit(delegatorId, rule.getDelegateId(), null, "DELETE_DELEGATION", "SUCCESS", null);

        log.info("用户 {} 删除了委托规则 {}", delegatorId, ruleId);
    }

    /**
     * 暂停委托规则
     */
    @Transactional
    public DelegationRule suspendDelegationRule(Long ruleId, String delegatorId) {
        DelegationRule rule = delegationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new PortalException("404", "委托规则不存在"));

        if (!delegatorId.equals(rule.getDelegatorId())) {
            throw new PortalException("403", "只有委托人才能暂停委托规则");
        }

        rule.setStatus(DelegationStatus.SUSPENDED);
        rule = delegationRuleRepository.save(rule);

        recordAudit(delegatorId, rule.getDelegateId(), null, "SUSPEND_DELEGATION", "SUCCESS", null);

        log.info("用户 {} 暂停了委托规则 {}", delegatorId, ruleId);
        return rule;
    }

    /**
     * 恢复委托规则
     */
    @Transactional
    public DelegationRule resumeDelegationRule(Long ruleId, String delegatorId) {
        DelegationRule rule = delegationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new PortalException("404", "委托规则不存在"));

        if (!delegatorId.equals(rule.getDelegatorId())) {
            throw new PortalException("403", "只有委托人才能恢复委托规则");
        }

        rule.setStatus(DelegationStatus.ACTIVE);
        rule = delegationRuleRepository.save(rule);

        recordAudit(delegatorId, rule.getDelegateId(), null, "RESUME_DELEGATION", "SUCCESS", null);

        log.info("用户 {} 恢复了委托规则 {}", delegatorId, ruleId);
        return rule;
    }

    /**
     * 获取用户的委托规则列表
     */
    public List<DelegationRule> getDelegationRules(String delegatorId) {
        return delegationRuleRepository.findByDelegatorId(delegatorId);
    }

    /**
     * 获取用户的有效委托规则
     */
    public List<DelegationRule> getActiveDelegationRules(String delegatorId) {
        return delegationRuleRepository.findActiveDelegationRules(delegatorId, LocalDateTime.now());
    }

    /**
     * 获取委托给用户的规则
     */
    public List<DelegationRule> getDelegationsForDelegate(String delegateId) {
        return delegationRuleRepository.findActiveDelegationsForDelegate(delegateId, LocalDateTime.now());
    }

    /**
     * 获取委托审计记录
     */
    public Page<DelegationAudit> getDelegationAuditRecords(String userId, Pageable pageable) {
        return delegationAuditRepository.findByDelegatorIdOrDelegateIdOrderByCreatedAtDesc(userId, userId, pageable);
    }

    /**
     * 获取代理任务的委托人列表
     * 返回当前用户作为代理人时，所有委托人的ID列表
     */
    public List<String> getDelegatorIds(String delegateId) {
        List<DelegationRule> rules = delegationRuleRepository
                .findActiveDelegationsForDelegate(delegateId, LocalDateTime.now());
        return rules.stream()
                .map(DelegationRule::getDelegatorId)
                .distinct()
                .toList();
    }

    /**
     * 检查用户是否有权代理处理指定任务
     * @param delegateId 代理人ID
     * @param delegatorId 委托人ID（任务原处理人）
     * @param processType 流程类型
     * @param priority 任务优先级
     * @return 是否有代理权限
     */
    public boolean canProcessAsDelegate(String delegateId, String delegatorId, 
                                        String processType, String priority) {
        List<DelegationRule> rules = delegationRuleRepository
                .findActiveDelegationsForDelegate(delegateId, LocalDateTime.now());
        
        for (DelegationRule rule : rules) {
            if (!rule.getDelegatorId().equals(delegatorId)) {
                continue;
            }
            
            // 检查流程类型过滤
            if (rule.getProcessTypes() != null && !rule.getProcessTypes().isEmpty()) {
                if (!rule.getProcessTypes().contains(processType)) {
                    continue;
                }
            }
            
            // 检查优先级过滤
            if (rule.getPriorityFilter() != null && !rule.getPriorityFilter().isEmpty()) {
                if (!rule.getPriorityFilter().contains(priority)) {
                    continue;
                }
            }
            
            return true;
        }
        return false;
    }

    /**
     * 记录代理任务处理审计
     */
    @Transactional
    public void recordDelegateTaskProcess(String delegatorId, String delegateId, 
                                          String taskId, String operationType, 
                                          String result, String detail) {
        recordAudit(delegatorId, delegateId, taskId, operationType, result, detail);
        log.info("代理人 {} 代理 {} 处理任务 {}, 操作: {}, 结果: {}", 
                delegateId, delegatorId, taskId, operationType, result);
    }

    /**
     * 获取指定委托人的有效委托规则
     */
    public DelegationRule getActiveDelegationRule(String delegatorId, String delegateId) {
        List<DelegationRule> rules = delegationRuleRepository
                .findActiveDelegationsForDelegate(delegateId, LocalDateTime.now());
        return rules.stream()
                .filter(r -> r.getDelegatorId().equals(delegatorId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查是否存在循环委托
     */
    private boolean hasCircularDelegation(String delegatorId, String delegateId) {
        // 检查被委托人是否已经委托给了委托人
        List<DelegationRule> delegateRules = delegationRuleRepository
                .findActiveDelegationRules(delegateId, LocalDateTime.now());
        
        for (DelegationRule rule : delegateRules) {
            if (rule.getDelegateId().equals(delegatorId)) {
                return true;
            }
            // 递归检查（限制深度为2级）
            List<DelegationRule> subRules = delegationRuleRepository
                    .findActiveDelegationRules(rule.getDelegateId(), LocalDateTime.now());
            for (DelegationRule subRule : subRules) {
                if (subRule.getDelegateId().equals(delegatorId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 记录审计日志
     */
    private void recordAudit(String delegatorId, String delegateId, String taskId, 
                            String operationType, String result, String detail) {
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(delegatorId)
                .delegateId(delegateId)
                .taskId(taskId)
                .operationType(operationType)
                .operationResult(result)
                .operationDetail(detail)
                .build();
        delegationAuditRepository.save(audit);
    }
}
