package com.developer.component.impl;

import com.developer.component.TableDesignComponent;
import com.developer.dto.TableDefinitionRequest;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormConfigJsonTableProvisionerTest {

    @Mock
    private TableDesignComponent tableDesignComponent;
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    @Mock
    private FormTableBindingRepository formTableBindingRepository;
    @Mock
    private FormDefinitionRepository formDefinitionRepository;

    private FormConfigJsonTableProvisioner provisioner;
    private final AtomicLong nextId = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        provisioner = new FormConfigJsonTableProvisioner(
                tableDesignComponent,
                tableDefinitionRepository,
                formTableBindingRepository,
                formDefinitionRepository);
    }

    @Test
    void provision_prefersSourceTableNames_andSuffixesWhenTaken() {
        FormDefinition targetForm = FormDefinition.builder()
                .id(10L)
                .formName("test1")
                .formType(FormType.PROCESS)
                .build();
        FormDefinition sourceForm = FormDefinition.builder().id(99L).formName("MCY").build();

        TableDefinition sourceMain = TableDefinition.builder()
                .id(1L).tableName("HMDC_Case").tableDisplayName("HMDC Case").tableType(TableType.MAIN).build();
        TableDefinition sourceSub = TableDefinition.builder()
                .id(2L).tableName("HMDC_Transaction").tableDisplayName("HMDC Transaction")
                .tableType(TableType.SUB).build();

        FormTableBinding sourcePrimary = FormTableBinding.builder()
                .id(270L).form(sourceForm).table(sourceMain).bindingType(BindingType.PRIMARY).build();
        FormTableBinding sourceSubBinding = FormTableBinding.builder()
                .id(273L).form(sourceForm).table(sourceSub).bindingType(BindingType.SUB).build();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", List.of(Map.of(
                "type", "subTable",
                "_bindingId", 273,
                "props", Map.of())));
        config.put("subForms", Map.of("273", Map.of(
                "rule", List.of(Map.of("type", "input", "field", "card_number", "title", "Card")))));
        config.put("subListViews", Map.of("273", Map.of(
                "columns", List.of(Map.of("fieldName", "card_number")))));

        when(formTableBindingRepository.findByFormIdWithTable(10L))
                .thenReturn(new ArrayList<>())
                .thenAnswer(inv -> new ArrayList<>(savedBindings));
        when(tableDefinitionRepository.findByFunctionUnitIdWithFields(7L))
                .thenReturn(new ArrayList<>())
                .thenAnswer(inv -> new ArrayList<>(createdTables));

        when(formTableBindingRepository.findByIdWithTable(273L)).thenReturn(Optional.of(sourceSubBinding));
        when(formTableBindingRepository.findByFormIdWithTable(99L))
                .thenReturn(List.of(sourcePrimary, sourceSubBinding));

        when(tableDesignComponent.isTableNameAvailable(eq("HMDC_Case"), isNull())).thenReturn(true);
        when(tableDesignComponent.isTableNameAvailable(eq("HMDC_Transaction"), isNull())).thenReturn(false);
        when(tableDesignComponent.isTableNameAvailable(eq("HMDC_Transaction_2"), isNull())).thenReturn(true);

        when(tableDesignComponent.create(eq(7L), any(TableDefinitionRequest.class)))
                .thenAnswer(inv -> {
                    TableDefinitionRequest req = inv.getArgument(1);
                    TableDefinition created = TableDefinition.builder()
                            .id(nextId.getAndIncrement())
                            .tableName(req.getTableName())
                            .tableDisplayName(req.getTableDisplayName())
                            .tableType(req.getTableType())
                            .fieldDefinitions(new ArrayList<>())
                            .build();
                    createdTables.add(created);
                    return created;
                });
        when(formTableBindingRepository.save(any(FormTableBinding.class))).thenAnswer(inv -> {
            FormTableBinding b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(nextId.getAndIncrement());
            }
            savedBindings.removeIf(x -> ObjectsEqualsId(x, b));
            savedBindings.add(b);
            return b;
        });
        when(formDefinitionRepository.save(any(FormDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        FormConfigJsonTableProvisioner.ProvisionResult result =
                provisioner.provision(7L, targetForm, config);

        assertThat(result.createdTableNames()).containsExactly("HMDC_Case", "HMDC_Transaction_2");
        ArgumentCaptor<TableDefinitionRequest> captor = ArgumentCaptor.forClass(TableDefinitionRequest.class);
        org.mockito.Mockito.verify(tableDesignComponent, org.mockito.Mockito.times(2))
                .create(eq(7L), captor.capture());
        assertThat(captor.getAllValues().get(0).getTableName()).isEqualTo("HMDC_Case");
        assertThat(captor.getAllValues().get(0).getTableDisplayName()).isEqualTo("HMDC Case");
        assertThat(captor.getAllValues().get(1).getTableName()).isEqualTo("HMDC_Transaction_2");
        assertThat(captor.getAllValues().get(1).getTableDisplayName()).isEqualTo("HMDC Transaction");
    }

    private final List<FormTableBinding> savedBindings = new ArrayList<>();
    private final List<TableDefinition> createdTables = new ArrayList<>();

    private static boolean ObjectsEqualsId(FormTableBinding a, FormTableBinding b) {
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
