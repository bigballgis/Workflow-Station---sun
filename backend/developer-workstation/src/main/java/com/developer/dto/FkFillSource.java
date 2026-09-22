package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where one declared foreign key on a SUB binding takes its parent row from.
 *
 * <p>{@code fieldId} is {@code dw_field_definitions.id}. {@code ancestorBindingId} is required
 * when {@code kind} is {@code ANCESTOR}. ZIP export uses field/table names instead of these ids.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FkFillSource {

    public static final String KIND_PARENT = "PARENT";
    public static final String KIND_PRIMARY = "PRIMARY";
    public static final String KIND_ANCESTOR = "ANCESTOR";

    private Long fieldId;
    /** Denormalized column name for live catalog JSON; ZIP still remaps by name. */
    private String fieldName;
    private String kind;
    private Long ancestorBindingId;
}
