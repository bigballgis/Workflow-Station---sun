package com.developer.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * 读取当前请求所选「开发组（团队）」——由前端通过 {@code X-Dev-Group-Id} 请求头传入。
 *
 * <p>该值仅作为「可见范围过滤」的维度，<b>不是</b>能力/权限边界：后端在
 * {@link FunctionUnitWorkspaceAccessService} 中会校验用户确实为该组成员后才据此收窄，
 * 因此即便前端伪造也无法越权（非成员的选择会被忽略）。</p>
 *
 * <p>特殊值 {@code __ALL__}（仅管理员使用）表示不按单一团队过滤，视为「未选择」。</p>
 */
public final class DevGroupContextHolder {

    public static final String HEADER = "X-Dev-Group-Id";
    private static final String ALL_SENTINEL = "__ALL__";

    private DevGroupContextHolder() {
    }

    /** 当前请求所选团队 id；未选择 / __ALL__ / 非 HTTP 上下文时返回空。 */
    public static Optional<String> getSelectedGroupId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            String value = sra.getRequest().getHeader(HEADER);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && !ALL_SENTINEL.equals(trimmed)) {
                    return Optional.of(trimmed);
                }
            }
        }
        return Optional.empty();
    }
}
