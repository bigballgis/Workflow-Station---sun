package com.admin.bi.service;

import com.admin.bi.dto.request.DashboardRegistryUpdateRequest;
import com.admin.bi.dto.response.DashboardRegistryResponse;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.enums.DashboardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Dashboard 注册表 Service 接口
 */
public interface BiDashboardRegistryService {

    /**
     * 触发手动同步
     */
    SyncResultResponse syncDashboards();

    /**
     * 分页查询 Dashboard 列表（支持 title/tags/status 筛选）
     */
    Page<DashboardRegistryResponse> listDashboards(String title, String tags, DashboardStatus status, Pageable pageable);

    /**
     * 获取单个 Dashboard 详情
     */
    DashboardRegistryResponse getDashboard(String id);

    /**
     * 更新本地扩展字段（tags、isDefaultLanding）
     */
    DashboardRegistryResponse updateDashboard(String id, DashboardRegistryUpdateRequest request);

    /**
     * 启用 Dashboard（MANUAL_INACTIVE → ACTIVE）
     */
    DashboardRegistryResponse enableDashboard(String id);

    /**
     * 禁用 Dashboard（ACTIVE → MANUAL_INACTIVE）
     */
    DashboardRegistryResponse disableDashboard(String id);

    /**
     * 删除 Dashboard 记录（有关联分配时拒绝）
     */
    void deleteDashboard(String id);
}
