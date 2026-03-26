package com.developer.service;

import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.RelationViewConfigRepository;
import com.developer.service.impl.RelationTableBindingServiceImpl;
import com.platform.common.dto.RelationTableDTO;
import com.platform.common.enums.RelationTableStatus;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for available table list filtering.
 *
 * <p><b>Feature: relation-tables, Property 20: 绑定列表仅含已部署表</b></p>
 *
 * For any Manage Table Bindings available table list request, the returned
 * Relation Tables should all have DEPLOYED status.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 */
class RelationTableBindingAvailablePropertyTest {

    /**
     * Property 20: 绑定列表仅含已部署表
     *
     * For any mix of table statuses, getAvailableTables should only return
     * tables with DEPLOYED status.
     *
     * **Validates: Requirements 9.1**
     */
    @Property(tries = 100)
    @Label("Feature: relation-tables, Property 20: Available tables only contain DEPLOYED status")
    void availableTablesShouldOnlyContainDeployed(
            @ForAll("tableCollections") List<RelationTableDTO> allTables) {

        // Simulate: only DEPLOYED tables should be returned by the SQL query
        List<RelationTableDTO> deployedOnly = allTables.stream()
                .filter(t -> t.getStatus() == RelationTableStatus.DEPLOYED)
                .collect(Collectors.toList());

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FormTableBindingRepository bindingRepo = mock(FormTableBindingRepository.class);
        FormDefinitionRepository formRepo = mock(FormDefinitionRepository.class);
        RelationViewConfigRepository viewConfigRepo = mock(RelationViewConfigRepository.class);

        // Mock JdbcTemplate to return only DEPLOYED tables (simulating WHERE status = 'DEPLOYED')
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(RelationTableStatus.DEPLOYED.getCode())))
                .thenReturn(deployedOnly);

        RelationTableBindingService service = new RelationTableBindingServiceImpl(
                bindingRepo, formRepo, viewConfigRepo, jdbcTemplate);

        // When
        List<RelationTableDTO> result = service.getAvailableTables();

        // Then: all returned tables have DEPLOYED status
        assertThat(result).allMatch(t -> t.getStatus() == RelationTableStatus.DEPLOYED);
        assertThat(result).hasSameSizeAs(deployedOnly);
    }

    @Provide
    Arbitrary<List<RelationTableDTO>> tableCollections() {
        Arbitrary<RelationTableDTO> tableArb = Combinators.combine(
                Arbitraries.longs().between(1, 10000),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),
                Arbitraries.of(RelationTableStatus.values())
        ).as((id, name, displayName, status) -> RelationTableDTO.builder()
                .id(id)
                .tableName(name)
                .displayName(displayName)
                .status(status)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(1)
                .build());

        return tableArb.list().ofMinSize(0).ofMaxSize(20);
    }
}
