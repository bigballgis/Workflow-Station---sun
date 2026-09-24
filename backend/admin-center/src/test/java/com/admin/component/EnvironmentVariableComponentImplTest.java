package com.admin.component;

import com.admin.dto.EnvironmentVariableRequest;
import com.admin.entity.EnvironmentVariable;
import com.admin.enums.EnvironmentValueKind;
import com.admin.repository.EnvironmentVariableRepository;
import com.platform.common.exception.BusinessException;
import com.platform.security.vault.VaultSecretClient;
import com.platform.security.vault.VaultSecretNotFoundException;
import com.platform.security.vault.VaultSecretUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    void resolveVaultPassword_vaultUnavailable_throwsExternalService() {
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
                .thenThrow(new VaultSecretUnavailableException("down"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.resolveVaultPassword("email.qq.inbound"));
        assertEquals("EXTERNAL_SERVICE_ERROR", ex.getErrorCode().name());
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

    @Test
    void create_vaultWritesPasswordThenSaves() {
        EnvironmentVariableRequest request = vaultCreateRequest("email.qq.inbound", "workflow/email/qq", "secret");
        when(repository.existsByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(false);
        when(vaultSecretClient.secretExists("ame-hase-hermes/env-var/email.qq.inbound")).thenReturn(false);
        when(repository.save(any(EnvironmentVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        component.create(request, "user-1");

        verify(vaultSecretClient).writePassword("ame-hase-hermes/env-var/email.qq.inbound", "secret");
        ArgumentCaptor<EnvironmentVariable> captor = ArgumentCaptor.forClass(EnvironmentVariable.class);
        verify(repository).save(captor.capture());
        assertEquals("ame-hase-hermes/env-var/email.qq.inbound", captor.getValue().getVaultSecretPath());
    }

    @Test
    void create_duplicateCatalogKey_doesNotWriteVault() {
        EnvironmentVariableRequest request = vaultCreateRequest("email.qq.inbound", "workflow/email/qq", "secret");
        when(repository.existsByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> component.create(request, "user-1"));
        assertEquals("RESOURCE_ALREADY_EXISTS", ex.getErrorCode().name());
        assertEquals("Environment variable key already exists", ex.getMessage());
        verify(vaultSecretClient, never()).writePassword(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void create_existingVaultSecret_reusesWithoutWrite() {
        EnvironmentVariableRequest request = vaultCreateRequest("email.qq.inbound", "workflow/email/qq", "secret");
        when(repository.existsByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(false);
        when(vaultSecretClient.secretExists("ame-hase-hermes/env-var/email.qq.inbound")).thenReturn(true);
        when(repository.save(any(EnvironmentVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        component.create(request, "user-1");

        verify(vaultSecretClient, never()).writePassword(any(), any());
        ArgumentCaptor<EnvironmentVariable> captor = ArgumentCaptor.forClass(EnvironmentVariable.class);
        verify(repository).save(captor.capture());
        assertEquals("ame-hase-hermes/env-var/email.qq.inbound", captor.getValue().getVaultSecretPath());
    }

    @Test
    void create_existingVaultSecret_passwordOptional() {
        EnvironmentVariableRequest request = vaultCreateRequest("email.qq.inbound", "workflow/email/qq", null);
        when(repository.existsByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(false);
        when(vaultSecretClient.secretExists("ame-hase-hermes/env-var/email.qq.inbound")).thenReturn(true);
        when(repository.save(any(EnvironmentVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        component.create(request, "user-1");

        verify(vaultSecretClient, never()).writePassword(any(), any());
        verify(repository).save(any(EnvironmentVariable.class));
    }

    @Test
    void create_missingVaultSecret_requiresPassword() {
        EnvironmentVariableRequest request = vaultCreateRequest("email.qq.inbound", "workflow/email/qq", null);
        when(repository.existsByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(false);
        when(vaultSecretClient.secretExists("ame-hase-hermes/env-var/email.qq.inbound")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> component.create(request, "user-1"));
        assertEquals("VALIDATION_FIELD_REQUIRED", ex.getErrorCode().name());
        verify(vaultSecretClient, never()).writePassword(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void update_neverWritesVault() {
        EnvironmentVariable entity = EnvironmentVariable.builder()
                .id("1")
                .varKey("email.qq.inbound")
                .deployEnv("dev")
                .valueKind(EnvironmentValueKind.VAULT)
                .displayName("QQ inbound")
                .vaultSecretPath("workflow/email/qq")
                .build();
        EnvironmentVariableRequest request = vaultCreateRequest(
                "email.qq.inbound", "ignored/request/path", "new-secret");
        when(repository.findById("1")).thenReturn(Optional.of(entity));
        when(repository.findByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(Optional.of(entity));
        when(repository.save(any(EnvironmentVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        component.update("1", request, "user-1");

        verify(vaultSecretClient, never()).writePassword(any(), any());
        verify(vaultSecretClient, never()).secretExists(any());
        ArgumentCaptor<EnvironmentVariable> captor = ArgumentCaptor.forClass(EnvironmentVariable.class);
        verify(repository).save(captor.capture());
        assertEquals("workflow/email/qq", captor.getValue().getVaultSecretPath());
    }

    @Test
    void create_vaultWriteFailure_doesNotSave() {
        EnvironmentVariableRequest request = vaultCreateRequest("email.qq.inbound", "workflow/email/qq", "secret");
        when(repository.existsByVarKeyAndDeployEnv("email.qq.inbound", "dev")).thenReturn(false);
        when(vaultSecretClient.secretExists("ame-hase-hermes/env-var/email.qq.inbound")).thenReturn(false);
        doThrow(new VaultSecretUnavailableException("down"))
                .when(vaultSecretClient).writePassword("ame-hase-hermes/env-var/email.qq.inbound", "secret");

        BusinessException ex = assertThrows(BusinessException.class, () -> component.create(request, "user-1"));
        assertEquals("EXTERNAL_SERVICE_ERROR", ex.getErrorCode().name());
        verify(repository, never()).save(any());
    }

    private static EnvironmentVariableRequest vaultCreateRequest(String key, String path, String password) {
        EnvironmentVariableRequest request = new EnvironmentVariableRequest();
        request.setVarKey(key);
        request.setDisplayName("QQ inbound");
        request.setValueKind(EnvironmentValueKind.VAULT);
        request.setVaultSecretPath(path);
        request.setVaultPassword(password);
        return request;
    }
}
