package com.workflow.repository;

import com.workflow.entity.ProcessVariable;
import com.workflow.enums.VariableType;
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
 * 流程变量数据访问层
 * 
 * 提供流程变量的数据库操作接口
 * 支持复杂查询和历史记录管理
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Repository
public interface ProcessVariableRepository extends JpaRepository<ProcessVariable, String> {

    /**
     * 根据流程实例ID和变量名称查询变量历史
     * 按创建时间倒序排列
     * 
     * @param processInstanceId 流程实例ID
     * @param name 变量名称
     * @return 变量历史记录列表
     */
    List<ProcessVariable> findByProcessInstanceIdAndNameOrderByCreatedTimeDesc(
            String processInstanceId, String name);

    /**
     * 根据流程实例ID查询所有变量
     * 
     * @param processInstanceId 流程实例ID
     * @return 变量列表
     */
    List<ProcessVariable> findByProcessInstanceIdOrderByCreatedTimeDesc(String processInstanceId);

    /**
     * 根据任务ID查询变量
     * 
     * @param taskId 任务ID
     * @return 变量列表
     */
    List<ProcessVariable> findByTaskIdOrderByCreatedTimeDesc(String taskId);

    /**
     * 根据执行ID查询变量
     * 
     * @param executionId 执行ID
     * @return 变量列表
     */
    List<ProcessVariable> findByExecutionIdOrderByCreatedTimeDesc(String executionId);

    /**
     * 查询指定流程实例的最新变量值
     * 
     * @param processInstanceId 流程实例ID
     * @param name 变量名称
     * @return 最新的变量记录
     */
    @Query("SELECT v FROM ProcessVariable v WHERE v.processInstanceId = :processInstanceId " +
           "AND v.name = :name AND v.createdTime = " +
           "(SELECT MAX(v2.createdTime) FROM ProcessVariable v2 " +
           "WHERE v2.processInstanceId = :processInstanceId AND v2.name = :name)")
    Optional<ProcessVariable> findLatestByProcessInstanceIdAndName(
            @Param("processInstanceId") String processInstanceId, 
            @Param("name") String name);

    /**
     * 根据变量类型查询变量
     * 
     * @param type 变量类型
     * @param pageable 分页参数
     * @return 分页的变量列表
     */
    Page<ProcessVariable> findByType(VariableType type, Pageable pageable);

    /**
     * 查询指定时间范围内的变量变更
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 变量变更列表
     */
    List<ProcessVariable> findByCreatedTimeBetweenOrderByCreatedTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据租户ID查询变量
     * 
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 分页的变量列表
     */
    Page<ProcessVariable> findByTenantId(String tenantId, Pageable pageable);

    /**
     * 查询流程实例的变量统计信息
     * 
     * @param processInstanceId 流程实例ID
     * @return 统计结果：[变量名称, 变更次数]
     */
    @Query("SELECT v.name, COUNT(v) FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId " +
           "GROUP BY v.name ORDER BY COUNT(v) DESC")
    List<Object[]> getVariableStatistics(@Param("processInstanceId") String processInstanceId);

    /**
     * 查询包含指定JSON路径的变量
     * 使用PostgreSQL JSONB查询功能
     * 
     * @param jsonPath JSON路径表达式
     * @return 匹配的变量列表
     */
    @Query(value = "SELECT * FROM wf_process_variables " +
                   "WHERE type = 'JSON' AND json_value @> :jsonPath\\:\\:jsonb", 
           nativeQuery = true)
    List<ProcessVariable> findByJsonPath(@Param("jsonPath") String jsonPath);

    /**
     * 全文搜索变量内容
     * 
     * @param searchText 搜索文本
     * @param pageable 分页参数
     * @return 匹配的变量列表
     */
    @Query("SELECT v FROM ProcessVariable v WHERE " +
           "LOWER(v.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(v.textValue) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(v.changeReason) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    Page<ProcessVariable> searchVariables(@Param("searchText") String searchText, Pageable pageable);

    /**
     * 删除指定流程实例的所有变量历史记录
     * 
     * @param processInstanceId 流程实例ID
     * @return 删除的记录数
     */
    long deleteByProcessInstanceId(String processInstanceId);

    /**
     * 删除指定时间之前的变量历史记录
     * 
     * @param beforeTime 截止时间
     * @return 删除的记录数
     */
    long deleteByCreatedTimeBefore(LocalDateTime beforeTime);

    /**
     * 查询变量名称列表（去重）
     * 
     * @param processInstanceId 流程实例ID
     * @return 变量名称列表
     */
    @Query("SELECT DISTINCT v.name FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId " +
           "ORDER BY v.name")
    List<String> findDistinctVariableNames(@Param("processInstanceId") String processInstanceId);

    /**
     * 统计流程实例的变量数量
     * 
     * @param processInstanceId 流程实例ID
     * @return 变量数量
     */
    @Query("SELECT COUNT(DISTINCT v.name) FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId")
    long countDistinctVariablesByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /**
     * 查询大型JSON变量（超过指定大小）
     * 
     * @param sizeThreshold 大小阈值（字符数）
     * @return 大型变量列表
     */
    @Query(value = "SELECT * FROM wf_process_variables " +
                   "WHERE type = 'JSON' AND LENGTH(json_value) > :sizeThreshold " +
                   "ORDER BY LENGTH(json_value) DESC", 
           nativeQuery = true)
    List<ProcessVariable> findLargeJsonVariables(@Param("sizeThreshold") int sizeThreshold);

    /**
     * 查询变量变更频率统计
     * 
     * @param processInstanceId 流程实例ID
     * @param hours 统计时间范围（小时）
     * @return 变更频率统计
     */
    @Query("SELECT v.name, COUNT(v) as changeCount FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId " +
           "AND v.createdTime >= :startTime " +
           "GROUP BY v.name " +
           "HAVING COUNT(v) > 1 " +
           "ORDER BY COUNT(v) DESC")
    List<Object[]> getVariableChangeFrequency(
            @Param("processInstanceId") String processInstanceId,
            @Param("startTime") LocalDateTime startTime);
}