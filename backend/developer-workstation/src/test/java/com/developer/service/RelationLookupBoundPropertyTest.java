package com.developer.service;

import com.developer.entity.FormTableBinding;
import com.developer.entity.RelationViewConfig;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationLookupConfigRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.service.RelationLookupService.BoundViewDTO;
import com.developer.service.impl.RelationLookupServiceImpl;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Lookup bound views filtering.
 *
 * <p><b>Feature: relation-tables, Property 12: Lookup 仅限已绑定表</b></p>
 *
 * For any Lookup component config request, the available Relation Table list
 * should exactly equal the set of Relation Tables bound to the current Form.
 *
 * <p><b>Validates: Requirements 9.8, 10.2</b></p>
 */
class RelationLookupBoundPropertyTest {

    /**
     * Property 12: Lookup 仅限已绑定表
     *
     * For any set of RELATED bindings on a form, getBoundViews should return
     * exactly the set of bound Relation Tables, no more, no less.
     *
     * **Validates: Requirements 9.8, 10.2**
     */
    @Property(tries = 100)
    @Label("Feature: relation-tables, Property 12: Lookup only shows bound tables")
    void boundViewsShouldMatchBoundRelationTables(
            @ForAll("bindingScenarios") BindingScenario scenario) {

        FormTableBindingRepository bindingRepo = mock(FormTableBindingRepository.class);
        RelationLookupConfigRepository lookupRepo = mock(RelationLookupConfigRepository.class);
        RelationViewConfigRepository viewConfigRepo = mock(RelationViewConfigRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        // Mock: return RELATED bindings for the form
        when(bindingRepo.findByFormIdAndBindingTypeList(eq(scenario.formId), eq(BindingType.RELATED)))
                .thenReturn(scenario.relatedBindings);

        // Mock: each binding has a view config
        for (FormTableBinding binding : scenario.relatedBindings) {
            RelationViewConfig vc = RelationViewConfig.builder()
                    .id(binding.getId() * 100)
                    .bindingId(binding.getId())
                    .tableId(binding.getRelationTableId())
                    .build();
            when(viewConfigRepo.findByBindingId(binding.getId()))
                    .thenReturn(Optional.of(vc));
        }

        // Mock: display name queries
        when(jdbcTemplate.query(contains("display_name"), any(RowMapper.class), any()))
                .thenReturn(List.of("Display"));
        when(jdbcTemplate.query(contains("table_name"), any(RowMapper.class), any()))
                .thenReturn(List.of("table"));

        RelationLookupServiceImpl service = new RelationLookupServiceImpl(
                lookupRepo, bindingRepo, viewConfigRepo, jdbcTemplate);

        // When
        List<BoundViewDTO> result = service.getBoundViews(scenario.formId);

        // Then: result set should exactly match bound relation table IDs
        Set<Long> expectedTableIds = scenario.relatedBindings.stream()
                .map(FormTableBinding::getRelationTableId)
                .collect(Collectors.toSet());
        Set<Long> actualTableIds = result.stream()
                .map(BoundViewDTO::tableId)
                .collect(Collectors.toSet());

        assertThat(actualTableIds).isEqualTo(expectedTableIds);
        assertThat(result).hasSameSizeAs(scenario.relatedBindings);
    }

    @Provide
    Arbitrary<BindingScenario> bindingScenarios() {
        Arbitrary<Long> formIdArb = Arbitraries.longs().between(1, 1000);
        Arbitrary<List<Long>> tableIdsArb = Arbitraries.longs().between(1, 10000)
                .list().ofMinSize(0).ofMaxSize(10).uniqueElements();

        return Combinators.combine(formIdArb, tableIdsArb).as((formId, tableIds) -> {
            List<FormTableBinding> bindings = new ArrayList<>();
            for (int i = 0; i < tableIds.size(); i++) {
                FormTableBinding b = new FormTableBinding();
                b.setId((long) (i + 1));
                b.setRelationTableId(tableIds.get(i));
                b.setBindingType(BindingType.RELATED);
                b.setBindingMode(BindingMode.READONLY);
                b.setSortOrder(i);
                bindings.add(b);
            }
            return new BindingScenario(formId, bindings);
        });
    }

    record BindingScenario(Long formId, List<FormTableBinding> relatedBindings) {}
}
