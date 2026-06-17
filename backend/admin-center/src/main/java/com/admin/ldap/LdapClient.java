package com.admin.ldap;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 基于 JDK 原生 JNDI 的 LDAP/AD 访问封装（仅 {@code ldap.enabled=true} 时创建 Bean）。
 *
 * <p>职责：服务账号连接、用户 DN 搜索、账号口令 bind 校验、分页全量/增量拉取、按 DN 取属性。
 * 不含落库与业务编排（由 {@code LdapUserSyncService} / {@code LdapAuthenticator} 负责）。</p>
 *
 * <p>安全：日志不打印明文密码；DN/username 等敏感信息仅在 DEBUG 级输出（生产 INFO 不泄露）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "true")
public class LdapClient {

    private static final String CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String CONNECT_TIMEOUT_KEY = "com.sun.jndi.ldap.connect.timeout";
    private static final String READ_TIMEOUT_KEY = "com.sun.jndi.ldap.read.timeout";

    private final LdapProperties props;

    /**
     * 启动时配置自定义 LDAPS truststore（含 HSBC CA 根证书）。
     * 设置 JVM 级 trustStore 系统属性；未配置则沿用 JVM 默认信任库。
     */
    @PostConstruct
    void configureTrustStore() {
        if (StringUtils.hasText(props.getKeystorePath())) {
            System.setProperty("javax.net.ssl.trustStore", props.getKeystorePath());
            if (StringUtils.hasText(props.getKeystorePassword())) {
                System.setProperty("javax.net.ssl.trustStorePassword", props.getKeystorePassword());
            }
            log.info("LDAP custom truststore configured");
        }
    }

    /**
     * 按 uid → samAccountName → cn → employeeID 顺序搜索用户 DN。
     *
     * @return 用户完整 DN；未命中返回 empty
     * @throws NamingException 连接/搜索异常（调用方据此决定是否回退本地）
     */
    public Optional<String> findUserDn(String username) throws NamingException {
        String escaped = escapeFilterValue(username);
        LdapProperties.Attributes a = props.getAttributes();
        List<String> attrCandidates = List.of(
                a.getUid(), a.getSamAccountName(), a.getCn(), a.getEmployeeId());

        DirContext ctx = openServiceContext();
        try {
            for (String attr : attrCandidates) {
                String dn = searchSingleDn(ctx, "(" + attr + "=" + escaped + ")");
                if (dn != null) {
                    log.debug("Resolved user DN via attribute {}", attr);
                    return Optional.of(dn);
                }
            }
            return Optional.empty();
        } finally {
            closeQuietly(ctx);
        }
    }

    /**
     * 用用户 DN + 明文密码做 LDAP simple bind 校验。
     *
     * @return {@code true}=口令正确；{@code false}=口令错误（确定性失败，不应回退本地）
     * @throws NamingException 连接异常（非认证失败），调用方可回退本地
     */
    public boolean bindAuthenticate(String userDn, String password) throws NamingException {
        if (!StringUtils.hasText(userDn) || !StringUtils.hasText(password)) {
            return false;
        }
        Hashtable<String, Object> env = baseEnv(userDn, password);
        DirContext userCtx = null;
        try {
            userCtx = new InitialLdapContext(env, null);
            return true;
        } catch (javax.naming.AuthenticationException e) {
            // 口令错误：确定性失败
            return false;
        } finally {
            closeQuietly(userCtx);
        }
    }

    /** 全量拉取用户（分页）。 */
    public List<Map<String, String>> fetchAllUsers() throws NamingException {
        return pagedSearch(props.getUserSearchFilter());
    }

    /**
     * 增量拉取：在基础用户过滤上叠加额外条件（如 whenChanged 水位）。
     *
     * @param extraFilter 形如 {@code (whenChanged>=20260101000000.0Z)}；为空时等价全量
     */
    public List<Map<String, String>> fetchUsersWithFilter(String extraFilter) throws NamingException {
        String base = props.getUserSearchFilter();
        String combined = StringUtils.hasText(extraFilter) ? "(&" + base + extraFilter + ")" : base;
        return pagedSearch(combined);
    }

    /** 按 DN 取单用户属性（用于登录 JIT）。 */
    public Optional<Map<String, String>> getUserAttributes(String userDn) throws NamingException {
        DirContext ctx = openServiceContext();
        try {
            Attributes attrs = ctx.getAttributes(userDn, retrieveAttributeNames());
            return Optional.of(flatten(attrs));
        } finally {
            closeQuietly(ctx);
        }
    }

    // ==================== 内部实现 ====================

    /** 在 baseDn 下做 SUBTREE 搜索，返回首个命中 DN。 */
    private String searchSingleDn(DirContext ctx, String filter) throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[0]);
        controls.setCountLimit(1);
        NamingEnumeration<SearchResult> results = ctx.search(props.getBaseDn(), filter, controls);
        try {
            if (results.hasMore()) {
                return results.next().getNameInNamespace();
            }
            return null;
        } finally {
            closeQuietly(results);
        }
    }

    /** RFC 2696 分页搜索，逐页累积；受 {@code maxEntries} 截断保护。 */
    private List<Map<String, String>> pagedSearch(String filter) throws NamingException {
        List<Map<String, String>> out = new ArrayList<>();
        LdapContext ctx = (LdapContext) openServiceContext();
        try {
            ctx.setRequestControls(new Control[]{
                    new PagedResultsControl(props.getPageSize(), Control.NONCRITICAL)});
            byte[] cookie = null;
            do {
                cookie = collectOnePage(ctx, filter, out);
                ctx.setRequestControls(new Control[]{
                        new PagedResultsControl(props.getPageSize(), cookie, Control.NONCRITICAL)});
            } while (cookie != null && cookie.length != 0 && !reachedLimit(out));
            return out;
        } catch (java.io.IOException e) {
            throw new NamingException("LDAP paged control error: " + e.getMessage());
        } finally {
            closeQuietly(ctx);
        }
    }

    /** 收集一页结果到 out，返回下一页 cookie。 */
    private byte[] collectOnePage(LdapContext ctx, String filter, List<Map<String, String>> out)
            throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(retrieveAttributeNames());
        NamingEnumeration<SearchResult> page = ctx.search(props.getBaseDn(), filter, controls);
        try {
            while (page.hasMore() && !reachedLimit(out)) {
                out.add(flatten(page.next().getAttributes()));
            }
        } finally {
            closeQuietly(page);
        }
        return extractCookie(ctx.getResponseControls());
    }

    private boolean reachedLimit(List<Map<String, String>> out) {
        return props.getMaxEntries() > 0 && out.size() >= props.getMaxEntries();
    }

    private byte[] extractCookie(Control[] controls) {
        if (controls == null) {
            return null;
        }
        for (Control c : controls) {
            if (c instanceof PagedResultsResponseControl prrc) {
                return prrc.getCookie();
            }
        }
        return null;
    }

    /** Attributes → 大小写不敏感的 {@code 属性名 -> 首值字符串}。 */
    private Map<String, String> flatten(Attributes attrs) throws NamingException {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (attrs == null) {
            return map;
        }
        NamingEnumeration<? extends Attribute> all = attrs.getAll();
        try {
            while (all.hasMore()) {
                Attribute attr = all.next();
                Object value = attr.size() > 0 ? attr.get(0) : null;
                if (value != null) {
                    map.put(attr.getID(), String.valueOf(value));
                }
            }
        } finally {
            closeQuietly(all);
        }
        return map;
    }

    private String[] retrieveAttributeNames() {
        List<String> attrs = props.getRetrieveAttributes();
        if (attrs == null || attrs.isEmpty()) {
            return null; // null = 返回全部属性
        }
        return attrs.toArray(new String[0]);
    }

    private DirContext openServiceContext() throws NamingException {
        return new InitialLdapContext(baseEnv(props.getBindDn(), props.getBindPassword()), null);
    }

    private Hashtable<String, Object> baseEnv(String principal, String credentials) {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY);
        env.put(Context.PROVIDER_URL, props.getProviderUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal == null ? "" : principal);
        env.put(Context.SECURITY_CREDENTIALS, credentials == null ? "" : credentials);
        env.put(CONNECT_TIMEOUT_KEY, String.valueOf(props.getConnectTimeoutMs()));
        env.put(READ_TIMEOUT_KEY, String.valueOf(props.getReadTimeoutMs()));
        if (props.isTls()) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }
        return env;
    }

    /** 转义 LDAP 过滤值，防注入：\ * ( ) NUL / 。 */
    static String escapeFilterValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\5c");
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case '\u0000' -> sb.append("\\00");
                case '/' -> sb.append("\\2f");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private void closeQuietly(NamingEnumeration<?> e) {
        if (e != null) {
            try {
                e.close();
            } catch (NamingException ignored) {
                // 关闭枚举失败不影响结果
            }
        }
    }

    private void closeQuietly(Context ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ignored) {
                // 关闭上下文失败不影响结果
            }
        }
    }
}
