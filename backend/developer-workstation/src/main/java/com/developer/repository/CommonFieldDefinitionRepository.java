package com.developer.repository;

import com.developer.entity.CommonFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 公共表字段定义仓库
 */
@Repository
public interface CommonFieldDefinitionRepository extends JpaRepository<CommonFieldDefinition, Long> {

    List<CommonFieldDefinition> findByCommonTable_IdOrderBySortOrder(Long commonTableId);

    @Modifying
    @Query("DELETE FROM CommonFieldDefinition f WHERE f.commonTable.id = :commonTableId")
    void deleteByCommonTableId(@Param("commonTableId") Long commonTableId);
}
