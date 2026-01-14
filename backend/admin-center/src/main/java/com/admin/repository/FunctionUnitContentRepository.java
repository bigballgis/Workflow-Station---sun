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
     * 根据流程定义Key查找内容（flowable_process_definition_id 以 processKey: 开头）
     */
    @Query("SELECT c FROM FunctionUnitContent c WHERE c.flowableProcessDefinitionId LIKE :processKey || ':%'")
    Optional<FunctionUnitContent> findByProcessDefinitionKey(@Param("processKey") String processKey);
}
