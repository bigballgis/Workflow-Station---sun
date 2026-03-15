package com.portal.repository;

import com.portal.entity.CommonTableDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommonTableDefinitionRepository extends JpaRepository<CommonTableDefinition, Long> {

    Optional<CommonTableDefinition> findByCode(String code);

    @Query("SELECT t FROM CommonTableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.code = :code AND t.status = 'PUBLISHED' AND t.enabled = true")
    Optional<CommonTableDefinition> findByCodeWithFields(@Param("code") String code);

    @Query("SELECT DISTINCT t FROM CommonTableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.status = 'PUBLISHED' AND t.enabled = true ORDER BY t.createdAt DESC")
    List<CommonTableDefinition> findAllWithFields();

    @Query("SELECT t FROM CommonTableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.code = :code AND t.status = 'PUBLISHED' AND t.enabled = true")
    Optional<CommonTableDefinition> findPublishedByCodeWithFields(@Param("code") String code);
}
