package com.developer.component;

import com.developer.dto.EmailTemplateRequest;
import com.developer.dto.EmailTemplateResponse;

import java.util.List;

public interface EmailTemplateComponent {

    List<EmailTemplateResponse> listByFunctionUnitId(Long functionUnitId);

    EmailTemplateResponse getById(Long functionUnitId, Long templateId);

    EmailTemplateResponse create(Long functionUnitId, EmailTemplateRequest request);

    EmailTemplateResponse update(Long functionUnitId, Long templateId, EmailTemplateRequest request);

    void delete(Long functionUnitId, Long templateId);
}
