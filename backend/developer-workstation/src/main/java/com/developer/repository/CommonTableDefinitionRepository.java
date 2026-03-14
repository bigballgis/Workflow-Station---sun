package com.developer.repository;

import com.developer.entity.CommonTableDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 公共表定义仓库
 */
@Repository
public interface CommonTableDefinitionRepository extends JpaRepository<CommonTableDefinition, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<CommonTableDefinition> findByCode(String code);

    List<CommonTableDefinition> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT t FROM CommonTableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.id = :id")
    Optional<CommonTableDefinition> findByIdWithFields(@Param("id") Long id);

    @Query("SELECT t FROM CommonTableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.code = :code")
    Optional<CommonTableDefinition> findByCodeWithFields(@Param("code") String code);

    @Query("SELECT DISTINCT t FROM CommonTableDefinition t LEFT JOIN FETCH t.fieldDefinitions ORDER BY t.createdAt DESC")
    List<CommonTableDefinition> findAllWithFields();
}
