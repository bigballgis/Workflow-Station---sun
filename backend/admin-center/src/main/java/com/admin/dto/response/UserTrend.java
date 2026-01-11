package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户趋势响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTrend {
    
    /**
     * 日期
     */
    private String date;
    
    /**
     * 活跃用户数
     */
    private long activeUsers;
    
    /**
     * 新增用户数
     */
    private long newUsers;
    
    /**
     * 登录次数
     */
    private long loginCount;
}
