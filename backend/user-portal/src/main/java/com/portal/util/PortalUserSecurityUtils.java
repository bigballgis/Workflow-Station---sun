package com.portal.util;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.util.SecurityContextUtils;

import java.util.Optional;

/**
 * 门户 JWT 工作台与访问模式读取。
 * <p>
 * 使用反射调用 {@link UserPrincipal} 上的可选 getter，以便在仅于 {@code backend/user-portal} 目录执行
 * {@code mvn compile} 时，即使本地 {@code ~/.m2} 中仍是较旧的 {@code platform-common} 字节码（尚未包含
 * {@code getPortalAccessMode} / {@code getActiveBusinessUnitId} 等符号），也能通过编译；
 * 运行时使用当前 classpath 中的类，新 JAR 下行为与直接调用一致。
 */
public final class PortalUserSecurityUtils {

    private PortalUserSecurityUtils() {
    }

    public static String getPortalAccessMode(UserPrincipal principal) {
        if (principal == null) {
            return null;
        }
        try {
            Object v = principal.getClass().getMethod("getPortalAccessMode").invoke(principal);
            return v instanceof String s ? s : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static Optional<String> getCurrentActiveBusinessUnitId() {
        return SecurityContextUtils.getCurrentUser().flatMap(PortalUserSecurityUtils::readActiveBusinessUnitId);
    }

    public static Optional<String> getCurrentActiveRoleId() {
        return SecurityContextUtils.getCurrentUser().flatMap(PortalUserSecurityUtils::readActiveRoleId);
    }

    private static Optional<String> readActiveBusinessUnitId(UserPrincipal u) {
        return invokeOptionalStringGetter(u, "getActiveBusinessUnitId");
    }

    private static Optional<String> readActiveRoleId(UserPrincipal u) {
        return invokeOptionalStringGetter(u, "getActiveRoleId");
    }

    private static Optional<String> invokeOptionalStringGetter(UserPrincipal u, String methodName) {
        try {
            Object v = u.getClass().getMethod(methodName).invoke(u);
            if (v instanceof String s && !s.isBlank()) {
                return Optional.of(s);
            }
        } catch (ReflectiveOperationException ignored) {
            // 旧版 UserPrincipal 无此方法时视为未设置
        }
        return Optional.empty();
    }
}
