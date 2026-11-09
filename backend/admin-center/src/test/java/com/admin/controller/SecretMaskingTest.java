package com.admin.controller;

import com.admin.component.ConfigManagerComponent;
import com.admin.component.EnvironmentVariableComponent;
import com.admin.dto.EnvironmentVariableResponse;
import com.admin.entity.ConfigHistory;
import com.admin.entity.SystemConfig;
import com.admin.enums.EnvironmentValueKind;
import com.admin.security.AdminCaller;
import com.admin.security.AdminTeamScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vault paths and encrypted settings are SYS_ADMIN's alone (D-44, AC-14, AC-40): asserted on the serialized responses
 * of the read endpoints, for a Technical Lead, an Auditor and a System Administrator.
 */
class SecretMaskingTest {

    private static final String VAULT_PATH = "kv/hermes/smtp-password";
    private static final String SECRET = "s3cr3t-value";

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AdminTeamScope teamScope = mock(AdminTeamScope.class);
    private final EnvironmentVariableComponent variables = mock(EnvironmentVariableComponent.class);
    private final ConfigManagerComponent configs = mock(ConfigManagerComponent.class);
    private EnvironmentVariableController variableController;
    private ConfigController configController;

    @BeforeEach
    void setUp() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        variableController = new EnvironmentVariableController(variables, teamScope);
        configController = new ConfigController(configs, i18n, teamScope);

        EnvironmentVariableResponse vault = EnvironmentVariableResponse.builder().id("v1").varKey("SMTP_PASSWORD")
                .valueKind(EnvironmentValueKind.VAULT).vaultSecretPath(VAULT_PATH).vaultConfigured(true)
                .currentValue(SECRET).defaultValue(SECRET).build();
        EnvironmentVariableResponse text = EnvironmentVariableResponse.builder().id("v2").varKey("SMTP_HOST")
                .valueKind(EnvironmentValueKind.TEXT).currentValue("smtp.example.com").build();
        when(variables.list(any())).thenReturn(List.of(vault, text));
        when(variables.getById("v1")).thenReturn(vault);

        SystemConfig encrypted = SystemConfig.builder().id("c1").configKey("smtp.password").configValue(SECRET)
                .defaultValue(SECRET).encrypted(true).environment("DEV").build();
        SystemConfig plain = SystemConfig.builder().id("c2").configKey("smtp.host").configValue("smtp.example.com")
                .encrypted(false).environment("DEV").build();
        when(configs.getAllConfigs()).thenReturn(List.of(encrypted, plain));
        when(configs.getConfig("smtp.password")).thenReturn(encrypted);
        when(configs.getConfigValue("smtp.password")).thenReturn(SECRET);
        when(configs.getConfigsByEnvironment(anyString())).thenReturn(List.of(encrypted, plain));
        when(configs.getConfigHistory(any(), any())).thenReturn(new PageImpl<>(List.of(ConfigHistory.builder()
                .configKey("smtp.password").oldValue(SECRET).newValue(SECRET + "2").build())));
        when(configs.compareEnvironments("DEV", "PROD")).thenReturn(ConfigManagerComponent.ConfigDiffResult.builder()
                .differences(List.of(ConfigManagerComponent.ConfigDiffItem.builder().configKey("smtp.password")
                        .diffType("VALUE_DIFFERENT").sourceValue(SECRET).targetValue(SECRET + "2").build()))
                .totalDifferences(1).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TECH_LEAD", "AUDITOR"})
    void everyoneButASystemAdministratorGetsNoSecret(String role) throws Exception {
        when(teamScope.caller()).thenReturn(AdminCaller.ofUser("u1", List.of(role)));

        String listed = json.writeValueAsString(variableController.list(null).getBody());
        assertThat(listed).doesNotContain(VAULT_PATH).doesNotContain(SECRET)
                .contains("\"vaultConfigured\":true").contains("smtp.example.com");
        assertThat(json.writeValueAsString(variableController.get("v1").getBody())).doesNotContain(VAULT_PATH);

        String configJson = json.writeValueAsString(configController.getAllConfigs().getBody())
                + json.writeValueAsString(configController.getConfig("smtp.password").getBody())
                + json.writeValueAsString(configController.getConfigsByEnvironment("DEV").getBody())
                + json.writeValueAsString(configController.getConfigHistory("smtp.password", Pageable.unpaged())
                        .getBody().getContent())
                + json.writeValueAsString(configController.compareEnvironments("DEV", "PROD").getBody());
        assertThat(configJson).doesNotContain(SECRET).contains("smtp.password").contains("smtp.example.com");
        assertThatThrownBy(() -> configController.getConfigValue("smtp.password"))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** It answers with the current value of the config, so only a System Administrator may call it (D-44). */
    @ParameterizedTest
    @ValueSource(strings = {"TECH_LEAD", "AUDITOR"})
    void assessImpactIsRefusedToEveryoneButASystemAdministrator(String role) throws Exception {
        java.lang.reflect.Method assess = ConfigController.class.getMethod("assessConfigChange", String.class,
                String.class);
        com.admin.security.AdminEndpointPolicy policy =
                com.admin.security.AdminEndpointPolicy.resolve(assess, ConfigController.class, "POST");

        assertThat(policy.audience().admitsAnyOf(List.of(role))).isFalse();
        assertThat(policy.services()).isFalse();
        assertThat(policy.audience().admitsAnyOf(List.of("SYS_ADMIN"))).isTrue();
    }

    @org.junit.jupiter.api.Test
    void aSystemAdministratorSeesEverything() throws Exception {
        when(teamScope.caller()).thenReturn(AdminCaller.ofUser("admin", List.of("SYS_ADMIN")));

        assertThat(json.writeValueAsString(variableController.list(null).getBody())).contains(VAULT_PATH);
        assertThat(json.writeValueAsString(configController.getAllConfigs().getBody())).contains(SECRET);
        assertThat(configController.getConfigValue("smtp.password").getBody()).isEqualTo(SECRET);
        assertThat(json.writeValueAsString(configController.compareEnvironments("DEV", "PROD").getBody()))
                .contains(SECRET);
    }
}
