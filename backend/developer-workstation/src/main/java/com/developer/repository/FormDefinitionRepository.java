package com.developer.repository;

import com.developer.entity.FormDefinition;
import com.developer.enums.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 表单定义仓库
 */
@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {
    
    List<FormDefinition> findByFunctionUnitId(Long functionUnitId);
    
    /**
     * 查询功能单元的所有表单，同时加载绑定的表信息
     */
    @Query("SELECT f FROM FormDefinition f LEFT JOIN FETCH f.boundTable WHERE f.functionUnit.id = :functionUnitId")
    List<FormDefinition> findByFunctionUnitIdWithBoundTable(@Param("functionUnitId") Long functionUnitId);
    
    /**
     * 根据ID查询表单，同时加载绑定的表信息
     */
    @Query("SELECT f FROM FormDefinition f LEFT JOIN FETCH f.boundTable WHERE f.id = :id")
    Optional<FormDefinition> findByIdWithBoundTable(@Param("id") Long id);
    
    List<FormDefinition> findByFunctionUnitIdAndFormType(Long functionUnitId, FormType formType);
    
    Optional<FormDefinition> findByFunctionUnitIdAndFormName(Long functionUnitId, String formName);
    
    boolean existsByFunctionUnitIdAndFormName(Long functionUnitId, String formName);
    
    boolean existsByFunctionUnitIdAndFormNameAndIdNot(Long functionUnitId, String formName, Long id);
    
    boolean existsByBoundTable_Id(Long tableId);
}
