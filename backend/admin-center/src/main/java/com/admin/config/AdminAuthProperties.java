package com.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin Center 登录相关开关（前缀 {@code admin.auth.*}）。
 *
 * <p>{@link #developerBypassRoleCheck} 仅供本地开发：开启后跳过「必须具备 SYS_ADMIN/AUDITOR 才能进入
 * Admin Center」的角色校验，让任意 ACTIVE 用户可登录以便联调 LDAP/JIT。<b>默认关闭，生产严禁开启。</b></p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin.auth")
public class AdminAuthProperties {

    /** 开发跳过 Admin Center 角色校验（默认 false；仅本地）。 */
    private boolean developerBypassRoleCheck = false;
}
