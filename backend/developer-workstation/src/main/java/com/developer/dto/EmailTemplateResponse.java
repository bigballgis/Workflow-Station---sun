package com.developer.dto;

import com.developer.entity.EmailTemplate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailTemplateResponse {

    private Long id;
    private String name;
    private String subject;
    private String bodyHtml;
    private Boolean enabled;

    public static EmailTemplateResponse fromEntity(EmailTemplate entity) {
        return EmailTemplateResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .subject(entity.getSubject())
                .bodyHtml(entity.getBodyHtml())
                .enabled(entity.getEnabled())
                .build();
    }
}
