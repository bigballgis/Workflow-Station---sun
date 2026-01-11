package com.portal.repository;

import com.portal.entity.ProcessInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程实例Repository
 */
@Repository
public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, String> {

    Page<ProcessInstance> findByStartUserIdOrderByStartTimeDesc(String startUserId, Pageable pageable);

    Page<ProcessInstance> findByStartUserIdAndStatusOrderByStartTimeDesc(String startUserId, String status, Pageable pageable);

    List<ProcessInstance> findByStartUserIdAndStatus(String startUserId, String status);

    long countByStartUserId(String startUserId);

    long countByStartUserIdAndStatus(String startUserId, String status);

    /**
     * 查询分配给指定用户的待办流程实例
     */
    Page<ProcessInstance> findByCurrentAssigneeAndStatusOrderByStartTimeDesc(String assignee, String status, Pageable pageable);

    /**
     * 查询候选用户包含指定用户的待办流程实例（用于或签场景）
     */
    @Query("SELECT p FROM ProcessInstance p WHERE p.status = :status AND p.candidateUsers LIKE %:userId%")
    Page<ProcessInstance> findByCandidateUsersContainingAndStatus(@Param("userId") String userId, @Param("status") String status, Pageable pageable);

    /**
     * 查询分配给指定用户或候选用户包含指定用户的待办流程实例
     */
    @Query("SELECT p FROM ProcessInstance p WHERE p.status = :status AND (p.currentAssignee = :userId OR p.candidateUsers LIKE %:userId%)")
    Page<ProcessInstance> findByAssigneeOrCandidateAndStatus(@Param("userId") String userId, @Param("status") String status, Pageable pageable);
}
