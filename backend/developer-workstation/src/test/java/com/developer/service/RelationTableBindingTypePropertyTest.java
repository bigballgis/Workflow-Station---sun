package com.developer.service;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.RelationViewConfig;
import com.developer.enums.BindingType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.service.impl.RelationTableBindingServiceImpl;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Relation Table binding type constraint.
 *
 * <p><b>Feature: relation-tables, Property 21: Relation Table 绑定类型为 RELATED</b></p>
 *
 * For any binding created through bindRelationTable, the bindingType should
 * always be RELATED.
 *
 * <p><b>Validates: Requirements 9.2</b></p>
 */
class RelationTableBindingTypePropertyTest {

    /**
     * Property 21: Relation Table 绑定类型为 RELATED
     *
     * For any formId and tableId, calling bindRelationTable should always
     * create a FormTableBinding with bindingType = RELATED.
     *
     * **Validates: Requirements 9.2**
     */
    @Property(tries = 100)
    @Label("Feature: relation-tables, Property 21: Binding type is always RELATED")
    void bindRelationTableShouldAlwaysUseRelatedType(
            @ForAll("formIds") Long formId,
            @ForAll("tableIds") Long tableId) {

        FormTableBindingRepository bindingRepo = mock(FormTableBindingRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        RelationViewConfigRepository viewConfigRepo = mock(RelationViewConfigRepository.class);
        var jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);

        RelationTableBindingService service = new RelationTableBindingServiceImpl(
                bindingRepo, formRepo, viewConfigRepo, jdbcTemplate);

        // Given: form exists, no prior binding
        FormDefinition form = FormDefinition.builder().id(formId).formName("test").build();
        when(formRepo.findById(formId)).thenReturn(Optional.of(form));
        when(bindingRepo.existsByFormIdAndRelationTableId(formId, tableId)).thenReturn(false);
        when(bindingRepo.countByFormId(formId)).thenReturn(0L);

        ArgumentCaptor<FormTableBinding> captor = ArgumentCaptor.forClass(FormTableBinding.class);
        when(bindingRepo.save(captor.capture())).thenAnswer(inv -> {
            FormTableBinding b = inv.getArgument(0);
            b.setId(formId * 1000 + tableId);
            return b;
        });
        when(viewConfigRepo.save(any(RelationViewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.bindRelationTable(formId, tableId);

        // Then: the saved binding has type RELATED
        FormTableBinding saved = captor.getValue();
        assertThat(saved.getBindingType()).isEqualTo(BindingType.RELATED);
    }

    @Provide
    Arbitrary<Long> formIds() {
        return Arbitraries.longs().between(1, 10000);
    }

    @Provide
    Arbitrary<Long> tableIds() {
        return Arbitraries.longs().between(1, 10000);
    }
}
