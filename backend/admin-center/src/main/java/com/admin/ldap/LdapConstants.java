package com.admin.ldap;

/**
 * LDAP / AD 集成的共享常量。
 *
 * <p>集中存放跨多个 LDAP 类使用的魔法值，遵循「禁止魔法值」的代码质量标准。</p>
 */
public final class LdapConstants {

    private LdapConstants() {
        // 工具类，禁止实例化
    }

    /**
     * LDAP-only 用户的 password_hash 占位标记。
     *
     * <p>语义：当用户由 LDAP 同步/JIT 入库时，本地不保存可用密码。我们写入一个包含此标记的
     * 不可登录占位哈希，使得：
     * <ul>
     *   <li>{@code BCrypt} 永远无法匹配（占位串不是合法 bcrypt 哈希）；</li>
     *   <li>本地登录路径可显式识别「LDAP-only 用户」并拒绝本地密码登录，引导走 LDAP/SSO。</li>
     * </ul></p>
     */
    public static final String LDAP_ONLY_AUTH_PLACEHOLDER = "LDAP_ONLY_AUTH_PLACEHOLDER";

    /**
     * LDAP 同步/JIT 写库时使用的操作者标识（created_by / updated_by）。
     */
    public static final String LDAP_SYNC_ACTOR = "LDAP_SYNC_JOB";

    /**
     * 微软 AD 「分页结果」控制 OID（RFC 2696）。用于全量/增量拉取时分页，避免一次性加载超大结果集。
     */
    public static final String PAGED_RESULTS_CONTROL_OID = "1.2.840.113556.1.4.319";
}
