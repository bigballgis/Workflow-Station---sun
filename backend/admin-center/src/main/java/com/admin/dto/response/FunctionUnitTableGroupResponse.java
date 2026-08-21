package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight Function Unit grouping entry for the Relation Tables nav sidebar:
 * one Function Unit that has at least one deployed relation table, with a count.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitTableGroupResponse {

    private String functionUnitId;
    private String functionUnitCode;
    private String functionUnitName;
    private long tableCount;
}
