package com.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class VaultCatalogCorsProcessorTest {

    private final VaultCatalogCorsProcessor processor = new VaultCatalogCorsProcessor();

    @Test
    void environmentVariableWriteDoesNotEmitCorsHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = processor.processRequest(
                allowedLocalhost(),
                catalogRequest("POST", "/environment-variables"),
                response);

        assertThat(allowed).isTrue();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void vaultPasswordReadDoesNotEmitCorsHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        processor.processRequest(
                allowedLocalhost(),
                catalogRequest("GET", "/internal/environment-variables/vault-password"),
                response);

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void otherAdminApiKeepsCors() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        processor.processRequest(
                allowedLocalhost(),
                catalogRequest("GET", "/users"),
                response);

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");
    }

    private static MockHttpServletRequest catalogRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/admin" + path);
        request.setContextPath("/api/v1/admin");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
        return request;
    }

    private static CorsConfiguration allowedLocalhost() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        return config;
    }
}
