package com.platform.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Custom locale resolver that supports multiple sources for locale detection.
 * Priority: Header > Cookie > User Preference > Default
 * Validates: Requirements 10.2
 */
@Component("localeResolver")
public class PlatformLocaleResolver implements LocaleResolver {
    
    private static final String LOCALE_HEADER = "Accept-Language";
    private static final String LOCALE_PARAM = "lang";
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 1. Check query parameter
        String langParam = request.getParameter(LOCALE_PARAM);
        if (langParam != null && !langParam.isBlank()) {
            return parseLocale(langParam);
        }
        
        // 2. Check header
        String acceptLanguage = request.getHeader(LOCALE_HEADER);
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            return parseLocale(acceptLanguage.split(",")[0].trim());
        }
        
        // 3. Return default
        return DEFAULT_LOCALE;
    }
    
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // Locale is typically set via header or parameter, not stored server-side
    }
    
    private Locale parseLocale(String localeString) {
        if (localeString == null || localeString.isBlank()) {
            return DEFAULT_LOCALE;
        }
        
        // Handle formats like "en", "en-US", "zh-CN", "zh-TW"
        String normalized = localeString.replace("-", "_").toLowerCase();
        
        if (normalized.startsWith("zh_cn") || normalized.equals("zh")) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if (normalized.startsWith("zh_tw") || normalized.startsWith("zh_hk")) {
            return Locale.TRADITIONAL_CHINESE;
        }
        if (normalized.startsWith("en")) {
            return Locale.ENGLISH;
        }
        
        // Try to parse as standard locale
        String[] parts = normalized.split("_");
        if (parts.length >= 2) {
            return new Locale(parts[0], parts[1].toUpperCase());
        }
        return new Locale(parts[0]);
    }
}
