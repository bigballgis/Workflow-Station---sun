package com.developer.component.impl;

import com.developer.component.EmailTemplateComponent;
import com.developer.dto.EmailTemplateRequest;
import com.developer.dto.EmailTemplateResponse;
import com.developer.entity.EmailTemplate;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.EmailTemplateRepository;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailTemplateComponentImpl implements EmailTemplateComponent {

    private final EmailTemplateRepository emailTemplateRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final I18nService i18nService;

    @Override
    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> listByFunctionUnitId(Long functionUnitId) {
        ensureFunctionUnitExists(functionUnitId);
        return emailTemplateRepository.findByFunctionUnitIdOrderByNameAsc(functionUnitId).stream()
                .map(EmailTemplateResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmailTemplateResponse getById(Long functionUnitId, Long templateId) {
        return EmailTemplateResponse.fromEntity(getEntity(functionUnitId, templateId));
    }

    @Override
    @Transactional
    public EmailTemplateResponse create(Long functionUnitId, EmailTemplateRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        String name = request.getName().trim();
        if (emailTemplateRepository.existsByFunctionUnitIdAndName(functionUnitId, name)) {
            throw new DeveloperBusinessException("CONFLICT_TEMPLATE_NAME",
                    i18nService.getMessage("email.template.name_conflict", name));
        }

        EmailTemplate template = EmailTemplate.builder()
                .functionUnit(functionUnit)
                .name(name)
                .subject(request.getSubject())
                .bodyHtml(request.getBodyHtml())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        return EmailTemplateResponse.fromEntity(emailTemplateRepository.save(template));
    }

    @Override
    @Transactional
    public EmailTemplateResponse update(Long functionUnitId, Long templateId, EmailTemplateRequest request) {
        EmailTemplate template = getEntity(functionUnitId, templateId);

        String name = request.getName().trim();
        if (emailTemplateRepository.existsByFunctionUnitIdAndNameAndIdNot(functionUnitId, name, templateId)) {
            throw new DeveloperBusinessException("CONFLICT_TEMPLATE_NAME",
                    i18nService.getMessage("email.template.name_conflict", name));
        }

        template.setName(name);
        template.setSubject(request.getSubject());
        template.setBodyHtml(request.getBodyHtml());
        if (request.getEnabled() != null) {
            template.setEnabled(request.getEnabled());
        }

        return EmailTemplateResponse.fromEntity(emailTemplateRepository.save(template));
    }

    @Override
    @Transactional
    public void delete(Long functionUnitId, Long templateId) {
        emailTemplateRepository.delete(getEntity(functionUnitId, templateId));
    }

    private void ensureFunctionUnitExists(Long functionUnitId) {
        if (!functionUnitRepository.existsById(functionUnitId)) {
            throw new ResourceNotFoundException("FunctionUnit", functionUnitId);
        }
    }

    private EmailTemplate getEntity(Long functionUnitId, Long templateId) {
        EmailTemplate template = emailTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", templateId));
        if (!template.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new ResourceNotFoundException("EmailTemplate", templateId);
        }
        return template;
    }
}
