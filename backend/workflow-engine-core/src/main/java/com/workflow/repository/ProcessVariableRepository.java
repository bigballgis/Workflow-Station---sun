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
 * Process variable data access layer.
 *
 * Provides database operation interfaces for process variables.
 * Supports complex queries and history management.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Repository
public interface ProcessVariableRepository extends JpaRepository<ProcessVariable, String> {

    /**
     * Find variable history by process instance ID and variable name,
     * ordered by creation time descending.
     *
     * @param processInstanceId process instance ID
     * @param name variable name
     * @return variable history list
     */
    List<ProcessVariable> findByProcessInstanceIdAndNameOrderByCreatedTimeDesc(
            String processInstanceId, String name);

    /**
     * Find all variables by process instance ID.
     *
     * @param processInstanceId process instance ID
     * @return variable list
     */
    List<ProcessVariable> findByProcessInstanceIdOrderByCreatedTimeDesc(String processInstanceId);

    /**
     * Find variables by task ID.
     *
     * @param taskId task ID
     * @return variable list
     */
    List<ProcessVariable> findByTaskIdOrderByCreatedTimeDesc(String taskId);

    /**
     * Find variables by execution ID.
     *
     * @param executionId execution ID
     * @return variable list
     */
    List<ProcessVariable> findByExecutionIdOrderByCreatedTimeDesc(String executionId);

    /**
     * Find the latest variable value for the specified process instance.
     *
     * @param processInstanceId process instance ID
     * @param name variable name
     * @return latest variable record
     */
    @Query("SELECT v FROM ProcessVariable v WHERE v.processInstanceId = :processInstanceId " +
           "AND v.name = :name AND v.createdTime = " +
           "(SELECT MAX(v2.createdTime) FROM ProcessVariable v2 " +
           "WHERE v2.processInstanceId = :processInstanceId AND v2.name = :name)")
    Optional<ProcessVariable> findLatestByProcessInstanceIdAndName(
            @Param("processInstanceId") String processInstanceId, 
            @Param("name") String name);

    /**
     * Find variables by variable type.
     *
     * @param type variable type
     * @param pageable pagination parameters
     * @return paginated variable list
     */
    Page<ProcessVariable> findByType(VariableType type, Pageable pageable);

    /**
     * Find variable changes within the specified time range.
     *
     * @param startTime start time
     * @param endTime end time
     * @return variable change list
     */
    List<ProcessVariable> findByCreatedTimeBetweenOrderByCreatedTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find variables by tenant ID.
     *
     * @param tenantId tenant ID
     * @param pageable pagination parameters
     * @return paginated variable list
     */
    Page<ProcessVariable> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Query variable statistics for a process instance.
     *
     * @param processInstanceId process instance ID
     * @return statistics results: [variable name, change count]
     */
    @Query("SELECT v.name, COUNT(v) FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId " +
           "GROUP BY v.name ORDER BY COUNT(v) DESC")
    List<Object[]> getVariableStatistics(@Param("processInstanceId") String processInstanceId);

    /**
     * Find variables containing the specified JSON path.
     * Uses PostgreSQL JSONB query features.
     *
     * @param jsonPath JSON path expression
     * @return matching variable list
     */
    @Query(value = "SELECT * FROM wf_process_variables " +
                   "WHERE type = 'JSON' AND json_value @> :jsonPath\\:\\:jsonb", 
           nativeQuery = true)
    List<ProcessVariable> findByJsonPath(@Param("jsonPath") String jsonPath);

    /**
     * Full-text search for variable content.
     *
     * @param searchText search text
     * @param pageable pagination parameters
     * @return matching variable list
     */
    @Query("SELECT v FROM ProcessVariable v WHERE " +
           "LOWER(v.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(v.textValue) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(v.changeReason) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    Page<ProcessVariable> searchVariables(@Param("searchText") String searchText, Pageable pageable);

    /**
     * Delete all variable history records for the specified process instance.
     *
     * @param processInstanceId process instance ID
     * @return number of deleted records
     */
    long deleteByProcessInstanceId(String processInstanceId);

    /**
     * Delete variable history records before the specified time.
     *
     * @param beforeTime cutoff time
     * @return number of deleted records
     */
    long deleteByCreatedTimeBefore(LocalDateTime beforeTime);

    /**
     * Query distinct variable names.
     *
     * @param processInstanceId process instance ID
     * @return variable name list
     */
    @Query("SELECT DISTINCT v.name FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId " +
           "ORDER BY v.name")
    List<String> findDistinctVariableNames(@Param("processInstanceId") String processInstanceId);

    /**
     * Count distinct variables for a process instance.
     *
     * @param processInstanceId process instance ID
     * @return distinct variable count
     */
    @Query("SELECT COUNT(DISTINCT v.name) FROM ProcessVariable v " +
           "WHERE v.processInstanceId = :processInstanceId")
    long countDistinctVariablesByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /**
     * Find large JSON variables (exceeding the specified size).
     *
     * @param sizeThreshold size threshold (characters)
     * @return large variable list
     */
    @Query(value = "SELECT * FROM wf_process_variables " +
                   "WHERE type = 'JSON' AND LENGTH(json_value) > :sizeThreshold " +
                   "ORDER BY LENGTH(json_value) DESC", 
           nativeQuery = true)
    List<ProcessVariable> findLargeJsonVariables(@Param("sizeThreshold") int sizeThreshold);

    /**
     * Query variable change frequency statistics.
     *
     * @param processInstanceId process instance ID
     * @param hours statistics time range (hours)
     * @return change frequency statistics
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
