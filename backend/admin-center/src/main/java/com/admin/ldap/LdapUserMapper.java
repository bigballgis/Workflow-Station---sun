package com.admin.ldap;

import com.platform.security.model.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 将一条 LDAP 用户属性（已扁平化为 {@code 属性名 -> 字符串值}）映射为 {@link LdapUserData}。
 *
 * <p>映射规则严格遵循《ldap和本项目的字段映射表》。属性名来自 {@link LdapProperties.Attributes}，
 * 默认 HSBC AD 命名，可经配置覆盖以适配本地 mock 目录。</p>
 */
@Component
@RequiredArgsConstructor
public class LdapUserMapper {

    private final LdapProperties ldapProperties;

    /**
     * 映射单条用户。
     *
     * @param attrs 扁平化属性表（不区分大小写由调用方保证）
     * @return 映射结果；当缺少 employeeID（无法确定主键）时返回 {@link Optional#empty()}（跳过该条）。
     */
    public Optional<LdapUserData> mapToUser(Map<String, String> attrs) {
        LdapProperties.Attributes names = ldapProperties.getAttributes();
        String employeeId = trimToNull(attrs.get(names.getEmployeeId()));
        if (employeeId == null) {
            // 无 employeeID 无法确定权威主键，按规则跳过
            return Optional.empty();
        }

        String username = firstNonBlank(
                attrs.get(names.getUid()),
                attrs.get(names.getSamAccountName()),
                attrs.get(names.getCn()),
                employeeId);

        String displayName = resolveDisplayName(attrs, names);
        String managerId = firstNonBlank(
                attrs.get(names.getLineManagerId()),
                attrs.get(names.getManagerEmpId()),
                attrs.get(names.getAuthManagerEmpId()));

        return Optional.of(LdapUserData.builder()
                .id(employeeId)
                .employeeId(employeeId)
                .username(trimToNull(username))
                .email(trimToNull(attrs.get(names.getMail())))
                .displayName(displayName)
                .fullName(displayName)
                .phone(normalizePhone(firstNonBlank(
                        attrs.get(names.getTelephoneNumber()),
                        attrs.get(names.getIntlTelNumber()))))
                .position(firstNonBlank(attrs.get(names.getTitle()), attrs.get(names.getWorkRole())))
                .entityManagerId(managerId)
                // 初期 function manager 与 entity manager 同值（见映射表）
                .functionManagerId(managerId)
                .status(resolveStatus(attrs.get(names.getLockoutTime())))
                .build());
    }

    /** displayName → (givenName + ' ' + sn)，去重空格。 */
    private String resolveDisplayName(Map<String, String> attrs, LdapProperties.Attributes names) {
        String display = trimToNull(attrs.get(names.getDisplayName()));
        if (display != null) {
            return display;
        }
        String given = trimToNull(attrs.get(names.getGivenName()));
        String surname = trimToNull(attrs.get(names.getSn()));
        String joined = ((given == null ? "" : given) + " " + (surname == null ? "" : surname)).trim();
        return joined.isEmpty() ? null : joined.replaceAll("\\s+", " ");
    }

    /** lockoutTime 非空且不等于 "0" → LOCKED，否则 ACTIVE。 */
    private UserStatus resolveStatus(String lockoutTime) {
        String v = trimToNull(lockoutTime);
        if (v != null && !"0".equals(v)) {
            return UserStatus.LOCKED;
        }
        return UserStatus.ACTIVE;
    }

    /** 去空格/横线/括号；空则 null。 */
    private String normalizePhone(String phone) {
        String v = trimToNull(phone);
        if (v == null) {
            return null;
        }
        String normalized = v.replaceAll("[\\s\\-()]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            String t = trimToNull(v);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
