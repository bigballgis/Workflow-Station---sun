package com.admin.bi.service;

import com.admin.bi.dto.request.GuestTokenRequest;
import com.admin.bi.dto.response.GuestTokenResponse;

/**
 * Guest Token Service 接口
 * 负责验证用户 Dashboard 分配权限并获取 Superset Guest Token
 */
public interface BiGuestTokenService {

    /**
     * 获取 Superset Guest Token
     *
     * @param userId  当前用户 ID
     * @param request 包含 dashboardId 的请求
     * @return Guest Token 和 Dashboard Embed ID
     * @throws com.admin.exception.DashboardNotFoundException Dashboard 不存在
     * @throws org.springframework.security.access.AccessDeniedException 用户未被分配该 Dashboard
     * @throws com.admin.exception.SupersetApiException Superset API 调用失败
     */
    GuestTokenResponse getGuestToken(String userId, GuestTokenRequest request);
}
