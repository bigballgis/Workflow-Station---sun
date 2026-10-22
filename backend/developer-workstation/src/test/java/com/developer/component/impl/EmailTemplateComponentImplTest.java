package com.developer.component.impl;

import com.developer.component.EmailResourceUsageChecker;
import com.developer.entity.EmailTemplate;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.EmailTemplateRepository;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailTemplateComponentImplTest {

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private I18nService i18nService;

    @Mock
    private EmailResourceUsageChecker emailResourceUsageChecker;

    @InjectMocks
    private EmailTemplateComponentImpl component;

    @Test
    void delete_unused_deletes() {
        EmailTemplate template = sampleTemplate();
        when(emailTemplateRepository.findById(8L)).thenReturn(Optional.of(template));

        component.delete(1L, 8L);

        verify(emailResourceUsageChecker).assertTemplateUnused(1L, 8L);
        verify(emailTemplateRepository).delete(template);
    }

    @Test
    void delete_inUse_doesNotDelete() {
        EmailTemplate template = sampleTemplate();
        when(emailTemplateRepository.findById(8L)).thenReturn(Optional.of(template));
        doThrow(new DeveloperBusinessException("CONFLICT_TEMPLATE_IN_USE", "Template is in use"))
                .when(emailResourceUsageChecker).assertTemplateUnused(1L, 8L);

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> component.delete(1L, 8L));

        assertEquals("CONFLICT_TEMPLATE_IN_USE", ex.getErrorCode());
        verify(emailTemplateRepository, never()).delete(template);
    }

    private static EmailTemplate sampleTemplate() {
        return EmailTemplate.builder()
                .id(8L)
                .name("Notice")
                .functionUnit(FunctionUnit.builder().id(1L).build())
                .build();
    }
}
