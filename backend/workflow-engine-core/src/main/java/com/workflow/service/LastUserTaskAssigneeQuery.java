package com.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 按流程实例查询「物理上最近一次已完成的用户任务」的 assignee，供 {@code LAST_TASK_ASSIGNEE} 锚点使用。
 * <p>仅统计 {@link HistoricTaskInstance}（用户任务），不含服务任务等。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LastUserTaskAssigneeQuery {

    private final HistoryService historyService;

    /**
     * @param processInstanceId 流程实例 ID
     * @return 最近完成用户任务的 assignee；若无则空
     */
    public Optional<String> findLastCompletedUserTaskAssignee(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Optional.empty();
        }
        try {
            List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId.trim())
                    .finished()
                    .orderByHistoricTaskInstanceEndTime()
                    .desc()
                    .list();
            for (HistoricTaskInstance hti : list) {
                if (hti == null) {
                    continue;
                }
                String assignee = hti.getAssignee();
                if (assignee != null && !assignee.isBlank()) {
                    return Optional.of(assignee.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query last completed user task assignee for instance {}: {}",
                    processInstanceId, e.getMessage());
        }
        return Optional.empty();
    }
}
