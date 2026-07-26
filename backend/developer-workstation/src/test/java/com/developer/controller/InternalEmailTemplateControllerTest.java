package com.developer.controller;

import com.developer.component.EmailTemplateComponent;
import com.developer.dto.EmailTemplateResponse;
import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalEmailTemplateControllerTest {

    @Mock
    private EmailTemplateComponent emailTemplateComponent;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @InjectMocks
    private InternalEmailTemplateController controller;

    @Test
    void getTemplate_acceptsNumericFunctionUnitId() {
        when(emailTemplateComponent.getById(1L, 9L)).thenReturn(EmailTemplateResponse.builder()
                .id(9L)
                .name("Welcome")
                .subject("Subj")
                .bodyHtml("<b>Body</b>")
                .enabled(true)
                .build());

        ResponseEntity<Map<String, Object>> response = controller.getTemplate("1", 9L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("subject", "Subj");
        assertThat(response.getBody()).containsEntry("bodyHtml", "<b>Body</b>");
    }

    @Test
    void getTemplate_resolvesFunctionUnitCode() {
        when(functionUnitRepository.findByCode("fu-20260505-thwmut"))
                .thenReturn(Optional.of(FunctionUnit.builder().id(48L).code("fu-20260505-thwmut").build()));
        when(emailTemplateComponent.getById(48L, 2L)).thenReturn(EmailTemplateResponse.builder()
                .id(2L)
                .name("test")
                .subject("Hello")
                .bodyHtml("<p>Hi</p>")
                .enabled(true)
                .build());

        ResponseEntity<Map<String, Object>> response = controller.getTemplate("fu-20260505-thwmut", 2L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("subject", "Hello");
    }

    @Test
    void getTemplate_returns404WhenDisabled() {
        when(emailTemplateComponent.getById(1L, 9L)).thenReturn(EmailTemplateResponse.builder()
                .id(9L)
                .name("Off")
                .subject("S")
                .bodyHtml("B")
                .enabled(false)
                .build());

        ResponseEntity<Map<String, Object>> response = controller.getTemplate("1", 9L);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getTemplate_unknownCodeThrowsNotFound() {
        when(functionUnitRepository.findByCode("missing-fu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getTemplate("missing-fu", 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
