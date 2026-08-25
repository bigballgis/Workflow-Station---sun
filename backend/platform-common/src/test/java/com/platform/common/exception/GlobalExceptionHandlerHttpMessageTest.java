package com.platform.common.exception;

import com.platform.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerHttpMessageTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest request =
            new ServletWebRequest(new MockHttpServletRequest("POST", "/list-query"));

    @Test
    void compactCtorIllegalArgumentWrappedByJacksonIs400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Cannot construct instance",
                new IllegalArgumentException("page must be >= 0"));

        ResponseEntity<ApiResponse<?>> response = handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("VAL_INVALID_ARGUMENT");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("page must be >= 0");
    }

    @Test
    void malformedJsonIs400NotInternalError() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character");

        ResponseEntity<ApiResponse<?>> response = handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("VAL_INVALID_INPUT");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Invalid request body");
    }
}
