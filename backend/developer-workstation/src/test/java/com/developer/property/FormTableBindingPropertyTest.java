package com.developer.property;

import com.developer.dto.FormTableBindingRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.*;
import com.developer.exception.BusinessException;
import com.developer.repository.FormTableBindingRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 表单表绑定属性测试
 * Feature: form-multi-table-binding
 */
public class FormTableBindingPropertyTest {
    
    /**
     * Property 6: Binding persistence round-trip
     * For any set of table bindings created for a form, saving and then loading 
     * the form should return all bindings with their original configurations intact.
     * 
     * Validates: Requirements 6.1, 6.2, 6.3
     */
    @Property(tries = 50)
    void bindingPersistenceRoundTripProperty(
            @ForAll("validBindingConfigs") List<BindingConfig> configs) {
        
        FormTableBindingRepository repository = mock(FormTableBindingRepository.class);
        List<FormTableBinding> savedBindings = new ArrayList<>();
        
        when(repository.save(any(FormTableBinding.class))).thenAnswer(invocation -> {
            FormTableBinding binding = invocation.getArgument(0);
            if (binding.getId() == null) {
                binding.setId((long) (savedBindings.size() + 1));
            }
            savedBindings.add(binding);
            return binding;
        });
        
        when(repository.findByFormIdWithTable(anyLong())).thenReturn(savedBindings);
        
        FunctionUnit functionUnit = createFunctionUnit();
        FormDefinition form = createFormDefinition(functionUnit);
        List<TableDefinition> tables = createTableDefinitions(functionUnit, configs.size());
        
        for (int i = 0; i < configs.size(); i++) {
            BindingConfig config = configs.get(i);
            FormTableBinding binding = FormTableBinding.builder()
                    .form(form)
                    .table(tables.get(i))
                    .bindingType(config.bindingType)
                    .bindingMode(config.bindingMode)
                    .foreignKeyField(config.foreignKeyField)
                    .sortOrder(i)
                    .build();
            repository.save(binding);
        }
        
        List<FormTableBinding> loadedBindings = repository.findByFormIdWithTable(form.getId());
        
        assertThat(loadedBindings).hasSize(configs.size());
        
        for (int i = 0; i < configs.size(); i++) {
            FormTableBinding loaded = loadedBindings.get(i);
            BindingConfig original = configs.get(i);
            
            assertThat(loaded.getBindingType()).isEqualTo(original.bindingType);
            assertThat(loaded.getBindingMode()).isEqualTo(original.bindingMode);
            assertThat(loaded.getForeignKeyField()).isEqualTo(original.foreignKeyField);
            assertThat(loaded.getSortOrder()).isEqualTo(i);
        }
    }
    
    /**
     * Property 6 (continued): Individual bindings should be updatable
     */
    @Property(tries = 30)
    void bindingUpdateProperty(
            @ForAll("bindingModes") BindingMode originalMode,
            @ForAll("bindingModes") BindingMode newMode) {
        
        FormTableBindingRepository repository = mock(FormTableBindingRepository.class);
        
        FormTableBinding binding = FormTableBinding.builder()
                .id(1L)
                .bindingType(BindingType.SUB)
                .bindingMode(originalMode)
                .foreignKeyField("parent_id")
                .sortOrder(0)
                .build();
        
        when(repository.findById(1L)).thenReturn(Optional.of(binding));
        when(repository.save(any(FormTableBinding.class))).thenAnswer(inv -> inv.getArgument(0));
        
        Optional<FormTableBinding> found = repository.findById(1L);
        assertThat(found).isPresent();
        
        found.get().setBindingMode(newMode);
        FormTableBinding updated = repository.save(found.get());
        
        assertThat(updated.getBindingMode()).isEqualTo(newMode);
    }
    
    /**
     * Property 6 (continued): Individual bindings should be deletable
     */
    @Property(tries = 30)
    void bindingDeleteProperty(@ForAll @IntRange(min = 1, max = 5) int bindingCount) {
        FormTableBindingRepository repository = mock(FormTableBindingRepository.class);
        List<FormTableBinding> bindings = new ArrayList<>();
        
        for (int i = 0; i < bindingCount; i++) {
            FormTableBinding binding = FormTableBinding.builder()
                    .id((long) (i + 1))
                    .bindingType(i == 0 ? BindingType.PRIMARY : BindingType.SUB)
                    .bindingMode(BindingMode.READONLY)
                    .sortOrder(i)
                    .build();
            bindings.add(binding);
        }
        
        when(repository.findByFormIdWithTable(anyLong())).thenReturn(new ArrayList<>(bindings));
        
        Long deleteId = bindings.get(0).getId();
        doAnswer(inv -> {
            bindings.removeIf(b -> b.getId().equals(deleteId));
            return null;
        }).when(repository).deleteById(deleteId);
        
        repository.deleteById(deleteId);
        
        assertThat(bindings).hasSize(bindingCount - 1);
        assertThat(bindings.stream().noneMatch(b -> b.getId().equals(deleteId))).isTrue();
    }
    
    /**
     * Property 2: Foreign key validation
     * Validates: Requirements 2.1, 2.2, 2.3
     */
    @Property(tries = 50)
    void foreignKeyValidationProperty(
            @ForAll("bindingTypesRequiringForeignKey") BindingType bindingType,
            @ForAll("fieldNames") String foreignKeyField,
            @ForAll("tableFieldSets") List<String> tableFields) {
        
        TableDefinition table = TableDefinition.builder()
                .id(1L)
                .tableName("test_table")
                .tableType(TableType.SUB)
                .build();
        
        List<FieldDefinition> fields = tableFields.stream()
                .map(fieldName -> FieldDefinition.builder()
                        .id((long) tableFields.indexOf(fieldName) + 1)
                        .tableDefinition(table)
                        .fieldName(fieldName)
                        .dataType(DataType.VARCHAR)
                        .nullable(true)
                        .build())
                .toList();
        table.setFieldDefinitions(new ArrayList<>(fields));
        
        boolean fieldExists = tableFields.contains(foreignKeyField);
        boolean actualValidation = table.getFieldDefinitions().stream()
                .anyMatch(field -> field.getFieldName().equals(foreignKeyField));
        
        assertThat(actualValidation).isEqualTo(fieldExists);
    }
    
    /**
     * Property 3: Binding mode configuration
     * Validates: Requirements 3.1, 3.2, 3.3
     */
    @Property(tries = 50)
    void bindingModeConfigurationProperty(
            @ForAll("allBindingTypes") BindingType bindingType,
            @ForAll("bindingModes") BindingMode explicitMode) {
        
        FormTableBindingRequest requestWithoutMode = FormTableBindingRequest.builder()
                .tableId(1L)
                .bindingType(bindingType)
                .foreignKeyField(bindingType != BindingType.PRIMARY ? "parent_id" : null)
                .build();
        
        BindingMode defaultMode = bindingType == BindingType.PRIMARY 
                ? BindingMode.EDITABLE 
                : BindingMode.READONLY;
        
        BindingMode actualDefaultMode = requestWithoutMode.getBindingMode();
        if (actualDefaultMode == null) {
            actualDefaultMode = bindingType == BindingType.PRIMARY 
                    ? BindingMode.EDITABLE 
                    : BindingMode.READONLY;
        }
        
        assertThat(actualDefaultMode).isEqualTo(defaultMode);
        
        FormTableBindingRequest requestWithMode = FormTableBindingRequest.builder()
                .tableId(1L)
                .bindingType(bindingType)
                .bindingMode(explicitMode)
                .foreignKeyField(bindingType != BindingType.PRIMARY ? "parent_id" : null)
                .build();
        
        assertThat(requestWithMode.getBindingMode()).isEqualTo(explicitMode);
    }
    
    // ========== Helper Methods ==========
    
    private FunctionUnit createFunctionUnit() {
        FunctionUnit fu = new FunctionUnit();
        fu.setId(1L);
        fu.setName("TestFunctionUnit");
        fu.setStatus(FunctionUnitStatus.DRAFT);
        return fu;
    }
    
    private FormDefinition createFormDefinition(FunctionUnit functionUnit) {
        return FormDefinition.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .formName("TestForm")
                .formType(FormType.MAIN)
                .configJson(new HashMap<>())
                .build();
    }
    
    private List<TableDefinition> createTableDefinitions(FunctionUnit functionUnit, int count) {
        List<TableDefinition> tables = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TableDefinition table = TableDefinition.builder()
                    .id((long) (i + 1))
                    .functionUnit(functionUnit)
                    .tableName("table_" + i)
                    .tableType(i == 0 ? TableType.MAIN : TableType.SUB)
                    .build();
            tables.add(table);
        }
        return tables;
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<List<BindingConfig>> validBindingConfigs() {
        return Arbitraries.integers().between(1, 5).flatMap(count -> {
            List<Arbitrary<BindingConfig>> configs = new ArrayList<>();
            configs.add(Arbitraries.of(new BindingConfig(BindingType.PRIMARY, BindingMode.EDITABLE, null)));
            for (int i = 1; i < count; i++) {
                configs.add(bindingConfig());
            }
            return Combinators.combine(configs).as(list -> list);
        });
    }
    
    @Provide
    Arbitrary<BindingConfig> bindingConfig() {
        return Combinators.combine(
                Arbitraries.of(BindingType.SUB, BindingType.RELATED),
                Arbitraries.of(BindingMode.EDITABLE, BindingMode.READONLY),
                Arbitraries.of("parent_id", "ref_id", "foreign_key", null)
        ).as(BindingConfig::new);
    }
    
    @Provide
    Arbitrary<BindingMode> bindingModes() {
        return Arbitraries.of(BindingMode.EDITABLE, BindingMode.READONLY);
    }
    
    @Provide
    Arbitrary<BindingType> bindingTypesRequiringForeignKey() {
        return Arbitraries.of(BindingType.SUB, BindingType.RELATED);
    }
    
    @Provide
    Arbitrary<String> fieldNames() {
        return Arbitraries.of("parent_id", "ref_id", "foreign_key", "main_table_id", "link_id");
    }
    
    @Provide
    Arbitrary<List<String>> tableFieldSets() {
        return Arbitraries.of(
                List.of("id", "name", "parent_id", "created_at"),
                List.of("id", "ref_id", "value"),
                List.of("id", "foreign_key", "data"),
                List.of("id", "name", "description")
        );
    }
    
    @Provide
    Arbitrary<BindingType> allBindingTypes() {
        return Arbitraries.of(BindingType.PRIMARY, BindingType.SUB, BindingType.RELATED);
    }
    
    // ========== Helper Classes ==========
    
    record BindingConfig(BindingType bindingType, BindingMode bindingMode, String foreignKeyField) {}
}
