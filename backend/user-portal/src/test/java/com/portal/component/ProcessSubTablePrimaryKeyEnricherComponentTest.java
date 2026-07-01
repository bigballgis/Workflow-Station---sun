package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.fk.PrimaryKeyAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProcessSubTablePrimaryKeyEnricherComponentTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PrimaryKeyAllocationService primaryKeyAllocationService;
    @Mock
    private PortalPrimaryKeyAllocationComponent portalPrimaryKeyAllocationComponent;

    private ProcessSubTablePrimaryKeyEnricherComponent enricher;

    @BeforeEach
    void setUp() {
        enricher = new ProcessSubTablePrimaryKeyEnricherComponent(
                jdbcTemplate,
                primaryKeyAllocationService,
                new ObjectMapper(),
                portalPrimaryKeyAllocationComponent);
    }

    @Test
    void allocateMissingPrimaryKeysInVariables_withoutSubTables_isNoOp() {
        enricher.allocateMissingPrimaryKeysInVariables("fu-code", Map.of("title", "x"));
        verifyNoInteractions(primaryKeyAllocationService);
    }
}
