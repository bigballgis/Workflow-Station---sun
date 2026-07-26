package com.workflow.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperWorkstationEmailTemplateClientTest {

    @Mock
    private RestTemplate restTemplate;

    private DeveloperWorkstationEmailTemplateClient client;

    @BeforeEach
    void setUp() {
        client = new DeveloperWorkstationEmailTemplateClient(restTemplate);
        ReflectionTestUtils.setField(client, "developerWorkstationBaseUrl", "http://developer-workstation:8080");
    }

    @Test
    void getTemplate_buildsInternalUrlAndMapsBody() {
        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "subject", "Hello ${name}",
                        "bodyHtml", "<p>Hi</p>"
                )));

        Optional<DeveloperWorkstationEmailTemplateClient.EmailTemplateContent> result =
                client.getTemplate("42", "7");

        assertThat(result).isPresent();
        assertThat(result.get().subject()).isEqualTo("Hello ${name}");
        assertThat(result.get().bodyHtml()).isEqualTo("<p>Hi</p>");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class));
        assertThat(urlCaptor.getValue())
                .isEqualTo("http://developer-workstation:8080/api/v1/internal/function-units/42/email-templates/7");
    }

    @Test
    void getTemplate_returnsEmptyOnBlankIds() {
        assertThat(client.getTemplate("", "1")).isEmpty();
        assertThat(client.getTemplate("1", null)).isEmpty();
    }
}
