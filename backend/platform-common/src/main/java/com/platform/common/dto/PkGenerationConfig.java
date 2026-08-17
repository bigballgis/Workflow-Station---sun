package com.platform.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PK generation strategy stored on field metadata (PRD §5.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PkGenerationConfig {

    /** manual | uuid | autoIncrement | prefixedSequence | dailyDateSequence | monthlyDateSequence */
    private String strategy;

    /** perTable | perFunctionUnit | perPrefix | perDay | perMonth */
    private String scope;

    private Long startValue;

    private Integer padWidth;

    private String prefix;
}
