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
        if (request.getValueKind() == EnvironmentValueKind.TEXT) {
            if (!StringUtils.hasText(request.getDefaultValue())) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                        "TEXT environment variables require defaultValue");
            }
            if (StringUtils.hasText(request.getVaultSecretPath())) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                        "TEXT environment variables must not set vaultSecretPath");
            }
            return;
        }
        if (request.getValueKind() == EnvironmentValueKind.VAULT) {
            if (!StringUtils.hasText(request.getVaultSecretPath())) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                        "VAULT environment variables require vaultSecretPath");
            }
            if (StringUtils.hasText(request.getDefaultValue()) || StringUtils.hasText(request.getCurrentValue())) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                        "VAULT environment variables must not set current or default values");
            }
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Unknown environment value kind");
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
}
