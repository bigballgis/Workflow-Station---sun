package com.developer.service;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.RelationViewConfig;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.service.impl.RelationTableBindingServiceImpl;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for Binding-View lifecycle synchronization.
 *
 * <p><b>Feature: relation-tables, Property 10: 绑定-View 生命周期同步</b></p>
 *
 * For any Relation Table binding operation, a successful bind should auto-create
 * a corresponding RelationViewConfig record; unbinding should auto-delete the
 * RelationViewConfig and its RelationViewFields.
 *
 * <p><b>Validates: Requirements 9.3, 9.7</b></p>
 */
class RelationTableBindingViewSyncPropertyTest {

    private FormTableBindingRepository bindingRepo;
    private FormDefinitionRepository formRepo;
    private RelationViewConfigRepository viewConfigRepo;
    private RelationTableBindingService service;

    void setUp() {
        openMocks(this);
        bindingRepo = mock(FormTableBindingRepository.class);
        formRepo = mock(FormDefinitionRepository.class);
        viewConfigRepo = mock(RelationViewConfigRepository.class);
        var jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        service = new RelationTableBindingServiceImpl(bindingRepo, formRepo, viewConfigRepo, jdbcTemplate);
    }

    /**
     * Property 10: 绑定-View 生命周期同步 — Bind creates ViewConfig
     *
     * For any valid formId and tableId, binding a Relation Table should
     * automatically create a RelationViewConfig with matching bindingId and tableId.
     *
     * **Validates: Requirements 9.3, 9.7**
     */
    @Property(tries = 100)
    @Label("Feature: relation-tables, Property 10: Bind auto-creates ViewConfig")
    void bindShouldAutoCreateViewConfig(
            @ForAll("formIds") Long formId,
            @ForAll("tableIds") Long tableId) {

        setUp();

        // Given: a form exists and no prior binding
        FormDefinition form = FormDefinition.builder().id(formId).formName("test").build();
        when(formRepo.findById(formId)).thenReturn(Optional.of(form));
        when(bindingRepo.existsByFormIdAndRelationTableId(formId, tableId)).thenReturn(false);
        when(bindingRepo.countByFormId(formId)).thenReturn(0L);

        // Mock save to return binding with generated ID
        long generatedBindingId = formId * 1000 + tableId;
        when(bindingRepo.save(any(FormTableBinding.class))).thenAnswer(inv -> {
            FormTableBinding b = inv.getArgument(0);
            b.setId(generatedBindingId);
            return b;
        });
        when(viewConfigRepo.save(any(RelationViewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: bind
        Long bindingId = service.bindRelationTable(formId, tableId);

        // Then: ViewConfig is created with correct bindingId and tableId
        ArgumentCaptor<RelationViewConfig> captor = ArgumentCaptor.forClass(RelationViewConfig.class);
        verify(viewConfigRepo).save(captor.capture());
        RelationViewConfig created = captor.getValue();

        assertThat(created.getBindingId()).isEqualTo(generatedBindingId);
        assertThat(created.getTableId()).isEqualTo(tableId);
        assertThat(bindingId).isEqualTo(generatedBindingId);
    }

    /**
     * Property 10: 绑定-View 生命周期同步 — Unbind deletes ViewConfig
     *
     * For any valid binding, unbinding should delete the corresponding
     * RelationViewConfig (which cascades to delete ViewFields).
     *
     * **Validates: Requirements 9.3, 9.7**
     */
    @Property(tries = 100)
    @Label("Feature: relation-tables, Property 10: Unbind auto-deletes ViewConfig")
    void unbindShouldAutoDeleteViewConfig(
            @ForAll("formIds") Long formId,
            @ForAll("bindingIds") Long bindingId,
            @ForAll("tableIds") Long tableId) {

        setUp();

        // Given: a binding exists with a ViewConfig
        FormDefinition form = FormDefinition.builder().id(formId).formName("test").build();
        FormTableBinding binding = FormTableBinding.builder()
                .id(bindingId)
                .form(form)
                .relationTableId(tableId)
                .bindingType(BindingType.RELATED)
                .bindingMode(BindingMode.READONLY)
                .build();
        when(bindingRepo.findById(bindingId)).thenReturn(Optional.of(binding));

        RelationViewConfig viewConfig = RelationViewConfig.builder()
                .id(bindingId * 10)
                .bindingId(bindingId)
                .tableId(tableId)
                .build();
        when(viewConfigRepo.findByBindingId(bindingId)).thenReturn(Optional.of(viewConfig));

        // When: unbind
        service.unbindRelationTable(formId, bindingId);

        // Then: ViewConfig is deleted
        verify(viewConfigRepo).delete(viewConfig);
        // And: binding is deleted
        verify(bindingRepo).delete(binding);
    }

    @Provide
    Arbitrary<Long> formIds() {
        return Arbitraries.longs().between(1, 10000);
    }

    @Provide
    Arbitrary<Long> tableIds() {
        return Arbitraries.longs().between(1, 10000);
    }

    @Provide
    Arbitrary<Long> bindingIds() {
        return Arbitraries.longs().between(1, 10000);
    }
}
