package com.developer.repository;

import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元仓库
 */
@Repository
public interface FunctionUnitRepository extends JpaRepository<FunctionUnit, Long>, JpaSpecificationExecutor<FunctionUnit> {
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
    
    boolean existsByCode(String code);
    
    Optional<FunctionUnit> findByName(String name);
    
    Optional<FunctionUnit> findByCode(String code);
    
    Page<FunctionUnit> findByStatus(FunctionUnitStatus status, Pageable pageable);
    
    /**
     * 使用 EntityGraph 预加载关联数据，避免懒加载异常
     */
    @EntityGraph(attributePaths = {"icon", "tableDefinitions", "formDefinitions", "actionDefinitions", "processDefinition"})
    @Query("SELECT fu FROM FunctionUnit fu")
    Page<FunctionUnit> findAllWithRelations(Pageable pageable);

    /**
     * 根据 ID 加载功能单元及所有关联数据（用于 AI 上下文序列化）
     */
    @EntityGraph(attributePaths = {"icon", "tableDefinitions", "formDefinitions", "actionDefinitions", "processDefinition"})
    @Query("SELECT fu FROM FunctionUnit fu WHERE fu.id = :id")
    Optional<FunctionUnit> findByIdWithRelations(@Param("id") Long id);
    
    @Query("SELECT fu.id FROM FunctionUnit fu WHERE fu.createdBy = :username")
    List<Long> findIdsByCreatedBy(@Param("username") String username);
}
