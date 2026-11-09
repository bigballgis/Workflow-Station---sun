package com.admin.audit;

import com.admin.component.AdminAuditListQueryComponent;
import com.admin.component.SecurityAuditComponent;
import com.admin.controller.SecurityAuditController;
import com.admin.entity.AuditLog;
import com.admin.enums.AuditAction;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.SecurityPolicyRepository;
import com.admin.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No secret reaches the Admin operation audit, which every Technical Lead and Auditor reads (D-21): snapshots are
 * masked when written, and rows written before masking are masked when read, without changing the stored row.
 */
class AuditSecretsTest {

    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void secretsAreMaskedAtAnyDepthAndOtherValuesKept() {
        String masked = AuditSecrets.maskJson(mapper, "{\"username\":\"ann\",\"passwordHash\":\"" + HASH + "\","
                + "\"members\":[{\"user\":{\"password\":\"p\",\"email\":\"a@x\"}}],\"token\":null}");

        assertThat(masked).doesNotContain(HASH).doesNotContain("\"p\"")
                .contains("\"username\":\"ann\"").contains("\"email\":\"a@x\"")
                .contains("\"passwordHash\":\"***\"").contains("\"token\":null");
    }

    @Test
    void anEncryptedConfigLosesItsValueAndAPlainOneKeepsIt() {
        assertThat(AuditSecrets.maskJson(mapper,
                "{\"configKey\":\"smtp.password\",\"encrypted\":true,\"configValue\":\"s3cr3t\",\"defaultValue\":\"d\"}"))
                .doesNotContain("s3cr3t").doesNotContain("\"d\"");
        assertThat(AuditSecrets.maskJson(mapper, "{\"configKey\":\"smtp.host\",\"encrypted\":false,"
                + "\"configValue\":\"smtp.example.com\"}")).contains("smtp.example.com");
    }

    @Test
    void textThatIsNotJsonIsLeftAsItIs() {
        assertThat(AuditSecrets.maskJson(mapper, "Added user ann to team A")).isEqualTo("Added user ann to team A");
        assertThat(AuditSecrets.maskJson(mapper, null)).isNull();
    }

    @Test
    void textThatOnlyStartsWithAJsonLiteralIsLeftAsItIs() {
        assertThat(AuditSecrets.maskJson(mapper, "3 users created")).isEqualTo("3 users created");
        assertThat(AuditSecrets.maskJson(mapper, "true story")).isEqualTo("true story");
        assertThat(AuditSecrets.maskJson(mapper, "\"quoted\" then text")).isEqualTo("\"quoted\" then text");
        assertThat(AuditSecrets.maskJson(mapper, "[1] item")).isEqualTo("[1] item");
        assertThat(AuditSecrets.maskJson(mapper, "{\"token\":\"t\"} and more")).isEqualTo("{\"token\":\"t\"} and more");
    }

    @Test
    void theSingleWriterStoresNoSecret() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        SecurityAuditComponent writer = new SecurityAuditComponent(mock(SecurityPolicyRepository.class), repository,
                mock(UserRepository.class), mapper);

        writer.recordAudit(SecurityAuditComponent.AuditLogRequest.builder()
                .action(AuditAction.DELETE).resourceType("USER").resourceId("u1").userId("admin")
                .oldValue("{\"id\":\"u1\",\"passwordHash\":\"" + HASH + "\"}")
                .newValue("{\"apiKey\":\"k-123\"}")
                .success(true).build());

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOldValue()).doesNotContain(HASH).contains("\"id\":\"u1\"");
        assertThat(saved.getValue().getNewValue()).doesNotContain("k-123");
    }

    @Test
    void aRowWrittenBeforeMaskingIsMaskedForReadersAndLeftUnchangedInStore() {
        AuditLog legacy = AuditLog.builder().id("a1").action(AuditAction.DELETE).resourceType("USER")
                .oldValue("{\"id\":\"u1\",\"passwordHash\":\"" + HASH + "\"}").success(true).build();
        AdminAuditListQueryComponent rows = mock(AdminAuditListQueryComponent.class);
        when(rows.findById("a1")).thenReturn(Optional.of(legacy));
        SecurityAuditComponent component = mock(SecurityAuditComponent.class);
        when(component.getAuditLogsByUser(any(), any())).thenReturn(new PageImpl<>(List.of(legacy)));
        when(component.getAuditLogsByResource(any(), any(), any())).thenReturn(new PageImpl<>(List.of(legacy)));
        when(component.queryAuditLogs(any(), any())).thenReturn(new PageImpl<>(List.of(legacy)));
        SecurityAuditController controller = new SecurityAuditController(component, rows, mock(I18nService.class),
                mapper);

        assertThat(controller.getAuditLog("a1").getBody().getOldValue()).doesNotContain(HASH);
        assertThat(controller.getAuditLogsByUser("u1", Pageable.unpaged()).getBody().getContent().get(0)
                .getOldValue()).doesNotContain(HASH);
        assertThat(controller.getAuditLogsByResource("USER", "u1", Pageable.unpaged()).getBody().getContent().get(0)
                .getOldValue()).doesNotContain(HASH);
        assertThat(controller.queryAuditLogs(new SecurityAuditController.AuditQueryRequestDto(), Pageable.ofSize(20))
                .getBody().getContent().get(0).getOldValue()).doesNotContain(HASH);
        assertThat(legacy.getOldValue()).as("the stored row is not changed").contains(HASH);
    }
}
