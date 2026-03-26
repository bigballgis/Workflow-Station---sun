package com.portal.service;

import com.platform.common.dto.RelationTableDTO;
import com.portal.dto.PageResponse;

import java.util.List;
import java.util.Map;

/**
 * Portal Relation Table 服务接口
 * 提供只读数据查看、权限过滤、CSV 导出和 Lookup 搜索
 */
public interface PortalRelationTableService {

    /**
     * 获取用户可见的 Relation Table 列表
     * 根据 portal_visible 和用户 Business Role 过滤
     */
    List<RelationTableDTO> getVisibleTables(String userId);

    /**
     * 只读分页查询表数据
     */
    PageResponse<Map<String, Object>> queryTableData(Long tableId, String userId,
                                                      int page, int size, String search);

    /**
     * 导出 CSV
     */
    String exportCsv(Long tableId, String userId, int maxRows);

    /**
     * Lookup 搜索
     */
    List<Map<String, Object>> searchForLookup(Long tableId, String keyword,
                                               List<String> searchFields, String displayField,
                                               int limit);
}
