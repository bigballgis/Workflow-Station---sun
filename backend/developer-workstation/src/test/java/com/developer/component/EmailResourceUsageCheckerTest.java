package com.developer.component;

import com.developer.entity.ActionDefinition;
import com.developer.entity.EmailConnection;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.EmailMonitorRuleRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailResourceUsageCheckerTest {

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @Mock
    private EmailMonitorRuleRepository emailMonitorRuleRepository;

    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private EmailResourceUsageChecker checker;

    @Test
    void assertConnectionUnused_monitorBinding_throws() {
        EmailConnection connection = connection("uid-a", 10L);
        when(emailMonitorRuleRepository.existsByFunctionUnitIdAndConnectionUid(1L, "uid-a")).thenReturn(true);
        when(i18nService.getMessage("email.connection.in_use")).thenReturn("Connection is in use");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> checker.assertConnectionUnused(1L, connection));

        assertEquals("CONFLICT_CONNECTION_IN_USE", ex.getErrorCode());
        assertEquals("Connection is in use", ex.getMessage());
    }

    @Test
    void assertConnectionUnused_actionPostEmail_throws() {
        EmailConnection connection = connection("uid-a", 10L);
        when(emailMonitorRuleRepository.existsByFunctionUnitIdAndConnectionUid(1L, "uid-a")).thenReturn(false);
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of(
                ActionDefinition.builder()
                        .configJson(Map.of("postEmail", Map.of("connectionId", "uid-a")))
                        .build()));
        when(i18nService.getMessage("email.connection.in_use")).thenReturn("Connection is in use");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> checker.assertConnectionUnused(1L, connection));

        assertEquals("CONFLICT_CONNECTION_IN_USE", ex.getErrorCode());
    }

    @Test
    void assertConnectionUnused_sendTaskBpmn_throws() {
        EmailConnection connection = connection("uid-a", 10L);
        when(emailMonitorRuleRepository.existsByFunctionUnitIdAndConnectionUid(1L, "uid-a")).thenReturn(false);
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
        when(processDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(Optional.of(
                ProcessDefinition.builder()
                        .bpmnXml("<custom:property name=\"connectionId\" value=\"uid-a\" />")
                        .build()));
        when(i18nService.getMessage("email.connection.in_use")).thenReturn("Connection is in use");

        assertEquals("CONFLICT_CONNECTION_IN_USE",
                assertThrows(DeveloperBusinessException.class,
                        () -> checker.assertConnectionUnused(1L, connection)).getErrorCode());
    }

    @Test
    void assertConnectionUnused_unreferenced_passes() {
        EmailConnection connection = connection("uid-a", 10L);
        when(emailMonitorRuleRepository.existsByFunctionUnitIdAndConnectionUid(1L, "uid-a")).thenReturn(false);
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of(
                ActionDefinition.builder()
                        .configJson(Map.of("postEmail", Map.of("connectionId", "uid-other")))
                        .build()));
        when(processDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(Optional.empty());

        checker.assertConnectionUnused(1L, connection);
    }

    @Test
    void assertTemplateUnused_actionRule_throws() {
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of(
                ActionDefinition.builder()
                        .configJson(Map.of("postEmailRules", List.of(Map.of("emailTemplateId", "8"))))
                        .build()));
        when(i18nService.getMessage("email.template.in_use")).thenReturn("Template is in use");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> checker.assertTemplateUnused(1L, 8L));

        assertEquals("CONFLICT_TEMPLATE_IN_USE", ex.getErrorCode());
        assertEquals("Template is in use", ex.getMessage());
    }

    @Test
    void assertTemplateUnused_unreferenced_passes() {
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
        when(processDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(Optional.of(
                ProcessDefinition.builder()
                        .bpmnXml("<custom:property name=\"emailTemplateId\" value=\"99\" />")
                        .build()));

        checker.assertTemplateUnused(1L, 8L);
    }

    private static EmailConnection connection(String uid, Long id) {
        return EmailConnection.builder().id(id).connectionUid(uid).build();
    }
}
