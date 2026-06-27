package com.admin.service;

import com.admin.dto.response.RelationTableResponse;
import com.platform.common.dto.RelationTableDataRowDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Relation Table 表数据管理服务接口
 * 管理已部署 Relation Table 的业务数据（CRUD + 状态变更）
 */
public interface RelationTableDataService {

    /**
     * 获取已部署的表列表（仅返回 DEPLOYED 状态的表）
     */
    List<RelationTableResponse> getDeployedTables();

    /**
     * 分页查询表数据（根据已部署的最新表结构动态查询物理表数据，支持搜索过滤）
     */
    Page<RelationTableDataRowDTO> queryData(Long tableId, String search, Pageable pageable);

    /**
     * 新增数据
     */
    RelationTableDataRowDTO addData(Long tableId, Map<String, Object> data);

    /**
     * 修改数据
     */
    RelationTableDataRowDTO updateData(Long tableId, String rowId, Map<String, Object> data);

    /**
     * 删除数据
     */
    void deleteData(Long tableId, String rowId);

    /**
     * 变更数据状态（Active/Inactive）
     */
    RelationTableDataRowDTO changeStatus(Long tableId, String rowId, String status);

    /**
     * 导出表数据为 CSV
     */
    String exportCsv(Long tableId, int maxRows);

    /**
     * 生成导入模板（csv|xlsx）
     */
    byte[] generateTemplate(Long tableId, String format);

    /**
     * 导入数据（按公共校验器校验后插入），返回 {inserted, failed, errors}
     */
    Map<String, Object> importData(Long tableId, byte[] fileBytes, String format);
}
