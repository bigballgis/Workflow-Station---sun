package com.admin.ldap;

import com.platform.security.model.UserStatus;
import lombok.Builder;
import lombok.Data;

/**
 * 由 LDAP 属性映射出的「平台用户」中间数据（落库前的规整结果）。
 *
 * <p>字段对应《ldap和本项目的字段映射表》中的 {@code sys_users} 列。落库由
 * {@code LdapUserSyncService.upsertUser} 负责，本对象只承载映射结果，不含持久化逻辑。</p>
 */
@Data
@Builder
public class LdapUserData {

    /** sys_users.id —— 来自 employeeID（权威主键）。 */
    private String id;

    /** 登录名：uid → samAccountName → cn → employeeID，统一 trim。 */
    private String username;

    private String email;

    private String displayName;

    private String fullName;

    private String phone;

    /** 与 id 一致（employeeID）。 */
    private String employeeId;

    private String position;

    private String entityManagerId;

    private String functionManagerId;

    /** 由 lockoutTime 等推导：锁定→LOCKED，否则 ACTIVE。 */
    private UserStatus status;

    /** LDAP jpegPhoto 的 Base64 编码。为 null 表示目录中无照片。 */
    private String photoBase64;
}
