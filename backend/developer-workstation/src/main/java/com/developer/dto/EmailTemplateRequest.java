package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailTemplateRequest {

    @NotBlank
    private String name;

    private String subject;

    private String bodyHtml;

    private Boolean enabled = true;
}
