package com.platform.common.i18n;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Message bundle wiring for every service that scans {@code com.platform.common}.
 *
 * <p>The bundles live in this module's {@code i18n/} folder, which means that at runtime they sit
 * inside {@code BOOT-INF/lib/platform-common-*.jar} of each executable jar. Spring Boot's default
 * {@link org.springframework.context.support.ResourceBundleMessageSource} resolves bundles through
 * {@code ClassLoader.getResource}, and the Boot nested-jar loader does not expose entries of a jar
 * within a jar that way — so every lookup missed and {@code I18nService} fell back to returning the
 * raw key. Users saw {@code form.name_exists} instead of "Form name already exists: test".
 *
 * <p>{@link ReloadableResourceBundleMessageSource} reads through Spring's resource loader, which
 * does understand nested jars, so the same basename resolves in a packaged app as it does in tests
 * and in an IDE.
 */
@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource(
            @Value("${spring.messages.basename:i18n/messages}") String basenames,
            @Value("${spring.messages.encoding:UTF-8}") String encoding) {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        // Same comma-separated basename property Boot's own auto-configuration honours, so a
        // service can still add its own bundle without touching this class.
        for (String basename : basenames.split(",")) {
            String trimmed = basename.trim();
            if (!trimmed.isEmpty()) {
                source.addBasenames("classpath:" + trimmed);
            }
        }
        source.setDefaultEncoding(encoding);
        // Deliberately NOT setUseCodeAsDefaultMessage: I18nService relies on a failed lookup to
        // fall back from the request locale to English. Returning the code here would look like a
        // hit and strand a zh-CN caller with the raw key even when an English message exists.
        return source;
    }
}
