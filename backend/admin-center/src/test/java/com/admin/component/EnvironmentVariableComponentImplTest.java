package com.admin.component;

import com.admin.entity.EnvironmentVariable;
import com.admin.enums.EnvironmentValueKind;
import com.admin.repository.EnvironmentVariableRepository;
import com.platform.common.exception.BusinessException;
import com.platform.security.vault.VaultSecretClient;
import com.platform.security.vault.VaultSecretNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentVariableComponentImplTest {

    @Mock
    private EnvironmentVariableRepository repository;

    @Mock
    private VaultSecretClient vaultSecretClient;

    @Mock
    private Environment springEnvironment;

    @InjectMocks
    private EnvironmentVariableComponentImpl component;

    @BeforeEach
    void profiles() {
        when(springEnvironment.getActiveProfiles()).thenReturn(new String[] {"docker"});
    }

    @Test
    void resolveSecretOrText_textWhitespaceCurrentFallsBackToDefault() {
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id("1")
                .varKey("smtp.from")
                .deployEnv("dev")
                .valueKind(EnvironmentValueKind.TEXT)
                .displayName("From")
                .currentValue("  ")
                .defaultValue("noreply@example.com")
                .build();
        when(repository.findByVarKeyAndDeployEnv("smtp.from", "dev")).thenReturn(Optional.of(entity));

        assertEquals("noreply@example.com", component.resolveSecretOrText("smtp.from"));
    }

    @Test
    void resolveVaultPassword_rejectsTextKind() {
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id("1")
                .varKey("smtp.from")
                .deployEnv("dev")
                .valueKind(EnvironmentValueKind.TEXT)
                .displayName("From")
                .defaultValue("x")
                .build();
        when(repository.findByVarKeyAndDeployEnv("smtp.from", "dev")).thenReturn(Optional.of(entity));

        assertThrows(BusinessException.class, () -> component.resolveVaultPassword("smtp.from"));
    }

    @Test
    void resolveVaultPassword_vault404_throwsBusinessNotFound() {
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id("1")
                .varKey("email.qq.inbound")
                .deployEnv("dev")
                .valueKind(EnvironmentValueKind.VAULT)
                .displayName("QQ inbound")
                .vaultSecretPath("workflow/email/qq")
                .build();
        when(repository.findByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(Optional.of(entity));
        when(vaultSecretClient.readPassword("workflow/email/qq"))
                .thenThrow(new VaultSecretNotFoundException("missing"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.resolveVaultPassword("email.qq.inbound"));
        assertEquals("RESOURCE_NOT_FOUND", ex.getErrorCode().name());
    }

    @Test
    void delete_rejectsOtherDeployEnv() {
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id("uat-row")
                .varKey("email.qq.inbound")
                .deployEnv("uat")
                .valueKind(EnvironmentValueKind.VAULT)
                .displayName("QQ inbound")
                .vaultSecretPath("workflow/email/qq")
                .build();
        when(repository.findById("uat-row")).thenReturn(Optional.of(entity));

        BusinessException ex = assertThrows(BusinessException.class, () -> component.delete("uat-row"));
        assertEquals("OPERATION_NOT_ALLOWED", ex.getErrorCode().name());
        verify(repository, never()).delete(entity);
    }

    @Test
    void getById_rejectsOtherDeployEnv() {
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id("uat-row")
                .varKey("email.qq.inbound")
                .deployEnv("uat")
                .valueKind(EnvironmentValueKind.VAULT)
                .displayName("QQ inbound")
                .vaultSecretPath("workflow/email/qq")
                .build();
        when(repository.findById("uat-row")).thenReturn(Optional.of(entity));

        BusinessException ex = assertThrows(BusinessException.class, () -> component.getById("uat-row"));
        assertEquals("OPERATION_NOT_ALLOWED", ex.getErrorCode().name());
    }
}
