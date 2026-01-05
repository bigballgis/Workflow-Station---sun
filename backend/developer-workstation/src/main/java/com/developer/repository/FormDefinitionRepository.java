package com.developer.repository;

import com.developer.entity.FormDefinition;
import com.developer.enums.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 表单定义仓库
 */
@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {
    
    List<FormDefinition> findByFunctionUnitId(Long functionUnitId);
    
    List<FormDefinition> findByFunctionUnitIdAndFormType(Long functionUnitId, FormType formType);
    
    Optional<FormDefinition> findByFunctionUnitIdAndFormName(Long functionUnitId, String formName);
    
    boolean existsByFunctionUnitIdAndFormName(Long functionUnitId, String formName);
    
    boolean existsByFunctionUnitIdAndFormNameAndIdNot(Long functionUnitId, String formName, Long id);
    
    boolean existsByBoundTableId(Long tableId);
}
