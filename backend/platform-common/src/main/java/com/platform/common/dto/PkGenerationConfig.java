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

    /** manual | uuid | autoIncrement | prefixedSequence | dailyDateSequence | monthlyDateSequence | customFormat */
    private String strategy;

    /** perTable | perFunctionUnit | perPrefix | perDay | perMonth */
    private String scope;

    private Long startValue;

    private Integer padWidth;

    private String prefix;

    /** Legacy {@code datePrefixedSequence} date style; migrated to {@code format} at allocate time. */
    private String datePattern;

    /** none | day | month — sequence reset for {@code customFormat}. */
    private String resetPeriod;

    /** Custom template for {@code customFormat}, e.g. {@code {DATETIME:yyyy-dd-MM}-{SEQNUM:4}}. */
    private String format;
}
