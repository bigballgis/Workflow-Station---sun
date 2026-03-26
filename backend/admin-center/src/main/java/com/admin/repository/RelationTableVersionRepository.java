package com.admin.repository;

import com.admin.entity.RelationTableVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Relation Table 版本 Repository
 */
@Repository
public interface RelationTableVersionRepository extends JpaRepository<RelationTableVersion, Long> {

    /**
     * 根据表定义ID查找所有版本（按版本号降序）
     */
    List<RelationTableVersion> findByTableDefinitionIdOrderByVersionNumberDesc(Long tableDefinitionId);

    /**
     * 根据表定义ID和版本号查找
     */
    Optional<RelationTableVersion> findByTableDefinitionIdAndVersionNumber(Long tableDefinitionId, Integer versionNumber);

    /**
     * 获取表定义的最新版本
     */
    @Query("SELECT v FROM RelationTableVersion v WHERE v.tableDefinition.id = :tableDefinitionId ORDER BY v.versionNumber DESC LIMIT 1")
    Optional<RelationTableVersion> findLatestVersion(@Param("tableDefinitionId") Long tableDefinitionId);

    /**
     * 删除表定义的所有版本
     */
    void deleteByTableDefinitionId(Long tableDefinitionId);
}
