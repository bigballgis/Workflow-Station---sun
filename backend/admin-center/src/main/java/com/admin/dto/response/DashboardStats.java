package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 统计数据响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    
    /**
     * 用户总数
     */
    private long totalUsers;
    
    /**
     * 业务单元总数
     */
    private long totalBusinessUnits;
    
    /**
     * 角色总数
     */
    private long totalRoles;
    
    /**
     * 在线用户数
     */
    private long onlineUsers;
    
    /**
     * 活跃流程数
     */
    private long activeProcesses;
    
    /**
     * 待办任务数
     */
    private long pendingTasks;
    
    /**
     * 今日登录数
     */
    private long todayLogins;
    
    /**
     * 今日新增用户数
     */
    private long todayNewUsers;
}
