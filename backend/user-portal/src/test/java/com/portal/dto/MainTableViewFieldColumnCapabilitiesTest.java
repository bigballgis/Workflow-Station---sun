package com.portal.dto;

import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTableViewFieldColumnCapabilitiesTest {

    @Test
    void booleanColumnsCarryTrueFalseOptionsOntoTheViewDto() {
        PortalListColumnMeta cap = PortalListColumnMeta.of("legal_hold", "Legal Hold", PortalListColumnMeta.Kind.BOOLEAN);
        MainTableViewFieldColumn column = MainTableViewFieldColumn.applyListCapabilities(
                        MainTableViewFieldColumn.builder()
                                .fieldName("legal_hold")
                                .displayLabel("Legal Hold"),
                        cap)
                .build();

        assertThat(column.kind()).isEqualTo(PortalListColumnMeta.Kind.BOOLEAN);
        assertThat(column.filterable()).isTrue();
        assertThat(column.operators()).containsExactly("eq", "ne", "isNull", "isNotNull");
        assertThat(column.options())
                .extracting(PortalListColumnMeta.Option::value)
                .containsExactly("true", "false");
    }

    @Test
    void enumColumnsCarryTheClosedChoiceListOntoTheViewDto() {
        PortalListColumnMeta cap = PortalListColumnMeta.withOptions(
                "process_status",
                "Status",
                PortalListColumnMeta.Kind.ENUM,
                java.util.List.of(
                        new PortalListColumnMeta.Option("RUNNING", "Running"),
                        new PortalListColumnMeta.Option("COMPLETED", "Completed"),
                        new PortalListColumnMeta.Option("WITHDRAWN", "Withdrawn")));
        MainTableViewFieldColumn column = MainTableViewFieldColumn.applyListCapabilities(
                        MainTableViewFieldColumn.builder()
                                .fieldName("process_status")
                                .displayLabel("Status"),
                        cap)
                .build();

        assertThat(column.options())
                .extracting(PortalListColumnMeta.Option::value)
                .containsExactly("RUNNING", "COMPLETED", "WITHDRAWN");
    }
}
