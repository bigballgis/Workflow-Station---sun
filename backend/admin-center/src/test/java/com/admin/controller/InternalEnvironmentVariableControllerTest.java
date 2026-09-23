package com.admin.controller;

import com.admin.component.EnvironmentVariableComponent;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalEnvironmentVariableControllerTest {

    @Mock
    private EnvironmentVariableComponent environmentVariableComponent;

    private InternalEnvironmentVariableController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalEnvironmentVariableController(environmentVariableComponent, "svc-secret");
    }

    @Test
    void vaultPassword_unavailable_returns503() {
        when(environmentVariableComponent.resolveVaultPassword("email.qq.inbound"))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Vault is unavailable"));

        ResponseEntity<Map<String, String>> response =
                controller.vaultPassword("svc-secret", "email.qq.inbound");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "VAULT_UNAVAILABLE");
    }

    @Test
    void vaultPassword_missingSecret_returns404() {
        when(environmentVariableComponent.resolveVaultPassword("email.qq.inbound"))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Vault secret not found"));

        ResponseEntity<Map<String, String>> response =
                controller.vaultPassword("svc-secret", "email.qq.inbound");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "VAULT_SECRET_NOT_FOUND");
    }
}
