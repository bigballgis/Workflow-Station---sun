package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.security.WorkspaceRouteOwnership;
import com.platform.common.i18n.I18nService;
import com.platform.common.i18n.MessageSourceConfig;
import com.platform.common.i18n.impl.I18nServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * XML a save request carries that cannot be read is the caller's error: {@link ProcessDesignComponentImpl#save}
 * refuses it as a business error (400, message in the caller's language), not as a server error (500 with a stack
 * trace), and stores nothing. The unit has an action, so fixStaleIds parses the XML (ActionBindingLifecycle).
 */
class ProcessDesignSaveInvalidXmlTest {

    private static final long FU_ID = 50005L;

    private final ProcessDefinitionRepository processDefinitionRepository = mock(ProcessDefinitionRepository.class);
    private ProcessDesignComponentImpl component;

    @BeforeEach
    void setUp() {
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        TableDefinitionRepository tableDefinitionRepository = mock(TableDefinitionRepository.class);
        FormDefinitionRepository formDefinitionRepository = mock(FormDefinitionRepository.class);
        ActionDefinitionRepository actionDefinitionRepository = mock(ActionDefinitionRepository.class);
        when(functionUnitRepository.findById(FU_ID)).thenReturn(Optional.of(FunctionUnit.builder().id(FU_ID).build()));
        when(formDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(tableDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(actionDefinitionRepository.findByFunctionUnitId(anyLong()))
                .thenReturn(List.of(ActionDefinition.builder().id(7L).actionName("Approve").build()));
        // The bundles the application loads (spring.messages.basename in application.yml).
        I18nService i18n = new I18nServiceImpl(
                new MessageSourceConfig().messageSource("i18n/messages,i18n/dw-messages", "UTF-8"));
        component = new ProcessDesignComponentImpl(
                processDefinitionRepository,
                functionUnitRepository,
                new ProcessBpmnStaleIdFixer(tableDefinitionRepository, formDefinitionRepository,
                        actionDefinitionRepository),
                new ProcessBpmnValidator(tableDefinitionRepository, formDefinitionRepository, null),
                new ProcessSimulationHelper(tableDefinitionRepository),
                new ProcessDebugProbeRunner(formDefinitionRepository, null, null, null, null),
                null,
                null,
                new ProcessBpmnEmailReferenceGuard(new WorkspaceRouteOwnership(mock(JdbcTemplate.class)), i18n),
                i18n);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void xmlThatDoesNotParseIsRefusedWithWhereItBreaks() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        DeveloperBusinessException ex = refusal(process("[\"7\"]").replace("</bpmn:process>", ""));

        assertThat(ex.getErrorCode()).isEqualTo("BPMN_XML_NOT_WELL_FORMED");
        assertThat(ex.getMessage()).startsWith("The process cannot be saved: its XML is not well-formed (line ");
        verify(processDefinitionRepository, never()).save(any(ProcessDefinition.class));
    }

    @Test
    void anActionIdListThatCannotBeReadIsRefusedInTheCallersLanguage() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        DeveloperBusinessException ex = refusal(process("[{\"id\": 7}]"));

        assertThat(ex.getErrorCode()).isEqualTo("BPMN_PROPERTY_INVALID");
        assertThat(ex.getMessage()).isEqualTo("流程无法保存：XML 中有属性值无法读取（Invalid action ID list）。请在设计器中重新保存。");
        verify(processDefinitionRepository, never()).save(any(ProcessDefinition.class));
    }

    @Test
    void aReadableProcessIsStillSaved() {
        when(processDefinitionRepository.save(any(ProcessDefinition.class))).thenAnswer(i -> i.getArgument(0));

        component.save(FU_ID, process("[\"7\"]"), false);

        verify(processDefinitionRepository).save(any(ProcessDefinition.class));
    }

    private DeveloperBusinessException refusal(String xml) {
        return catchThrowableOfType(() -> component.save(FU_ID, xml, false), DeveloperBusinessException.class);
    }

    private static String process(String actionIds) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:custom=\"http://custom.bpmn.io/schema\" id=\"d\"><bpmn:process id=\"p\" isExecutable=\"true\">"
                + "<bpmn:startEvent id=\"s\"/><bpmn:userTask id=\"t\" name=\"Review\"><bpmn:extensionElements>"
                + "<custom:properties><custom:property name=\"actionIds\" value='" + actionIds + "'/>"
                + "<custom:property name=\"actionNames\" value='[\"Approve\"]'/></custom:properties>"
                + "</bpmn:extensionElements></bpmn:userTask><bpmn:endEvent id=\"e\"/>"
                + "<bpmn:sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"t\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"t\" targetRef=\"e\"/>"
                + "</bpmn:process></bpmn:definitions>";
    }
}
