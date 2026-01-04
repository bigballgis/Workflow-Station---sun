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
 * 扩展任务信息数据访问层
 * 提供多维度任务查询和管理功能
 */
@Repository
public interface ExtendedTaskInfoRepository extends JpaRepository<ExtendedTaskInfo, Long> {

    /**
     * 根据任务ID查找扩展任务信息
     */
    Optional<ExtendedTaskInfo> findByTaskIdAndIsDeletedFalse(String taskId);

    /**
     * 根据流程实例ID查找所有任务
     */
    List<ExtendedTaskInfo> findByProcessInstanceIdAndIsDeletedFalse(String processInstanceId);

    /**
     * 查询用户的直接分配任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'USER' " +
           "AND t.assignmentTarget = :userId AND t.status != 'COMPLETED' " +
           "AND t.isDeleted = false")
    List<ExtendedTaskInfo> findDirectAssignedTasks(@Param("userId") String userId);

    /**
     * 查询委托给用户的任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.delegatedTo = :userId " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findDelegatedTasks(@Param("userId") String userId);

    /**
     * 查询用户认领的任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.claimedBy = :userId " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findClaimedTasks(@Param("userId") String userId);

    /**
     * 查询虚拟组的未认领任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'VIRTUAL_GROUP' " +
           "AND t.assignmentTarget = :groupId AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findVirtualGroupTasks(@Param("groupId") String groupId);

    /**
     * 查询部门角色的未认领任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'DEPT_ROLE' " +
           "AND t.assignmentTarget = :deptRole AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findDeptRoleTasks(@Param("deptRole") String deptRole);

    /**
     * 查询用户的所有待办任务（包括直接分配、委托、认领的任务）
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false " +
           "ORDER BY t.priority DESC, t.createdTime ASC")
    List<ExtendedTaskInfo> findUserTodoTasks(@Param("userId") String userId);

    /**
     * 分页查询用户的所有待办任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    Page<ExtendedTaskInfo> findUserTodoTasks(@Param("userId") String userId, Pageable pageable);

    /**
     * 查询用户可见的虚拟组任务（用户是虚拟组成员）
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'VIRTUAL_GROUP' " +
           "AND t.assignmentTarget IN :groupIds AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findUserVisibleGroupTasks(@Param("groupIds") List<String> groupIds);

    /**
     * 查询用户可见的部门角色任务（用户拥有该部门角色）
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.assignmentType = 'DEPT_ROLE' " +
           "AND t.assignmentTarget IN :deptRoles AND t.claimedBy IS NULL " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findUserVisibleDeptRoleTasks(@Param("deptRoles") List<String> deptRoles);

    /**
     * 查询用户的所有可见任务（包括直接分配、委托、认领、虚拟组、部门角色）
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
     * 分页查询用户的所有可见任务
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
     * 查询过期任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.dueDate < :currentTime " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findOverdueTasks(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 查询即将过期的任务（指定时间范围内）
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.dueDate BETWEEN :currentTime AND :alertTime " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findTasksDueSoon(
        @Param("currentTime") LocalDateTime currentTime,
        @Param("alertTime") LocalDateTime alertTime
    );

    /**
     * 根据分配类型查询任务
     */
    List<ExtendedTaskInfo> findByAssignmentTypeAndIsDeletedFalse(AssignmentType assignmentType);

    /**
     * 根据状态查询任务
     */
    List<ExtendedTaskInfo> findByStatusAndIsDeletedFalse(String status);

    /**
     * 查询指定时间范围内创建的任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.createdTime BETWEEN :startTime AND :endTime " +
           "AND t.isDeleted = false")
    List<ExtendedTaskInfo> findTasksCreatedBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查询指定时间范围内完成的任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.completedTime BETWEEN :startTime AND :endTime " +
           "AND t.status = 'COMPLETED' AND t.isDeleted = false")
    List<ExtendedTaskInfo> findTasksCompletedBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 统计用户的任务数量
     */
    @Query("SELECT COUNT(t) FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false")
    long countUserTodoTasks(@Param("userId") String userId);

    /**
     * 统计用户的过期任务数量
     */
    @Query("SELECT COUNT(t) FROM ExtendedTaskInfo t WHERE " +
           "((t.assignmentType = 'USER' AND t.assignmentTarget = :userId) " +
           "OR t.delegatedTo = :userId " +
           "OR t.claimedBy = :userId) " +
           "AND t.dueDate < :currentTime AND t.status != 'COMPLETED' AND t.isDeleted = false")
    long countUserOverdueTasks(@Param("userId") String userId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * 根据优先级查询任务
     */
    @Query("SELECT t FROM ExtendedTaskInfo t WHERE t.priority >= :minPriority " +
           "AND t.status != 'COMPLETED' AND t.isDeleted = false " +
           "ORDER BY t.priority DESC, t.createdTime ASC")
    List<ExtendedTaskInfo> findHighPriorityTasks(@Param("minPriority") Integer minPriority);

    /**
     * 软删除任务
     */
    @Query("UPDATE ExtendedTaskInfo t SET t.isDeleted = true, t.updatedTime = :currentTime, " +
           "t.updatedBy = :updatedBy WHERE t.taskId = :taskId")
    void softDeleteByTaskId(
        @Param("taskId") String taskId,
        @Param("currentTime") LocalDateTime currentTime,
        @Param("updatedBy") String updatedBy
    );

    /**
     * 批量软删除流程实例的所有任务
     */
    @Query("UPDATE ExtendedTaskInfo t SET t.isDeleted = true, t.updatedTime = :currentTime, " +
           "t.updatedBy = :updatedBy WHERE t.processInstanceId = :processInstanceId")
    void softDeleteByProcessInstanceId(
        @Param("processInstanceId") String processInstanceId,
        @Param("currentTime") LocalDateTime currentTime,
        @Param("updatedBy") String updatedBy
    );
}