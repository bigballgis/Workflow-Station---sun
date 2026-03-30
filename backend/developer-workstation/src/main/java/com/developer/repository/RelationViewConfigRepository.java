package com.developer.repository;

import com.developer.entity.RelationViewConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Relation Table View 配置仓库
 */
@Repository
public interface RelationViewConfigRepository extends JpaRepository<RelationViewConfig, Long> {

    /**
     * 按绑定ID查询 View 配置
     */
    Optional<RelationViewConfig> findByBindingId(Long bindingId);

    /**
     * 按表ID查询所有 View 配置
     */
    List<RelationViewConfig> findByTableId(Long tableId);
}
