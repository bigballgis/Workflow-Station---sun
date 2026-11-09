package com.developer.component.impl;

import com.developer.dto.FkFillSource;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.security.WorkspaceRouteOwnership;
import com.developer.security.WorkspaceRouteOwnership.Owned;
import com.developer.service.SubTableViewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Table, field and binding ids in a form or binding body must name objects of the form's unit (§6.1, §6.2): the path
 * only proves the caller may edit this form. A foreign id is a 404 and nothing is written.
 */
@ExtendWith(MockitoExtension.class)
class FormDesignComponentImplOwnershipTest {

    private static final long UNIT = 50005L;
    private static final long FORM = 50601L;
    private static final long FOREIGN = 999L;

    @Mock private FormTableBindingRestorer formTableBindingRestorer;
    @Mock private FormConfigJsonTableProvisioner formConfigJsonTableProvisioner;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormTableBindingRepository formTableBindingRepository;
    @Mock private SubTableViewConfigRepository subTableViewConfigRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private I18nService i18nService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SubTableViewService subTableViewService;
    @Mock private FormTableBindingUniquenessGuard formTableBindingUniquenessGuard;
    @Mock private OwnerFieldFormReconciler ownerFieldFormReconciler;
    @Mock private WorkspaceRouteOwnership routeOwnership;

    @InjectMocks private FormDesignComponentImpl component;

    private FormDefinition storedForm() {
        FormDefinition form = FormDefinition.builder()
                .id(FORM)
                .functionUnit(FunctionUnit.builder().id(UNIT).build())
                .formName("Main")
                .formType(FormType.TASK)
                .configJson(new HashMap<>())
                .build();
        // Non-empty so getById does not take its binding-repair detour.
        form.getTableBindings().add(new FormTableBinding());
        when(formDefinitionRepository.findByIdWithBindings(FORM)).thenReturn(Optional.of(form));
        return form;
    }

    private void foreign(Owned kind) {
        // lenient: the other ids of the request reach the same method with other arguments
        lenient().doThrow(new ResourceNotFoundException(kind.name(), String.valueOf(FOREIGN)))
                .when(routeOwnership).requireInUnit(kind, FOREIGN, UNIT);
    }

    @Test
    void createBinding_tableOfAnotherUnit_isNotFound() {
        storedForm();
        foreign(Owned.TABLE);
        FormTableBindingRequest request = FormTableBindingRequest.builder()
                .tableId(FOREIGN).bindingType(BindingType.SUB).build();

        assertThatThrownBy(() -> component.createBinding(FORM, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(tableDefinitionRepository);
        verify(formTableBindingRepository, never()).save(any());
    }

    @Test
    void createBinding_filterFkFieldOfAnotherUnit_isNotFound() {
        storedForm();
        foreign(Owned.FIELD);
        when(tableDefinitionRepository.findById(7L)).thenReturn(Optional.of(
                com.developer.entity.TableDefinition.builder().id(7L).build()));
        FormTableBindingRequest request = FormTableBindingRequest.builder()
                .tableId(7L).bindingType(BindingType.SUB).filterFkFieldId(FOREIGN).build();

        assertThatThrownBy(() -> component.createBinding(FORM, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(formTableBindingRepository, never()).save(any());
    }

    @Test
    void updateBinding_fkFillSourceFieldOfAnotherUnit_isNotFound() {
        FormDefinition form = storedForm();
        foreign(Owned.FIELD);
        FormTableBinding binding = FormTableBinding.builder().id(3L).form(form).bindingType(BindingType.SUB).build();
        when(formTableBindingRepository.findById(3L)).thenReturn(Optional.of(binding));
        FormTableBindingRequest request = FormTableBindingRequest.builder()
                .bindingType(BindingType.SUB)
                .fkFillSources(List.of(FkFillSource.builder().fieldId(FOREIGN).kind(FkFillSource.KIND_PARENT).build()))
                .build();

        assertThatThrownBy(() -> component.updateBinding(3L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(formTableBindingRepository, never()).save(any());
    }

    @Test
    void updateBinding_ancestorBindingOfAnotherUnit_isNotFound() {
        FormDefinition form = storedForm();
        foreign(Owned.FORM_BINDING);
        FormTableBinding binding = FormTableBinding.builder().id(3L).form(form).bindingType(BindingType.SUB).build();
        when(formTableBindingRepository.findById(3L)).thenReturn(Optional.of(binding));
        FormTableBindingRequest request = FormTableBindingRequest.builder()
                .bindingType(BindingType.SUB)
                .fkFillSources(List.of(FkFillSource.builder().kind(FkFillSource.KIND_ANCESTOR)
                        .ancestorBindingId(FOREIGN).build()))
                .build();

        assertThatThrownBy(() -> component.updateBinding(3L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(formTableBindingRepository, never()).save(any());
    }

    @Test
    void create_boundTableOfAnotherUnit_isNotFound() {
        when(functionUnitRepository.findById(UNIT)).thenReturn(Optional.of(FunctionUnit.builder().id(UNIT).build()));
        foreign(Owned.TABLE);
        FormDefinitionRequest request = FormDefinitionRequest.builder()
                .formName("New").formType(FormType.TASK).configJson(new HashMap<>()).boundTableId(FOREIGN).build();

        assertThatThrownBy(() -> component.create(UNIT, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(tableDefinitionRepository);
        verify(formDefinitionRepository, never()).save(any());
    }

    @Test
    void update_boundTableOfAnotherUnit_isNotFound() {
        storedForm();
        foreign(Owned.TABLE);
        FormDefinitionRequest request = FormDefinitionRequest.builder()
                .formName("Main").formType(FormType.TASK).configJson(new HashMap<>()).boundTableId(FOREIGN).build();

        assertThatThrownBy(() -> component.update(FORM, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(tableDefinitionRepository);
        verify(formDefinitionRepository, never()).save(any());
    }
}
