package com.portal.repository;

import com.portal.entity.ChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 变更历史 Repository
 */
@Repository
public interface ChangeHistoryRepository extends JpaRepository<ChangeHistory, Long> {

    List<ChangeHistory> findByProcessInstanceIdOrderByTimestampAsc(String processInstanceId);

    List<ChangeHistory> findByTaskInstanceId(String taskInstanceId);
}
