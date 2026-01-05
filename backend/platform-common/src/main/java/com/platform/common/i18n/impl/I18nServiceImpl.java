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
        try {
            Locale effectiveLocale = isSupported(locale) ? locale : Locale.ENGLISH;
            return messageSource.getMessage(key, args, effectiveLocale);
        } catch (Exception e) {
            log.warn("Message not found for key: {} in locale: {}", key, locale);
            return key;
        }
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
