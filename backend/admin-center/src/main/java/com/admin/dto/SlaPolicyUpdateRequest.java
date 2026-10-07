package com.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** New SLA lead time of one Function Unit, in whole calendar days. */
@Data
public class SlaPolicyUpdateRequest {

    /**
     * BigDecimal rather than Integer: Jackson would silently truncate {@code 1.5} into an Integer,
     * saving a lead time nobody entered. {@code @Digits(fraction = 0)} rejects it instead.
     */
    @NotNull
    @Digits(integer = 4, fraction = 0)
    @DecimalMin("1")
    @DecimalMax("3650")
    private BigDecimal leadTimeDays;

    @Size(max = 500)
    private String changeReason;

    /** Only call after bean validation passed. */
    public int leadTimeDaysValue() {
        return leadTimeDays.intValueExact();
    }
}
