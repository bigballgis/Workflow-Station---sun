package com.developer.component.impl;

import com.developer.dto.ActionDefinitionRequest;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.ActionType;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Post-email send conditions may reference the FORM_POPUP's own ACTION table via
 * {@code source: "ACTION"}; everything else stays scoped to system + MAIN table fields.
 */
@ExtendWith(MockitoExtension.class)
class ActionPostEmailActionTableFilterTest {

    private static final long FU_ID = 1L;
    private static final long ACTION_ID = 5L;
    private static final long POPUP_FORM_ID = 7L;
    private static final long ACTION_TABLE_ID = 20L;

    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormTableBindingRepository formTableBindingRepository;
    @Mock private I18nService i18nService;

    @InjectMocks
    private ActionDesignComponentImpl component;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage(anyString())).thenReturn("msg");
        lenient().when(i18nService.getMessage(anyString(), any(Object[].class))).thenReturn("msg");
        FunctionUnit fu = FunctionUnit.builder().id(FU_ID).build();
        lenient().when(actionDefinitionRepository.findById(ACTION_ID)).thenReturn(Optional.of(
                ActionDefinition.builder().id(ACTION_ID).functionUnit(fu).actionName("Add Remark").build()));
        lenient().when(actionDefinitionRepository.save(any(ActionDefinition.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(formDefinitionRepository.findById(POPUP_FORM_ID)).thenReturn(Optional.of(
                FormDefinition.builder().id(POPUP_FORM_ID).formType(FormType.ACTION).build()));
        lenient().when(formTableBindingRepository.findByFormIdAndBindingType(POPUP_FORM_ID, BindingType.ACTION))
                .thenReturn(Optional.of(FormTableBinding.builder()
                        .table(TableDefinition.builder().id(ACTION_TABLE_ID).build()).build()));
        lenient().when(tableDefinitionRepository.findByFunctionUnitIdWithFields(FU_ID)).thenReturn(List.of(
                table(10L, TableType.MAIN, "amount"),
                table(ACTION_TABLE_ID, TableType.ACTION, "remark_type"),
                table(30L, TableType.ACTION, "other_popup_field")));
    }

    @Test
    void acceptsPopupActionTableFieldWithActionSource() {
        assertDoesNotThrow(() -> component.update(ACTION_ID,
                request(ActionType.FORM_POPUP, condition("remark_type", "ACTION"))));
    }

    @Test
    void acceptsMainTableFieldWithoutSource() {
        assertDoesNotThrow(() -> component.update(ACTION_ID,
                request(ActionType.FORM_POPUP, condition("amount", null))));
    }

    @Test
    void rejectsActionTableFieldWithoutSource() {
        DeveloperBusinessException ex = assertThrows(DeveloperBusinessException.class,
                () -> component.update(ACTION_ID, request(ActionType.FORM_POPUP, condition("remark_type", null))));
        assertEquals("ACTION_EMAIL_FILTER_FIELD", ex.getErrorCode());
    }

    @Test
    void rejectsFieldOfAnotherActionTable() {
        DeveloperBusinessException ex = assertThrows(DeveloperBusinessException.class,
                () -> component.update(ACTION_ID,
                        request(ActionType.FORM_POPUP, condition("other_popup_field", "ACTION"))));
        assertEquals("ACTION_EMAIL_FILTER_FIELD", ex.getErrorCode());
    }

    @Test
    void rejectsActionSourceOnNonPopupAction() {
        DeveloperBusinessException ex = assertThrows(DeveloperBusinessException.class,
                () -> component.update(ACTION_ID, request(ActionType.APPROVE, condition("remark_type", "ACTION"))));
        assertEquals("ACTION_EMAIL_FILTER_FIELD", ex.getErrorCode());
    }

    @Test
    void rejectsUnknownSource() {
        DeveloperBusinessException ex = assertThrows(DeveloperBusinessException.class,
                () -> component.update(ACTION_ID, request(ActionType.FORM_POPUP, condition("amount", "SUB"))));
        assertEquals("ACTION_EMAIL_FILTER_INVALID", ex.getErrorCode());
    }

    private static TableDefinition table(long id, TableType type, String fieldName) {
        return TableDefinition.builder()
                .id(id)
                .tableType(type)
                .fieldDefinitions(List.of(FieldDefinition.builder().fieldName(fieldName).build()))
                .build();
    }

    private static Map<String, Object> condition(String fieldName, String source) {
        Map<String, Object> cond = new HashMap<>();
        cond.put("fieldName", fieldName);
        cond.put("operator", "eq");
        cond.put("value", "x");
        if (source != null) {
            cond.put("source", source);
        }
        return Map.of("logic", "and", "conditions", List.of(cond));
    }

    private static ActionDefinitionRequest request(ActionType type, Map<String, Object> condition) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("id", "r1");
        rule.put("enabled", true);
        rule.put("connectionId", "conn-1");
        rule.put("emailTo", "a@example.com");
        rule.put("emailTemplateId", "2");
        rule.put("condition", condition);
        Map<String, Object> config = new HashMap<>();
        config.put("formId", POPUP_FORM_ID);
        config.put("postEmailRules", List.of(rule));
        return ActionDefinitionRequest.builder()
                .actionName("Add Remark")
                .actionType(type)
                .configJson(config)
                .build();
    }
}
