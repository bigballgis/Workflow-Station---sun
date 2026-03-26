package com.developer.service;

import com.developer.entity.RelationViewConfig;
import com.developer.entity.RelationViewField;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.repository.RelationViewFieldRepository;
import com.developer.service.RelationViewService.ViewFieldDTO;
import com.developer.service.impl.RelationViewServiceImpl;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for View configuration persistence round-trip.
 *
 * <p><b>Feature: relation-tables, Property 11: View 配置持久化往返</b></p>
 *
 * For any View field configuration, saving and then reading should return
 * field list, display labels, column widths, and sort orders identical to
 * the saved values.
 *
 * <p><b>Validates: Requirements 9.6, 10.5</b></p>
 */
class RelationViewConfigRoundTripPropertyTest {

    private RelationViewConfigRepository viewConfigRepo;
    private RelationViewFieldRepository viewFieldRepo;
    private RelationViewService service;

    void setUp() {
        viewConfigRepo = mock(RelationViewConfigRepository.class);
        viewFieldRepo = mock(RelationViewFieldRepository.class);
        var jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        service = new RelationViewServiceImpl(viewConfigRepo, viewFieldRepo, jdbcTemplate);
    }

    /**
     * Property 11: View 配置持久化往返
     *
     * For any list of ViewFieldDTOs, saving them via saveViewConfig and then
     * reading back via getViewConfig should yield fields with identical
     * fieldName, displayLabel, columnWidth, sortOrder, and visible values.
     *
     * **Validates: Requirements 9.6, 10.5**
     */
    @Property(tries = 100)
    @Label("Feature: relation-tables, Property 11: View config round-trip persistence")
    void savedViewConfigShouldBeReadBackIdentically(
            @ForAll("bindingIds") Long bindingId,
            @ForAll("tableIds") Long tableId,
            @ForAll("viewFieldLists") List<ViewFieldDTO> fields) {

        setUp();

        // Given: a ViewConfig exists for the binding
        RelationViewConfig config = RelationViewConfig.builder()
                .id(bindingId * 10)
                .bindingId(bindingId)
                .tableId(tableId)
                .viewFields(new ArrayList<>())
                .build();

        when(viewConfigRepo.findByBindingId(bindingId)).thenReturn(Optional.of(config));
        // save returns the config with populated viewFields
        when(viewConfigRepo.save(any(RelationViewConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: save the field configuration
        RelationViewConfig saved = service.saveViewConfig(bindingId, fields);

        // Then: the saved config's viewFields match the input DTOs
        List<RelationViewField> savedFields = saved.getViewFields();
        assertThat(savedFields).hasSameSizeAs(fields);

        for (int i = 0; i < fields.size(); i++) {
            ViewFieldDTO dto = fields.get(i);
            RelationViewField field = savedFields.get(i);

            assertThat(field.getFieldName()).isEqualTo(dto.fieldName());
            assertThat(field.getDisplayLabel()).isEqualTo(dto.displayLabel());
            assertThat(field.getColumnWidth()).isEqualTo(dto.columnWidth());
            assertThat(field.getSortOrder()).isEqualTo(dto.sortOrder());
            assertThat(field.getVisible()).isEqualTo(dto.visible() != null ? dto.visible() : true);
        }

        // And: reading back should return the same config
        when(viewConfigRepo.findByBindingId(bindingId)).thenReturn(Optional.of(saved));
        RelationViewConfig readBack = service.getViewConfig(bindingId);
        assertThat(readBack.getViewFields()).hasSameSizeAs(fields);
    }

    @Provide
    Arbitrary<Long> bindingIds() {
        return Arbitraries.longs().between(1, 10000);
    }

    @Provide
    Arbitrary<Long> tableIds() {
        return Arbitraries.longs().between(1, 10000);
    }

    @Provide
    Arbitrary<List<ViewFieldDTO>> viewFieldLists() {
        Arbitrary<ViewFieldDTO> fieldArb = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
                Arbitraries.integers().between(50, 500),
                Arbitraries.integers().between(0, 100),
                Arbitraries.of(true, false)
        ).as(ViewFieldDTO::new);

        return fieldArb.list().ofMinSize(1).ofMaxSize(20);
    }
}
