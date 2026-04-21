package com.admin.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA 转换器：向后兼容旧 DB 中遗留的细粒度 AuditAction 字符串值，
 * 将其自动归并到 4 大类（CREATE / UPDATE / DELETE / QUERY）。
 */
@Converter
public class AuditActionConverter implements AttributeConverter<AuditAction, String> {

    private static final Map<String, AuditAction> LEGACY = new HashMap<>();

    static {
        List.of("USER_CREATED", "ROLE_CREATED", "DATA_CREATED", "CONFIG_CREATED",
                "DATA_IMPORTED", "BACKUP_CREATED")
                .forEach(s -> LEGACY.put(s, AuditAction.CREATE));

        List.of("USER_UPDATED", "USER_LOCKED", "USER_UNLOCKED",
                "PASSWORD_CHANGED", "PASSWORD_RESET",
                "ROLE_UPDATED", "DATA_UPDATED", "CONFIG_UPDATED",
                "PERMISSION_GRANTED", "PERMISSION_REVOKED",
                "ROLE_ASSIGNED", "ROLE_UNASSIGNED",
                "USER_LOGIN", "USER_LOGOUT", "USER_LOGIN_FAILED",
                "DATA_EXPORTED", "SYSTEM_STARTUP", "SYSTEM_SHUTDOWN", "BACKUP_RESTORED")
                .forEach(s -> LEGACY.put(s, AuditAction.UPDATE));

        List.of("USER_DELETED", "ROLE_DELETED", "DATA_DELETED", "CONFIG_DELETED")
                .forEach(s -> LEGACY.put(s, AuditAction.DELETE));

        List.of("DATA_QUERIED")
                .forEach(s -> LEGACY.put(s, AuditAction.QUERY));
    }

    @Override
    public String convertToDatabaseColumn(AuditAction attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public AuditAction convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return AuditAction.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            return LEGACY.getOrDefault(dbData, AuditAction.UPDATE);
        }
    }
}
