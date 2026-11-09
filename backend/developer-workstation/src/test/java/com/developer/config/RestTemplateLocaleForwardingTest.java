package com.developer.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DW's calls to Admin Center carry the language DW answers in, so Admin's reasons (e.g. why an import failed) come
 * back in the same language as DW's own text (WP11a P3).
 */
class RestTemplateLocaleForwardingTest {

    private static final String ADMIN = "http://admin-center:8090";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplateConfig config = new RestTemplateConfig();
        ReflectionTestUtils.setField(config, "adminCenterUrl", ADMIN);
        ReflectionTestUtils.setField(config, "serviceInternalToken", "test-token");
        restTemplate = config.restTemplate(config.circuitBreakerRegistry(new SimpleMeterRegistry()));
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void forwardsTheRequestLanguageToAdminCenter() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        server.expect(requestTo(ADMIN + "/api/v1/admin/function-units-import/import"))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andRespond(withSuccess());

        restTemplate.postForEntity(ADMIN + "/api/v1/admin/function-units-import/import", "{}", String.class);

        server.verify();
    }

    @Test
    void forwardsTraditionalChinese() {
        LocaleContextHolder.setLocale(Locale.TRADITIONAL_CHINESE);
        server.expect(requestTo(ADMIN + "/api/v1/admin/business-units"))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "zh-TW"))
                .andRespond(withSuccess());

        restTemplate.getForEntity(ADMIN + "/api/v1/admin/business-units", String.class);

        server.verify();
    }

    @Test
    void outsideAnyRequestStatesThePlatformDefaultNotTheJvmLocale() {
        Locale jvmDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
            LocaleContextHolder.resetLocaleContext();
            server.expect(requestTo(ADMIN + "/api/v1/admin/business-units"))
                    .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andRespond(withSuccess());

            restTemplate.getForEntity(ADMIN + "/api/v1/admin/business-units", String.class);

            server.verify();
        } finally {
            Locale.setDefault(jvmDefault);
        }
    }

    @Test
    void keepsALanguageTheCallSiteSetItself() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
        server.expect(requestTo(ADMIN + "/api/v1/admin/business-units"))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andRespond(withSuccess());

        restTemplate.exchange(ADMIN + "/api/v1/admin/business-units", HttpMethod.GET, new HttpEntity<>(headers),
                String.class);

        server.verify();
    }
}
