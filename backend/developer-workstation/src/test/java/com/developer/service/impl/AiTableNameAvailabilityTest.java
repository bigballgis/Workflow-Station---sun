package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 生成期表名占用检查：别的 FU 的表与 Relation Table 算占用，本 FU 的不算；占用即抛可修违规。 */
@ExtendWith(MockitoExtension.class)
class AiTableNameAvailabilityTest {

    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AiTableNameAvailability availability;

    @BeforeEach
    void setUp() {
        availability = new AiTableNameAvailability(tableDefinitionRepository, jdbcTemplate);
        lenient().when(jdbcTemplate.queryForObject(contains("rt_table_definitions"), eq(Integer.class), anyString()))
                .thenAnswer(inv -> "customer_master".equals(inv.getArgument(2)) ? 1 : 0);
    }

    private static Map<String, Object> design(String... names) {
        return Map.of("tableDefinitions", java.util.Arrays.stream(names)
                .map(n -> (Object) new HashMap<String, Object>(Map.of("tableName", n))).toList());
    }

    @Test
    void takenNamesAreReportedAsARepairableViolationListingOnlyThoseNames() {
        when(tableDefinitionRepository.existsByTableNameIgnoreCaseAndFunctionUnitIdNot("leave_request", 5L))
                .thenReturn(true);
        when(tableDefinitionRepository.existsByTableNameIgnoreCaseAndFunctionUnitIdNot("leave_line", 5L))
                .thenReturn(false);
        when(tableDefinitionRepository.existsByTableNameIgnoreCaseAndFunctionUnitIdNot("customer_master", 5L))
                .thenReturn(false);

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> availability.requireAvailable(5L, design("leave_request", "leave_line", "customer_master")));

        assertEquals(AiTableNameAvailability.TABLE_NAME_TAKEN, e.getErrorCode());
        assertTrue(AiGenerationServiceImpl.REPAIRABLE_ERROR_CODES.contains(e.getErrorCode()));
        assertTrue(e.getMessage().startsWith("Table name(s) [leave_request, customer_master] "), e.getMessage());
    }

    @Test
    void namesFreeOnThePlatformPass() {
        when(tableDefinitionRepository.existsByTableNameIgnoreCaseAndFunctionUnitIdNot("hr_leave_request", 5L))
                .thenReturn(false);

        assertDoesNotThrow(() -> availability.requireAvailable(5L, design("hr_leave_request")));
    }

    @Test
    void dataWithoutTableDefinitionsIsNotChecked() {
        assertDoesNotThrow(() -> availability.requireAvailable(5L, Map.of("formDefinitions", List.of())));
        assertDoesNotThrow(() -> availability.requireAvailable(5L, null));
        assertEquals(List.of(), availability.takenNames(5L, List.of(Map.of("tableName", " "))));
        verifyNoInteractions(tableDefinitionRepository);
    }

    /** null FU id 会让"别的 FU"条件永不成立：直接报错，不静默放行 */
    @Test
    void aMissingFunctionUnitIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> availability.takenNames(null, List.of(Map.of("tableName", "leave_request"))));
    }
}
