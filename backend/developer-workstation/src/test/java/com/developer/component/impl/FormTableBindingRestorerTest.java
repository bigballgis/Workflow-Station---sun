package com.developer.component.impl;

import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormTableBindingRestorerTest {

    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    @Mock
    private FormTableBindingRepository formTableBindingRepository;
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    @Mock
    private SubTableViewConfigRepository subTableViewConfigRepository;

    private FormTableBindingRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new FormTableBindingRestorer(
                formDefinitionRepository,
                formTableBindingRepository,
                tableDefinitionRepository,
                subTableViewConfigRepository);
    }

    @Test
    void repairFormIfMissingBindings_rebuildsMcyCaseFormBindings() {
        TableDefinition caseTable = table("HMDC_Case", TableType.MAIN, "case_number", "legal_hold");
        TableDefinition txTable = table("HMDC_Transaction", TableType.SUB,
                "row_id", "card_number", "case_type", "merchant_name");
        TableDefinition attachmentTable = table("HMDC_Attachment", TableType.SUB, "row_id", "file");

        FormDefinition form = FormDefinition.builder()
                .id(50019L)
                .formName("HMDC Case Form")
                .formType(FormType.PROCESS)
                .configJson(mcyCaseFormConfigJson())
                .build();

        when(formTableBindingRepository.countByFormId(50019L)).thenReturn(0L);
        when(formTableBindingRepository.save(any(FormTableBinding.class)))
                .thenAnswer(inv -> {
                    FormTableBinding b = inv.getArgument(0);
                    if (b.getId() == null) {
                        b.setId(switch (b.getBindingType()) {
                            case PRIMARY -> 9001L;
                            case SUB -> b.getTable() == attachmentTable ? 9003L : 9002L;
                            case RELATED -> 9004L;
                            default -> 9099L;
                        });
                    }
                    return b;
                });
        when(subTableViewConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(formDefinitionRepository.save(any(FormDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean repaired = restorer.repairFormIfMissingBindings(
                form, List.of(caseTable, txTable, attachmentTable));

        assertThat(repaired).isTrue();
        ArgumentCaptor<FormTableBinding> captor = ArgumentCaptor.forClass(FormTableBinding.class);
        verify(formTableBindingRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(b -> b.getBindingType() == BindingType.PRIMARY
                && b.getTable() == caseTable);
        assertThat(captor.getAllValues()).anyMatch(b -> b.getBindingType() == BindingType.SUB
                && b.getTable() == txTable);
        assertThat(captor.getAllValues()).anyMatch(b -> b.getBindingType() == BindingType.SUB
                && b.getTable() == attachmentTable);

        Map<String, Object> subForms = (Map<String, Object>) form.getConfigJson().get("subForms");
        assertThat(subForms).containsKeys("9002", "9003");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rule = (List<Map<String, Object>>) form.getConfigJson().get("rule");
        assertThat(rule.stream()
                .filter(n -> "subTable".equals(n.get("type")))
                .map(n -> ((Number) n.get("_bindingId")).longValue())
                .toList()).containsExactlyInAnyOrder(9002L, 9003L);
    }

    /**
     * A DETAIL form renders one row of a sub-table, because MAIN rows open the request detail page
     * instead. This repair runs on every form load, so binding one to the MAIN table would keep
     * silently overwriting the developer's own choice of sub-table.
     */
    @Test
    void repairFormIfMissingBindings_bindsADetailFormToItsSubTableNotTheMainTable() {
        TableDefinition mainTable = table("meeting_main", TableType.MAIN, "title", "meeting_date");
        TableDefinition participants = table("participants", TableType.SUB,
                "row_id", "name", "assignee", "task_status");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", List.of(
                Map.of("type", "input", "field", "name"),
                Map.of("type", "input", "field", "assignee"),
                Map.of("type", "subTable", "_bindingId", 900)
        ));
        config.put("subForms", Map.of("900", Map.of("rule", List.of(Map.of("field", "task_status")))));

        FormDefinition form = FormDefinition.builder()
                .id(50616L)
                .formName("Participants Detail")
                .formType(FormType.DETAIL)
                .configJson(config)
                .build();

        when(formTableBindingRepository.countByFormId(50616L)).thenReturn(0L);
        when(formTableBindingRepository.save(any(FormTableBinding.class)))
                .thenAnswer(inv -> {
                    FormTableBinding b = inv.getArgument(0);
                    if (b.getId() == null) {
                        b.setId(7001L);
                    }
                    return b;
                });

        restorer.repairFormIfMissingBindings(form, List.of(mainTable, participants));

        ArgumentCaptor<FormTableBinding> captor = ArgumentCaptor.forClass(FormTableBinding.class);
        verify(formTableBindingRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(b -> b.getBindingType() == BindingType.PRIMARY)
                .allMatch(b -> b.getTable() == participants);
        assertThat(captor.getAllValues())
                .noneMatch(b -> b.getBindingType() == BindingType.PRIMARY && b.getTable() == mainTable);
    }

    /**
     * Inventing a wrong primary binding is worse than leaving it unset: the developer can still
     * pick the right sub-table, but a silent MAIN binding hides the form from its own group.
     */
    @Test
    void repairFormIfMissingBindings_leavesADetailFormUnboundWhenNoSubTableMatches() {
        TableDefinition mainTable = table("meeting_main", TableType.MAIN, "title");
        TableDefinition unrelatedSub = table("attachment", TableType.SUB, "row_id", "file");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", List.of(
                Map.of("type", "input", "field", "nothing_matches_here"),
                Map.of("type", "subTable", "_bindingId", 901)
        ));

        FormDefinition form = FormDefinition.builder()
                .id(50617L)
                .formName("Orphan Detail")
                .formType(FormType.DETAIL)
                .configJson(config)
                .build();

        when(formTableBindingRepository.countByFormId(50617L)).thenReturn(0L);

        restorer.repairFormIfMissingBindings(form, List.of(mainTable, unrelatedSub));

        // Nothing is written at all: no sub-table matches, and the MAIN table is not a substitute.
        verify(formTableBindingRepository, never()).save(any(FormTableBinding.class));
    }

    private static TableDefinition table(String name, TableType type, String... fields) {
        TableDefinition table = TableDefinition.builder()
                .id(name.hashCode() & 0xffffL)
                .tableName(name)
                .tableType(type)
                .build();
        int order = 0;
        for (String fieldName : fields) {
            table.getFieldDefinitions().add(FieldDefinition.builder()
                    .fieldName(fieldName)
                    .dataType(DataType.VARCHAR)
                    .sortOrder(order++)
                    .tableDefinition(table)
                    .build());
        }
        return table;
    }

    private static Map<String, Object> mcyCaseFormConfigJson() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", List.of(
                Map.of("type", "input", "field", "case_number"),
                Map.of("type", "subTable", "_bindingId", 271),
                Map.of("type", "subTable", "_bindingId", 273)
        ));
        config.put("subListViews", Map.of(
                "271", Map.of("columns", List.of(
                        Map.of("fieldName", "row_id", "columnType", "field"),
                        Map.of("fieldName", "card_number", "columnType", "field"))),
                "273", Map.of("columns", List.of(
                        Map.of("fieldName", "file", "columnType", "field")))
        ));
        config.put("subForms", Map.of(
                "271", Map.of("rule", List.of(Map.of("field", "card_number"))),
                "273", Map.of("rule", List.of(Map.of("field", "file")))
        ));
        config.put("relationViews", Map.of(
                "272", Map.of("allFields", List.of(
                        Map.of("fieldName", "username"),
                        Map.of("fieldName", "full_name")))
        ));
        return config;
    }
}
