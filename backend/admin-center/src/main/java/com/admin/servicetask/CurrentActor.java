package com.admin.servicetask;

import com.admin.exception.ServiceTaskActorRequiredException;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.util.SecurityContextUtils;

/**
 * 取当前操作人（Activepieces 一切写路径的归属主体）。
 *
 * <p>两条来源都落在同一个 {@code SecurityContext}：
 * <ul>
 *   <li><b>UI 路径</b>：平台 JWT（cookie / Authorization）→ {@code JwtAuthenticationFilter}；</li>
 *   <li><b>服务间路径</b>：C-3 的 {@code X-Service-Token} + {@code X-User-Id}/{@code X-Username}
 *       → {@code SecurityConfig.ServiceCallAuthenticationFilter}。</li>
 * </ul>
 *
 * <p>取不到就抛 {@link ServiceTaskActorRequiredException}（fail-loud）——绝不回退共享身份。
 */
public final class CurrentActor {

    private CurrentActor() {
    }

    /** @throws ServiceTaskActorRequiredException SecurityContext 里没有已认证主体 */
    public static UserPrincipal require() {
        return SecurityContextUtils.getCurrentUser().orElseThrow(() ->
                new ServiceTaskActorRequiredException(
                        "Activepieces 操作必须归属具体操作人：当前请求没有已认证主体。"
                                + "UI 调用请确认已登录；服务间调用请带上 C-3 的 X-Service-Token "
                                + "以及 X-User-Id / X-Username（发起人）。"));
    }
}
