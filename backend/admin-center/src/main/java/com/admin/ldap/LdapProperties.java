package com.admin.ldap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LDAP / AD 连接与同步配置（前缀 {@code ldap.*}）。
 *
 * <p>所有真实连接参数与密钥均通过 {@code application.yml} 的 {@code ${ENV}} 占位注入，
 * 严禁在代码中硬编码（见 security-guard 规则）。属性名（如 employeeID、uid 等）做成可配置，
 * 目的有二：①避免散落魔法字符串；②便于在本地用标准 schema 的 OpenLDAP 做端到端验证
 * （通过 env 覆盖为 inetOrgPerson 兼容属性，如 employeeNumber）。</p>
 *
 * <p>权威源语义：LDAP 为用户画像权威源——定期同步 + 登录时 JIT 实时回写 {@code sys_users}。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ldap")
public class LdapProperties {

    /** 是否启用 LDAP。关闭时所有 LDAP Bean 不创建，登录回退本地账号密码。 */
    private boolean enabled = false;

    /** LDAP 服务地址，例如 {@code ldaps://host:3269}。 */
    private String providerUrl = "";

    /** 用户/查询的搜索基准 DN。 */
    private String baseDn = "";

    /** 服务账号 DN（用于 service bind 后做搜索）。 */
    private String bindDn = "";

    /** 服务账号密码（仅从环境注入）。 */
    private String bindPassword = "";

    /** 是否使用 SSL/TLS（ldaps）。 */
    private boolean tls = true;

    /**
     * 显式允许明文 LDAP（{@code ldap://} 且 {@code tls=false}）。默认 {@code false}：
     * 非加密传输会在启动时 fail-fast，防止生产误配导致用户口令明文过网。
     * 仅供本地 mock OpenLDAP 联调时经环境变量开启（见 docker-compose.dev.yml）。
     */
    private boolean allowInsecure = false;

    /** 连接超时（毫秒）。 */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒）。 */
    private int readTimeoutMs = 10000;

    /** 分页拉取每页条数。 */
    private int pageSize = 500;

    /** 全量拉取上限；{@code <=0} 表示不限制。 */
    private int maxEntries = 0;

    /** 用户对象过滤（不含具体账号占位），例如 {@code (|(objectclass=userproxy)(objectclass=user))}。 */
    private String userSearchFilter = "(|(objectclass=userproxy)(objectclass=user))";

    /** 返回属性白名单（逗号分隔字符串映射为列表）。空表示返回全部。 */
    private List<String> retrieveAttributes = new ArrayList<>();

    /** 是否启用定时同步（与 {@link #enabled} 同时为真才会运行）。 */
    private boolean syncEnabled = false;

    /** 定时同步 cron 表达式。 */
    private String syncCron = "0 0 */2 * * ?";

    /** 自定义 LDAPS truststore 路径（含 HSBC CA 根证书）。空则用 JVM 默认。 */
    private String keystorePath = "";

    /** truststore 密码。 */
    private String keystorePassword = "";

    /** LDAP 属性名映射（默认 HSBC AD 命名；本地 mock 可经 env 覆盖为标准 schema 属性名）。 */
    private Attributes attributes = new Attributes();

    /** AD 组同步的目录参数。 */
    private GroupSync groupSync = new GroupSync();

    /**
     * 字段映射表（见《ldap和本项目的字段映射表》）对应的 LDAP 属性名。
     * 多来源字段保留主/备属性，按非空优先取值。
     */
    @Data
    public static class Attributes {
        /** sys_users.id 与 employee_id 来源；同时是 username 的最终回退。 */
        private String employeeId = "employeeID";
        /** username 首选。 */
        private String uid = "uid";
        /** username 次选（HSBC AD samAccountName）。 */
        private String samAccountName = "hsbc-ad-SAMAccountName";
        /** username 第三选 + displayName 拼装回退。 */
        private String cn = "cn";
        /** display_name / full_name 首选。 */
        private String displayName = "displayName";
        /** display_name 拼装来源（名）。 */
        private String givenName = "givenName";
        /** display_name 拼装来源（姓）。 */
        private String sn = "sn";
        /** email。 */
        private String mail = "mail";
        /** phone 首选。 */
        private String telephoneNumber = "telephoneNumber";
        /** phone 次选（HSBC 内部号码）。 */
        private String intlTelNumber = "hsbc-ad-IntlTelNumber";
        /** position 首选。 */
        private String title = "title";
        /** position 次选。 */
        private String workRole = "hsbc-ad-WorkRole";
         /** location */
        private String postalAddress = "postalAddress";
        /** entity manager（可为姓名或工号）首选。 */
        private String lineManagerName = "hsbc-ad-LineManagerName";
        /** function manager（可为姓名或工号）首选。 */
        private String authManagerName = "hsbc-ad-AuthManagerName";
        /** entity/function manager id 次选。 */
        private String lineManagerId = "hsbc-ad-LineManagerID";
        /** manager id 次选。 */
        private String managerEmpId = "hsbc-ad-managerEmpId";
        /** manager id 第三选。 */
        private String authManagerEmpId = "hsbc-ad-AuthManagerEmpId";
        /** 账号锁定时间（推导 status）。 */
        private String lockoutTime = "lockoutTime";
        /** 增量同步水位属性（AD whenChanged）。 */
        private String whenChanged = "whenChanged";
        /** LDAP/AD 二进制头像属性。 */
        private String jpegPhoto = "jpegPhoto";
    }

    /**
     * AD 组同步的目录参数。哪个 AD 组同步进哪个虚拟组 / 名单，配置在虚拟组上，不在这里。
     */
    @Data
    public static class GroupSync {
        /** 组搜索基准 DN。 */
        private String searchBaseDn = "";
        /** 用户 upsert 批大小。 */
        private int batchSize = 20;
    }
}
