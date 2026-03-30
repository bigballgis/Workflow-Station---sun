package com.developer.repository;

import com.developer.entity.RelationViewField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Relation Table View 字段仓库
 */
@Repository
public interface RelationViewFieldRepository extends JpaRepository<RelationViewField, Long> {

    /**
     * 按 View 配置ID查询字段列表，按排序顺序排列
     */
    List<RelationViewField> findByViewConfigIdOrderBySortOrderAsc(Long viewConfigId);
}
