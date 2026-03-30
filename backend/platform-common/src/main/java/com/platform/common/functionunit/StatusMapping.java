package com.platform.common.functionunit;

import java.util.Map;

/**
 * 跨模块状态枚举映射工具类。
 *
 * <p>developer-workstation 和 admin-center 使用不同的状态枚举：
 * <ul>
 *   <li>developer-workstation: DRAFT, PUBLISHED, ARCHIVED</li>
 *   <li>admin-center: DRAFT, VALIDATED, DEPLOYED, DEPRECATED</li>
 * </ul>
 *
 * <p>映射关系：
 * <ul>
 *   <li>DRAFT ↔ DRAFT</li>
 *   <li>PUBLISHED → VALIDATED（开发者发布 = 管理中心已验证）</li>
 *   <li>ARCHIVED → DEPRECATED（开发者归档 = 管理中心已废弃）</li>
 *   <li>VALIDATED → PUBLISHED（反向映射）</li>
 *   <li>DEPLOYED → PUBLISHED（管理中心已部署在开发者侧无直接对应，映射为 PUBLISHED）</li>
 *   <li>DEPRECATED → ARCHIVED（反向映射）</li>
 * </ul>
 *
 * <h3>ID 类型差异说明</h3>
 * <ul>
 *   <li>developer-workstation 使用 Long（自增），适合单实例开发环境</li>
 *   <li>admin-center 使用 String/UUID，适合分布式部署环境</li>
 * </ul>
 * 导入/导出时通过 sourceId 字段关联，不直接转换 ID 类型。
 *
 * <p>由于两个枚举分别位于 developer-workstation 和 admin-center 模块，
 * 本工具类使用 String 进行映射以避免循环依赖。
 */
public final class StatusMapping {

    // Developer-workstation status constants
    public static final String DEV_DRAFT = "DRAFT";
    public static final String DEV_PUBLISHED = "PUBLISHED";
    public static final String DEV_ARCHIVED = "ARCHIVED";

    // Admin-center status constants
    public static final String ADMIN_DRAFT = "DRAFT";
    public static final String ADMIN_VALIDATED = "VALIDATED";
    public static final String ADMIN_DEPLOYED = "DEPLOYED";
    public static final String ADMIN_DEPRECATED = "DEPRECATED";

    private static final Map<String, String> DEV_TO_ADMIN = Map.of(
            DEV_DRAFT, ADMIN_DRAFT,
            DEV_PUBLISHED, ADMIN_VALIDATED,
            DEV_ARCHIVED, ADMIN_DEPRECATED
    );

    private static final Map<String, String> ADMIN_TO_DEV = Map.of(
            ADMIN_DRAFT, DEV_DRAFT,
            ADMIN_VALIDATED, DEV_PUBLISHED,
            ADMIN_DEPLOYED, DEV_PUBLISHED,
            ADMIN_DEPRECATED, DEV_ARCHIVED
    );

    private StatusMapping() {
        // Utility class — no instantiation
    }

    /**
     * 将 developer-workstation 状态映射为 admin-center 状态。
     *
     * @param developerStatus developer-workstation 状态字符串 (DRAFT, PUBLISHED, ARCHIVED)
     * @return admin-center 状态字符串 (DRAFT, VALIDATED, DEPRECATED)
     * @throws IllegalArgumentException 如果状态值无效
     */
    public static String toAdminStatus(String developerStatus) {
        String result = DEV_TO_ADMIN.get(developerStatus);
        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown developer-workstation status: " + developerStatus);
        }
        return result;
    }

    /**
     * 将 admin-center 状态映射为 developer-workstation 状态。
     *
     * <p>注意：DEPLOYED 在 developer-workstation 中无直接对应，映射为 PUBLISHED。
     *
     * @param adminStatus admin-center 状态字符串 (DRAFT, VALIDATED, DEPLOYED, DEPRECATED)
     * @return developer-workstation 状态字符串 (DRAFT, PUBLISHED, ARCHIVED)
     * @throws IllegalArgumentException 如果状态值无效
     */
    public static String toDeveloperStatus(String adminStatus) {
        String result = ADMIN_TO_DEV.get(adminStatus);
        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown admin-center status: " + adminStatus);
        }
        return result;
    }
}
