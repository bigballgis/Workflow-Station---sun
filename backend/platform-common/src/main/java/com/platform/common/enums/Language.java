package com.platform.common.enums;

import lombok.Getter;

import java.util.Locale;

/**
 * Supported languages for the platform.
 * English is the primary language, with Chinese Simplified and Traditional as secondary.
 */
@Getter
public enum Language {
    
    EN("en", "English", Locale.ENGLISH),
    ZH_CN("zh-CN", "简体中文", Locale.SIMPLIFIED_CHINESE),
    ZH_TW("zh-TW", "繁體中文", Locale.TRADITIONAL_CHINESE);
    
    private final String code;
    private final String displayName;
    private final Locale locale;
    
    Language(String code, String displayName, Locale locale) {
        this.code = code;
        this.displayName = displayName;
        this.locale = locale;
    }
    
    /**
     * Get Language enum from code string
     */
    public static Language fromCode(String code) {
        if (code == null) return EN;
        for (Language lang : values()) {
            if (lang.code.equalsIgnoreCase(code)) {
                return lang;
            }
        }
        return EN; // Default to English
    }
    
    /**
     * Check if the code is a valid language code
     */
    public static boolean isValid(String code) {
        if (code == null) return false;
        for (Language lang : values()) {
            if (lang.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
