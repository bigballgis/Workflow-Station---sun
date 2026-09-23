package com.admin.environment;

import com.admin.dto.EnvironmentVariableRequest;
import com.admin.enums.EnvironmentValueKind;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import org.springframework.util.StringUtils;

public final class EnvironmentVariablePayloads {

    private EnvironmentVariablePayloads() {
    }

    public static void validate(EnvironmentVariableRequest request) {
        validate(request, false);
    }

    public static void validate(EnvironmentVariableRequest request, boolean requireVaultPassword) {
        if (request.getValueKind() == EnvironmentValueKind.TEXT) {
            validateText(request);
            return;
        }
        if (request.getValueKind() == EnvironmentValueKind.VAULT) {
            validateVault(request, requireVaultPassword);
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Unknown environment value kind");
    }

    public static String vaultPath(EnvironmentVariableRequest request) {
        if (StringUtils.hasText(request.getVaultSecretPath())) {
            return request.getVaultSecretPath().trim();
        }
        return request.getVarKey().trim();
    }

    public static String resolvedText(String currentValue, String defaultValue) {
        if (StringUtils.hasText(currentValue)) {
            return currentValue.trim();
        }
        if (!StringUtils.hasText(defaultValue)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "TEXT environment variable has no defaultValue");
        }
        return defaultValue.trim();
    }

    private static void validateText(EnvironmentVariableRequest request) {
        if (!StringUtils.hasText(request.getDefaultValue())) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "TEXT environment variables require defaultValue");
        }
        if (StringUtils.hasText(request.getVaultSecretPath())
                || StringUtils.hasText(request.getVaultPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "TEXT environment variables must not set vaultSecretPath or vaultPassword");
        }
    }

    private static void validateVault(EnvironmentVariableRequest request, boolean requireVaultPassword) {
        if (!StringUtils.hasText(vaultPath(request))) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "VAULT environment variables require a key or vaultSecretPath");
        }
        if (StringUtils.hasText(request.getDefaultValue()) || StringUtils.hasText(request.getCurrentValue())) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "VAULT environment variables must not set current or default values");
        }
        if (requireVaultPassword && !StringUtils.hasText(request.getVaultPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "VAULT environment variables require vaultPassword");
        }
    }
}
