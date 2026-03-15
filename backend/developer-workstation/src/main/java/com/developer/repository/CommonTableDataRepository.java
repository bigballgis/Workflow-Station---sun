package com.developer.repository;

import com.developer.entity.CommonTableData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 公共表数据仓库
 */
@Repository
public interface CommonTableDataRepository extends JpaRepository<CommonTableData, Long> {

    Page<CommonTableData> findByCommonTable_Id(Long commonTableId, Pageable pageable);

    List<CommonTableData> findByCommonTable_Id(Long commonTableId);

    /**
     * 通过关键词搜索（JSONB 全文搜索，cast to text for broad matching）
     */
    @Query(value = "SELECT * FROM dw_common_table_data WHERE common_table_id = :tableId AND data_json::text ILIKE CONCAT('%', :keyword, '%')",
           nativeQuery = true)
    List<CommonTableData> searchByKeyword(@Param("tableId") Long tableId, @Param("keyword") String keyword);

    long countByCommonTable_Id(Long commonTableId);
}
