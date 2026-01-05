package com.platform.common.i18n;

import java.util.Locale;

/**
 * Service interface for internationalization.
 * Validates: Requirements 10.1, 10.2, 10.3, 10.6
 */
public interface I18nService {
    
    /**
     * Get a message by key using the current locale.
     * 
     * @param key Message key
     * @return Localized message
     */
    String getMessage(String key);
    
    /**
     * Get a message by key with parameters using the current locale.
     * 
     * @param key Message key
     * @param args Message parameters
     * @return Localized message
     */
    String getMessage(String key, Object... args);
    
    /**
     * Get a message by key for a specific locale.
     * 
     * @param key Message key
     * @param locale Target locale
     * @return Localized message
     */
    String getMessage(String key, Locale locale);
    
    /**
     * Get a message by key with parameters for a specific locale.
     * 
     * @param key Message key
     * @param locale Target locale
     * @param args Message parameters
     * @return Localized message
     */
    String getMessage(String key, Locale locale, Object... args);
    
    /**
     * Get the current locale.
     * 
     * @return Current locale
     */
    Locale getCurrentLocale();
    
    /**
     * Set the current locale.
     * 
     * @param locale New locale
     */
    void setCurrentLocale(Locale locale);
    
    /**
     * Check if a locale is supported.
     * 
     * @param locale Locale to check
     * @return true if supported
     */
    boolean isSupported(Locale locale);
}
