package com.developer.repository;

import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 功能单元仓库
 */
@Repository
public interface FunctionUnitRepository extends JpaRepository<FunctionUnit, Long>, JpaSpecificationExecutor<FunctionUnit> {
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
    
    Optional<FunctionUnit> findByName(String name);
    
    Page<FunctionUnit> findByStatus(FunctionUnitStatus status, Pageable pageable);
}
