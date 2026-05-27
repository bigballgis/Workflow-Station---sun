package com.workflow.repository;

import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Extended task info data access layer.
 * Provides multi-dimensional task query and management capabilities.
 */
@Repository
public interface ExtendedTaskInfoRepository extends JpaRepository<ExtendedTaskInfo, Long> {

    /**
     * Find extended task info by task ID.
     */
    Optional<ExtendedTaskInfo> findByTaskIdAndIsDeletedFalse(String taskId);

    /**
     * Find all tasks by process instance ID.
     */
    List<ExtendedTaskInfo> findByProcessInstanceIdAndIsDeletedFalse(String processInstanceId);

    /**
     * All extended tasks for a process instance (including soft-deleted). The portal may still
     * depend on MI metadata within to reconstruct sub-table row status after process end.
     */
    List<ExtendedTaskInfo> findAllByProcessInstanceId(String processInstanceId);

    /**
     * Query directly assigned tasks for a user.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'USER' " +
           "AND t.assignmentTarget = :userId AND t.status != 'COMPLETED' " +
           "AND t.isDeleted = false")
    List<ExtendedTaskInfo> findDirectAssignedTasks(@Param("userId") String userId);

    /**
     * Query tasks delegated to a user.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.delegatedTo = :userId " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findDelegatedTasks(@Param("userId") String userId);

    /**
     * Query tasks claimed by a user.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.claimedBy = :userId " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findClaimedTasks(@Param("userId") String userId);

    /**
     * Query unclaimed virtual group tasks.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'VIRTUAL_GROUP' " +
           "AND t.assignmentTarget = :groupId AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findVirtualGroupTasks(@Param("groupId") String groupId);

    /**
     * Query unclaimed department role tasks.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'DEPT_ROLE' " +
           "AND t.assignmentTarget = :deptRole AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findDeptRoleTasks(@Param("deptRole") String deptRole);

    /**
     * Query all pending tasks for a user (including direct, delegated, and claimed tasks).
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false " +
           "ORDER BY t.priority DESC, t.createdTime ASC")
    List<ExtendedTaskInfo> findUserTodoTasks(@Param("userId") String userId);

    /**
     * Paginated query of all pending tasks for a user.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    Page<ExtendedTaskInfo> findUserTodoTasks(@Param("userId") String userId, Pageable pageable);

    /**
     * Query virtual group tasks visible to a user (user is a member of the virtual groups).
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'VIRTUAL_GROUP' " +
           "AND t.assignmentTarget IN :groupIds AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findUserVisibleGroupTasks(@Param("groupIds") List<String> groupIds);

    /**
     * Query department role tasks visible to a user (user has the department role).
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'DEPT_ROLE' " +
           "AND t.assignmentTarget IN :deptRoles AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findUserVisibleDeptRoleTasks(@Param("deptRoles") List<String> deptRoles);

    /**
     * Query all visible tasks for a user (including direct, delegated, claimed, virtual group,
     * and department role tasks).
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId " +
           "OR (t.assignmentType = 'VIRTUAL_GROUP' AND t.assignmentTarget IN :groupIds AND t.claimedBy IS NULL) " +
           "OR (t.assignmentType = 'DEPT_ROLE' AND t.assignmentTarget IN :deptRoles AND t.claimedBy IS NULL)) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false " +
           "ORDER BY t.priority DESC, t.createdTime ASC")
    List<ExtendedTaskInfo> findUserAllVisibleTasks(
        @Param("userId") String userId,
        @Param("groupIds") List<String> groupIds,
        @Param("deptRoles") List<String> deptRoles
    );

    /**
     * Paginated query of all visible tasks for a user.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId " +
           "OR (t.assignmentType = 'VIRTUAL_GROUP' AND t.assignmentTarget IN :groupIds AND t.claimedBy IS NULL) " +
           "OR (t.assignmentType = 'DEPT_ROLE' AND t.assignmentTarget IN :deptRoles AND t.claimedBy IS NULL)) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    Page<ExtendedTaskInfo> findUserAllVisibleTasks(
        @Param("userId") String userId,
        @Param("groupIds") List<String> groupIds,
        @Param("deptRoles") List<String> deptRoles,
        Pageable pageable
    );

    /**
     * Query overdue tasks.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.dueDate < :currentTime " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findOverdueTasks(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Query tasks due soon (within the specified time range).
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.dueDate BETWEEN :currentTime AND :alertTime " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findTasksDueSoon(
        @Param("currentTime") LocalDateTime currentTime,
        @Param("alertTime") LocalDateTime alertTime
    );

    /**
     * Query tasks by assignment type.
     */
    List<ExtendedTaskInfo> findByAssignmentTypeAndIsDeletedFalse(AssignmentType assignmentType);

    /**
     * Query tasks by status.
     */
    List<ExtendedTaskInfo> findByStatusAndIsDeletedFalse(String status);

    /**
     * Query tasks created within the specified time range.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.createdTime BETWEEN :startTime AND :endTime " +
           "AND t.isDeleted = false")
    List<ExtendedTaskInfo> findTasksCreatedBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Query tasks completed within the specified time range.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.completedTime BETWEEN :startTime AND :endTime " +
           "AND t.status = 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findTasksCompletedBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Count a user's tasks.
     */
    @Query("SELECT COUNT(t) FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    long countUserTodoTasks(@Param("userId") String userId);

    /**
     * Count a user's overdue tasks.
     */
    @Query("SELECT COUNT(t) FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.dueDate < :currentTime AND t.status != 'COMPLETED' AND t.isDeleted = false")
    long countUserOverdueTasks(@Param("userId") String userId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * Query high-priority tasks.
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.priority >= :minPriority " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false " +
           "ORDER BY t.priority DESC, t.createdTime ASC")
    List<ExtendedTaskInfo> findHighPriorityTasks(@Param("minPriority") Integer minPriority);

    /**
     * Soft-delete a task.
     */
    @Query("UPDATE ExtendedTaskInfo t SET t.isDeleted = true, t.updatedTime = :currentTime, " +
           "t.updatedBy = :updatedBy WHERE t.taskId = :taskId")
    void softDeleteByTaskId(
        @Param("taskId") String taskId,
        @Param("currentTime") LocalDateTime currentTime,
        @Param("updatedBy") String updatedBy
    );

    /**
     * Batch soft-delete all tasks of a process instance.
     */
    @Query("UPDATE ExtendedTaskInfo t SET t.isDeleted = true, t.updatedTime = :currentTime, " +
           "t.updatedBy = :updatedBy WHERE t.processInstanceId = :processInstanceId")
    void softDeleteByProcessInstanceId(
        @Param("processInstanceId") String processInstanceId,
        @Param("currentTime") LocalDateTime currentTime,
        @Param("updatedBy") String updatedBy
    );
}
