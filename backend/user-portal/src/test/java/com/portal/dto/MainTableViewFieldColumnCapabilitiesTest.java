package com.portal.dto;

import com.platform.common.list.ListColumnMeta;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MainTableViewFieldColumnCapabilitiesTest {

    @Test
    void booleanColumnsCarryTrueFalseOptionsOntoTheViewDto() {
        ListColumnMeta cap = ListColumnMeta.of("legal_hold", "Legal Hold", ListColumnMeta.Kind.BOOLEAN);
        MainTableViewFieldColumn column = MainTableViewFieldColumn.applyListCapabilities(
                        MainTableViewFieldColumn.builder()
                                .fieldName("legal_hold")
                                .displayLabel("Legal Hold"),
                        cap)
                .build();

        assertThat(column.kind()).isEqualTo(ListColumnMeta.Kind.BOOLEAN);
        assertThat(column.filterable()).isTrue();
        assertThat(column.operators()).containsExactly("eq", "ne", "isNull", "isNotNull");
        assertThat(column.options())
                .extracting(ListColumnMeta.Option::value)
                .containsExactly("true", "false");
    }

    @Test
    void enumColumnsCarryTheClosedChoiceListOntoTheViewDto() {
        ListColumnMeta cap = ListColumnMeta.withOptions(
                "process_status",
                "Status",
                ListColumnMeta.Kind.ENUM,
                java.util.List.of(
                        new ListColumnMeta.Option("RUNNING", "Running"),
                        new ListColumnMeta.Option("COMPLETED", "Completed"),
                        new ListColumnMeta.Option("WITHDRAWN", "Withdrawn")));
        MainTableViewFieldColumn column = MainTableViewFieldColumn.applyListCapabilities(
                        MainTableViewFieldColumn.builder()
                                .fieldName("process_status")
                                .displayLabel("Status"),
                        cap)
                .build();

        assertThat(column.options())
                .extracting(ListColumnMeta.Option::value)
                .containsExactly("RUNNING", "COMPLETED", "WITHDRAWN");
    }
}
