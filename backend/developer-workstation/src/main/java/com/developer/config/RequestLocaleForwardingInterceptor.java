package com.developer.config;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Locale;

/**
 * Sends the language DW is answering in to the platform services it calls (Admin Center, the engine), so their
 * messages come back in the same language. Without it a failed deployment read "导入失败: Team Payment Ops
 * (PAYMENT_OPS) is disabled; enable it first": DW's step text in the user's language, Admin's reason in English.
 *
 * <p>The language is the one resolved for the current request ({@code PlatformLocaleResolver}, from its
 * {@code Accept-Language}); the deployment worker thread carries it over from the request that started it
 * ({@code DeploymentComponentImpl}). Work that runs outside any request has no language of its own and states the
 * platform default, English, rather than whatever locale the JVM happens to run with. A header the call site set
 * itself is kept.
 */
public class RequestLocaleForwardingInterceptor implements ClientHttpRequestInterceptor {

    /** Same default as {@code PlatformLocaleResolver} and {@code I18nServiceImpl}. */
    static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        HttpHeaders headers = request.getHeaders();
        if (!headers.containsKey(HttpHeaders.ACCEPT_LANGUAGE)) {
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, currentLocale().toLanguageTag());
        }
        return execution.execute(request, body);
    }

    private static Locale currentLocale() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        Locale locale = context != null ? context.getLocale() : null;
        return locale != null ? locale : DEFAULT_LOCALE;
    }
}
