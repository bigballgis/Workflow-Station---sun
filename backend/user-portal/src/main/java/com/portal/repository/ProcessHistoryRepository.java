package com.portal.repository;

import com.portal.entity.ProcessHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程历史记录仓库
 */
@Repository
public interface ProcessHistoryRepository extends JpaRepository<ProcessHistory, Long> {

    /**
     * 根据流程实例ID查询历史记录，按操作时间排序
     */
    List<ProcessHistory> findByProcessInstanceIdOrderByOperationTimeAsc(String processInstanceId);

    /**
     * 根据任务ID查询历史记录
     */
    List<ProcessHistory> findByTaskIdOrderByOperationTimeAsc(String taskId);
}
