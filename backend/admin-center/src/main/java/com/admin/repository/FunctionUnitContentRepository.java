package com.admin.repository;

import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元内容仓库接口
 */
@Repository
public interface FunctionUnitContentRepository extends JpaRepository<FunctionUnitContent, String> {
    
    /**
     * 根据功能单元ID查找内容
     */
    List<FunctionUnitContent> findByFunctionUnitId(String functionUnitId);
    
    /**
     * 根据功能单元ID和内容类型查找内容
     */
    List<FunctionUnitContent> findByFunctionUnitIdAndContentType(
            String functionUnitId, ContentType contentType);
    
    /**
     * 根据功能单元ID和内容名称查找内容
     */
    Optional<FunctionUnitContent> findByFunctionUnitIdAndContentName(
            String functionUnitId, String contentName);
    
    /**
     * 查找流程定义内容
     */
    @Query("SELECT c FROM FunctionUnitContent c WHERE c.functionUnit.id = :functionUnitId AND c.contentType = 'PROCESS'")
    List<FunctionUnitContent> findProcessContents(@Param("functionUnitId") String functionUnitId);
    
    /**
     * 查找表单内容
     */
    @Query("SELECT c FROM FunctionUnitContent c WHERE c.functionUnit.id = :functionUnitId AND c.contentType = 'FORM'")
    List<FunctionUnitContent> findFormContents(@Param("functionUnitId") String functionUnitId);
    
    /**
     * 查找数据表内容
     */
    @Query("SELECT c FROM FunctionUnitContent c WHERE c.functionUnit.id = :functionUnitId AND c.contentType = 'DATA_TABLE'")
    List<FunctionUnitContent> findDataTableContents(@Param("functionUnitId") String functionUnitId);
    
    /**
     * 删除功能单元的所有内容
     */
    void deleteByFunctionUnitId(String functionUnitId);
    
    /**
     * 统计功能单元的内容数量
     */
    long countByFunctionUnitId(String functionUnitId);
    
    /**
     * 统计功能单元特定类型的内容数量
     */
    long countByFunctionUnitIdAndContentType(String functionUnitId, ContentType contentType);
    
    /**
     * 根据引擎 process key 查找关联发布（definition id 可能仅为 UUID）
     * 优先启用的发布，否则返回最新内容记录；只返回一个候选以界定查询成本
     * 通过 act_re_procdef 精确关联，避免依赖 definition id 的字符串格式
     */
    @Query(value = "SELECT c.* FROM sys_function_unit_contents c "
            + "JOIN sys_function_units u ON u.id=c.function_unit_id "
            + "JOIN act_re_procdef p ON p.id_=c.flowable_process_definition_id "
            + "WHERE c.content_type='PROCESS' AND p.key_=:processKey "
            + "ORDER BY COALESCE(u.enabled,false) DESC,c.created_at DESC LIMIT 1", nativeQuery = true)
    List<FunctionUnitContent> findAllByProcessDefinitionKey(@Param("processKey") String processKey);

    /**
     * @deprecated Use findAllByProcessDefinitionKey instead (multiple deployments may exist)
     */
    @Deprecated
    @Query(value = "SELECT * FROM sys_function_unit_contents WHERE flowable_process_definition_id LIKE CONCAT(:processKey, ':%') ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<FunctionUnitContent> findByProcessDefinitionKey(@Param("processKey") String processKey);
}
