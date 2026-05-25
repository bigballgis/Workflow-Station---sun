package com.platform.common.i18n.impl;

import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Implementation of I18nService.
 * Validates: Requirements 10.1, 10.2, 10.3, 10.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {
    
    private static final Set<Locale> SUPPORTED_LOCALES = Set.of(
            Locale.ENGLISH,
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE
    );
    
    private final MessageSource messageSource;
    
    @Override
    public String getMessage(String key) {
        return getMessage(key, getCurrentLocale());
    }
    
    @Override
    public String getMessage(String key, Object... args) {
        return getMessage(key, getCurrentLocale(), args);
    }
    
    @Override
    public String getMessage(String key, Locale locale) {
        return getMessage(key, locale, (Object[]) null);
    }
    
    @Override
    public String getMessage(String key, Locale locale, Object... args) {
        Locale primary = normalizeLocale(locale);
        String message = lookupMessage(key, args, primary);
        if (message != null) {
            return message;
        }
        if (!Locale.ENGLISH.equals(primary)) {
            message = lookupMessage(key, args, Locale.ENGLISH);
            if (message != null) {
                return message;
            }
        }
        log.warn("Message not found for key: {} in locale: {}", key, primary);
        return key;
    }

    private String lookupMessage(String key, Object[] args, Locale locale) {
        try {
            return messageSource.getMessage(key, args, null, locale);
        } catch (Exception e) {
            log.debug("Message lookup failed for key {} locale {}: {}", key, locale, e.toString());
            return null;
        }
    }

    /**
     * Map user language codes (e.g. zh_CN from DB) and Accept-Language variants to supported bundles.
     */
    private Locale normalizeLocale(Locale locale) {
        if (locale == null || !isSupported(locale)) {
            return Locale.ENGLISH;
        }
        String language = locale.getLanguage();
        if ("zh_cn".equalsIgnoreCase(language) && locale.getCountry().isEmpty()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if ("zh_tw".equalsIgnoreCase(language) && locale.getCountry().isEmpty()) {
            return Locale.TRADITIONAL_CHINESE;
        }
        return locale;
    }
    
    @Override
    public Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }
    
    @Override
    public void setCurrentLocale(Locale locale) {
        if (isSupported(locale)) {
            LocaleContextHolder.setLocale(locale);
            log.debug("Locale changed to: {}", locale);
        } else {
            log.warn("Unsupported locale: {}, using default", locale);
            LocaleContextHolder.setLocale(Locale.ENGLISH);
        }
    }
    
    @Override
    public boolean isSupported(Locale locale) {
        if (locale == null) {
            return false;
        }
        return SUPPORTED_LOCALES.stream()
                .anyMatch(supported -> 
                        supported.getLanguage().equals(locale.getLanguage()));
    }
}
