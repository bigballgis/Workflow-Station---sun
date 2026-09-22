package com.admin.environment;

import com.admin.dto.EnvironmentVariableRequest;
import com.admin.enums.EnvironmentValueKind;
import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentVariablePayloadsTest {

    @Test
    void resolvedText_usesCurrentWhenNonBlank() {
        assertEquals("live", EnvironmentVariablePayloads.resolvedText(" live ", "default"));
    }

    @Test
    void resolvedText_fallsBackToDefaultOnWhitespaceCurrent() {
        assertEquals("default", EnvironmentVariablePayloads.resolvedText("   ", "default"));
    }

    @Test
    void resolvedText_fallsBackToDefaultOnNullCurrent() {
        assertEquals("default", EnvironmentVariablePayloads.resolvedText(null, "default"));
    }

    @Test
    void validate_textRequiresDefault() {
        EnvironmentVariableRequest request = new EnvironmentVariableRequest();
        request.setVarKey("k");
        request.setDisplayName("n");
        request.setValueKind(EnvironmentValueKind.TEXT);
        request.setDefaultValue(" ");
        assertThrows(BusinessException.class, () -> EnvironmentVariablePayloads.validate(request));
    }

    @Test
    void validate_vaultRequiresPathAndRejectsTextValues() {
        EnvironmentVariableRequest request = new EnvironmentVariableRequest();
        request.setVarKey("k");
        request.setDisplayName("n");
        request.setValueKind(EnvironmentValueKind.VAULT);
        request.setVaultSecretPath("workflow/email/qq");
        request.setDefaultValue("x");
        assertThrows(BusinessException.class, () -> EnvironmentVariablePayloads.validate(request));
    }
}
