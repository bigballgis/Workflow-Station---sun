package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Bounded directory label lookup; never used to decide assignment eligibility. */
public record UserSummariesRequest(
        @NotNull @Size(min = 1, max = 200) List<@NotBlank @Size(max = 64) String> userIds) { }
