package com.workflow.util;

import com.platform.common.dto.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 从 Spring Security 上下文解析当前登录用户 ID，用于任务类 API 与 JWT 主体绑定。
 */
public final class WorkflowActorResolver {

    private WorkflowActorResolver() {
    }

    /**
     * @return 已认证用户的业务用户 ID；未认证或无法解析时为空
     */
    public static Optional<String> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up && StringUtils.hasText(up.getUserId())) {
            return Optional.of(up.getUserId().trim());
        }
        if (principal instanceof String s && StringUtils.hasText(s)) {
            return Optional.of(s.trim());
        }
        String name = authentication.getName();
        if (StringUtils.hasText(name) && !"anonymousUser".equalsIgnoreCase(name)) {
            return Optional.of(name.trim());
        }
        return Optional.empty();
    }
}
