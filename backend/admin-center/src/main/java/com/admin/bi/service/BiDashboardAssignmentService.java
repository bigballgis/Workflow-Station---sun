package com.admin.bi.service;

import com.admin.bi.dto.request.DashboardAssignmentCreateRequest;
import com.admin.bi.dto.response.DashboardAssignmentResponse;
import com.admin.bi.dto.response.UserDashboardResponse;
import com.admin.bi.enums.AssignmentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Dashboard 分配 Service 接口
 */
public interface BiDashboardAssignmentService {

    /**
     * 创建分配记录
     * 校验: Dashboard 存在且 ACTIVE、Target 存在、唯一性
     */
    DashboardAssignmentResponse createAssignment(DashboardAssignmentCreateRequest request);

    /**
     * 分页查询分配列表（支持 targetType/dashboardTitle 筛选）
     */
    Page<DashboardAssignmentResponse> listAssignments(
            AssignmentTargetType targetType, String dashboardTitle, Pageable pageable);

    /**
     * 更新分配记录
     */
    DashboardAssignmentResponse updateAssignment(String id, DashboardAssignmentCreateRequest request);

    /**
     * 删除分配记录
     */
    void deleteAssignment(String id);

    /**
     * 获取用户有效 Dashboard 列表
     * 合并 User/Role/BU 维度，去重（优先级 USER > ROLE > BU），
     * 仅含 ACTIVE Dashboard，按 displayOrder 升序排序。
     * activeBusinessUnitId 不为空时，必须是用户所属 BU；BU assignment 和
     * BU-scoped role 仅在该 BU 上下文生效。为空时不授予任何 BU 维度权限。
     */
    List<UserDashboardResponse> getUserDashboards(String userId, String activeBusinessUnitId);
}
