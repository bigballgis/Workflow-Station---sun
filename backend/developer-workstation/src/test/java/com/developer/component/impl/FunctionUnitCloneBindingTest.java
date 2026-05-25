package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import com.developer.repository.*;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.service.UserDisplayNameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FunctionUnitCloneBindingTest {

    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private ActionDefinitionRepository actionDefinitionRepository;
    @Mock private DecisionDefinitionRepository decisionDefinitionRepository;
    @Mock private FormTableBindingRepository formTableBindingRepository;
    @Mock private FormStageBindingRepository formStageBindingRepository;
    @Mock private TableRelationRepository tableRelationRepository;
    @Mock private SubTableViewConfigRepository subTableViewConfigRepository;
    @Mock private VersionRepository versionRepository;
    @Mock private IconRepository iconRepository;
    @Mock private UserDisplayNameService userDisplayNameService;
    @Mock private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    @Mock private FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;
    @Mock private com.developer.component.VersionComponent versionComponent;
    @Mock private com.developer.util.DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;

    private FunctionUnitComponentImpl component;

    @BeforeEach
    void setUp() {
        lenient().when(functionUnitDevGroupAssignmentRepository.findByFunctionUnitId(anyLong()))
                .thenReturn(List.of());
        component = new FunctionUnitComponentImpl(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formTableBindingRepository,
                formStageBindingRepository,
                tableRelationRepository,
                subTableViewConfigRepository,
                versionRepository,
                iconRepository,
                new ObjectMapper(),
                userDisplayNameService,
                functionUnitWorkspaceAccessService,
                functionUnitDevGroupAssignmentRepository,
                versionComponent,
                sequenceSynchronizer);
    }

    @Test
    void clone_remapsBindingIdsAndCopiesRelationTableId() {
        FunctionUnit source = FunctionUnit.builder().id(1L).name("Source").build();

        TableDefinition mainTable = TableDefinition.builder()
                .id(10L).functionUnit(source).tableName("Main").tableType(TableType.MAIN).build();
        mainTable.setFieldDefinitions(new ArrayList<>());

        FormTableBinding primaryBinding = FormTableBinding.builder()
                .id(101L).bindingType(BindingType.PRIMARY).bindingMode(BindingMode.EDITABLE)
                .table(mainTable).sortOrder(0).build();
        FormTableBinding relatedBinding = FormTableBinding.builder()
                .id(102L).bindingType(BindingType.RELATED).bindingMode(BindingMode.READONLY)
                .relationTableId(999L).foreignKeyField("user_id").sortOrder(1).build();

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("subForms", new LinkedHashMap<>(Map.of("101", Map.of("title", "Main sub"))));
        configJson.put("relationViews", new LinkedHashMap<>(Map.of("102", Map.of("columns", List.of()))));

        FormDefinition sourceForm = FormDefinition.builder()
                .id(11L).functionUnit(source).formName("MainForm").formType(FormType.PROCESS)
                .configJson(configJson).showLiveValues(true).build();
        sourceForm.setTableBindings(List.of(primaryBinding, relatedBinding));

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(source));
        when(functionUnitRepository.existsByName("Cloned")).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(inv -> {
            FunctionUnit fu = inv.getArgument(0);
            if (fu.getId() == null) {
                fu.setId(2L);
            }
            return fu;
        });
        when(tableDefinitionRepository.findByFunctionUnitIdWithFields(1L)).thenReturn(List.of(mainTable));
        when(formDefinitionRepository.findByFunctionUnitIdWithBindings(1L)).thenReturn(List.of(sourceForm));
        when(tableRelationRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
        when(tableDefinitionRepository.save(any(TableDefinition.class))).thenAnswer(inv -> {
            TableDefinition t = inv.getArgument(0);
            t.setId(20L);
            return t;
        });
        when(formDefinitionRepository.save(any(FormDefinition.class))).thenAnswer(inv -> {
            FormDefinition f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(21L);
            }
            return f;
        });
        when(formTableBindingRepository.findByFormIdWithTable(11L))
                .thenReturn(List.of(primaryBinding, relatedBinding));
        when(formStageBindingRepository.findByFormId(11L)).thenReturn(List.of());
        when(formTableBindingRepository.save(any(FormTableBinding.class))).thenAnswer(inv -> {
            FormTableBinding b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(b.getBindingType() == BindingType.PRIMARY ? 501L : 502L);
            }
            return b;
        });

        component.clone(1L, "Cloned");

        ArgumentCaptor<FormTableBinding> bindingCaptor = ArgumentCaptor.forClass(FormTableBinding.class);
        verify(formTableBindingRepository, times(2)).save(bindingCaptor.capture());
        FormTableBinding savedRelated = bindingCaptor.getAllValues().stream()
                .filter(b -> b.getBindingType() == BindingType.RELATED)
                .findFirst()
                .orElseThrow();
        assertEquals(999L, savedRelated.getRelationTableId());
        assertNull(savedRelated.getTable());

        ArgumentCaptor<FormDefinition> formCaptor = ArgumentCaptor.forClass(FormDefinition.class);
        verify(formDefinitionRepository, atLeast(2)).save(formCaptor.capture());
        FormDefinition finalForm = formCaptor.getAllValues().get(formCaptor.getAllValues().size() - 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> subForms = (Map<String, Object>) finalForm.getConfigJson().get("subForms");
        assertTrue(subForms.containsKey("501"));
        assertFalse(subForms.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> relationViews = (Map<String, Object>) finalForm.getConfigJson().get("relationViews");
        assertTrue(relationViews.containsKey("502"));
        assertFalse(relationViews.containsKey("102"));
    }
}
