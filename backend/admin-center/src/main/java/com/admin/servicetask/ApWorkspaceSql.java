package com.admin.servicetask;

import com.admin.servicetask.config.ServiceTaskProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 管理面列表里把 AP project 还原成 <b>workspace（DW 开发组）</b>的 SQL 片段。
 *
 * <p>映射方向与 {@link ApWorkspaceResolver} 相反：那边是「组 → project」，这里是
 * 「project → 组」。两边共用同一组配置（Public 组 id / Public externalId / 团队前缀），
 * 所以改前缀不会让列表和会话对不上。</p>
 *
 * <p>join 里用绑定参数而不是拼字符串常量：前缀是配置项，拼进 SQL 既是注入面也会让
 * 「配置改了、SQL 没跟着改」这种漂移无声发生。调用方必须把 {@link #joinParams()} 放在
 * 参数列表<b>最前面</b>（join 在 where 之前）。</p>
 */
@Component
@RequiredArgsConstructor
public class ApWorkspaceSql {

    /**
     * workspace 展示名。没有 externalId 的 project（AP 个人沙箱等）回落其自身显示名——
     * 这些不是 HERMES 的 workspace，但仍会出现在跨 project 的运维视图里，不能显示成空。
     */
    public static final String LABEL_SQL = "COALESCE(vg.name, p.\"displayName\")";

    /** {@link #LABEL_SQL} 依赖的 join 别名（{@code vg}）；必须与 {@code p} 同在一个查询里。 */
    private static final String JOIN_TEMPLATE = """
             LEFT JOIN sys_virtual_groups vg ON vg.id = CASE
                 WHEN p."externalId" = ? THEN ?
                 WHEN p."externalId" LIKE ? THEN substr(p."externalId", ?)
             END
            """;

    private final ServiceTaskProperties properties;

    /** 供列表 SQL 拼接的 join 子句（配合 {@link #joinParams()}）。 */
    public String joinClause() {
        return JOIN_TEMPLATE;
    }

    /** join 的绑定参数，顺序与 {@link #joinClause()} 中的 {@code ?} 一致。 */
    public List<Object> joinParams() {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        String prefix = managed.getTeamProjectExternalIdPrefix();
        return List.of(
                managed.getProjectExternalId(),
                managed.getPublicGroupId(),
                prefix + "%",
                prefix.length() + 1);
    }
}
