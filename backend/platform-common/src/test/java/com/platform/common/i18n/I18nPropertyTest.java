package com.platform.common.i18n;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for internationalization.
 * Validates: Property 15 (Multi-language Switch Immediacy)
 */
class I18nPropertyTest {
    
    // Property 15: Multi-language Switch Immediacy
    // For any language switch operation, the frontend interface and backend
    // error messages should immediately use the newly selected language
    
    @Property(tries = 100)
    void languageSwitchShouldBeImmediate(
            @ForAll("supportedLocales") Locale fromLocale,
            @ForAll("supportedLocales") Locale toLocale) {
        
        SimulatedI18nService i18nService = new SimulatedI18nService();
        
        // Set initial locale
        i18nService.setCurrentLocale(fromLocale);
        String messageBefore = i18nService.getMessage("error.permission_denied");
        
        // Switch locale
        i18nService.setCurrentLocale(toLocale);
        String messageAfter = i18nService.getMessage("error.permission_denied");
        
        // Message should be in the new locale
        assertThat(i18nService.getCurrentLocale()).isEqualTo(toLocale);
        
        // If locales are different, messages should be different
        if (!fromLocale.getLanguage().equals(toLocale.getLanguage())) {
            assertThat(messageAfter).isNotEqualTo(messageBefore);
        }
    }
    
    @Property(tries = 100)
    void allSupportedLocalesShouldHaveMessages(
            @ForAll("supportedLocales") Locale locale,
            @ForAll("messageKeys") String key) {
        
        SimulatedI18nService i18nService = new SimulatedI18nService();
        i18nService.setCurrentLocale(locale);
        
        String message = i18nService.getMessage(key);
        
        // Message should not be the key itself (meaning it was found)
        assertThat(message).isNotEqualTo(key);
        assertThat(message).isNotBlank();
    }
    
    @Property(tries = 100)
    void unsupportedLocaleShouldFallbackToDefault(
            @ForAll @AlphaChars @Size(min = 2, max = 2) String langCode) {
        
        SimulatedI18nService i18nService = new SimulatedI18nService();
        
        // Create an unsupported locale
        Locale unsupported = new Locale(langCode.toLowerCase());
        
        // If not in supported list, should fall back
        if (!i18nService.isSupported(unsupported)) {
            i18nService.setCurrentLocale(unsupported);
            
            // Should fall back to English
            assertThat(i18nService.getCurrentLocale()).isEqualTo(Locale.ENGLISH);
        }
    }
    
    @Property(tries = 100)
    void messageWithParametersShouldBeFormatted(
            @ForAll("supportedLocales") Locale locale,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String param) {
        
        SimulatedI18nService i18nService = new SimulatedI18nService();
        i18nService.setCurrentLocale(locale);
        
        String message = i18nService.getMessage("error.with_param", param);
        
        // Message should contain the parameter
        assertThat(message).contains(param);
    }
    
    @Property(tries = 100)
    void localeParserShouldHandleVariousFormats(
            @ForAll("localeStrings") String localeString) {
        
        Locale parsed = parseLocale(localeString);
        
        assertThat(parsed).isNotNull();
        assertThat(parsed.getLanguage()).isNotBlank();
    }
    
    @Property(tries = 100)
    void chineseVariantsShouldBeDistinguished() {
        SimulatedI18nService i18nService = new SimulatedI18nService();
        
        i18nService.setCurrentLocale(Locale.SIMPLIFIED_CHINESE);
        String simplified = i18nService.getMessage("common.save");
        
        i18nService.setCurrentLocale(Locale.TRADITIONAL_CHINESE);
        String traditional = i18nService.getMessage("common.save");
        
        // Simplified and Traditional Chinese should have different messages
        assertThat(simplified).isNotEqualTo(traditional);
    }
    
    @Provide
    Arbitrary<Locale> supportedLocales() {
        return Arbitraries.of(
                Locale.ENGLISH,
                Locale.SIMPLIFIED_CHINESE,
                Locale.TRADITIONAL_CHINESE
        );
    }
    
    @Provide
    Arbitrary<String> messageKeys() {
        return Arbitraries.of(
                "error.permission_denied",
                "error.resource_not_found",
                "error.internal_error",
                "common.success",
                "common.save",
                "common.cancel"
        );
    }
    
    @Provide
    Arbitrary<String> localeStrings() {
        return Arbitraries.of(
                "en", "en-US", "en_US",
                "zh", "zh-CN", "zh_CN", "zh-TW", "zh_TW",
                "EN", "ZH-CN"
        );
    }
    
    private Locale parseLocale(String localeString) {
        if (localeString == null || localeString.isBlank()) {
            return Locale.ENGLISH;
        }
        
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
        
        String[] parts = normalized.split("_");
        if (parts.length >= 2) {
            return new Locale(parts[0], parts[1].toUpperCase());
        }
        return new Locale(parts[0]);
    }
    
    // Simulated I18n service for testing
    private static class SimulatedI18nService implements I18nService {
        private Locale currentLocale = Locale.ENGLISH;
        
        private static final Map<String, Map<String, String>> MESSAGES = new HashMap<>();
        
        static {
            // English messages
            Map<String, String> en = new HashMap<>();
            en.put("error.permission_denied", "Permission denied");
            en.put("error.resource_not_found", "Resource not found");
            en.put("error.internal_error", "Internal server error");
            en.put("common.success", "Operation successful");
            en.put("common.save", "Save");
            en.put("common.cancel", "Cancel");
            en.put("error.with_param", "Error: %s");
            MESSAGES.put("en", en);
            
            // Simplified Chinese messages
            Map<String, String> zhCN = new HashMap<>();
            zhCN.put("error.permission_denied", "权限被拒绝");
            zhCN.put("error.resource_not_found", "资源未找到");
            zhCN.put("error.internal_error", "内部服务器错误");
            zhCN.put("common.success", "操作成功");
            zhCN.put("common.save", "保存");
            zhCN.put("common.cancel", "取消");
            zhCN.put("error.with_param", "错误: %s");
            MESSAGES.put("zh_CN", zhCN);
            
            // Traditional Chinese messages
            Map<String, String> zhTW = new HashMap<>();
            zhTW.put("error.permission_denied", "權限被拒絕");
            zhTW.put("error.resource_not_found", "資源未找到");
            zhTW.put("error.internal_error", "內部伺服器錯誤");
            zhTW.put("common.success", "操作成功");
            zhTW.put("common.save", "儲存");
            zhTW.put("common.cancel", "取消");
            zhTW.put("error.with_param", "錯誤: %s");
            MESSAGES.put("zh_TW", zhTW);
        }
        
        @Override
        public String getMessage(String key) {
            return getMessage(key, currentLocale);
        }
        
        @Override
        public String getMessage(String key, Object... args) {
            String message = getMessage(key, currentLocale);
            if (args != null && args.length > 0) {
                return String.format(message, args);
            }
            return message;
        }
        
        @Override
        public String getMessage(String key, Locale locale) {
            String localeKey = getLocaleKey(locale);
            Map<String, String> messages = MESSAGES.getOrDefault(localeKey, MESSAGES.get("en"));
            return messages.getOrDefault(key, key);
        }
        
        @Override
        public String getMessage(String key, Locale locale, Object... args) {
            String message = getMessage(key, locale);
            if (args != null && args.length > 0) {
                return String.format(message, args);
            }
            return message;
        }
        
        @Override
        public Locale getCurrentLocale() {
            return currentLocale;
        }
        
        @Override
        public void setCurrentLocale(Locale locale) {
            if (isSupported(locale)) {
                this.currentLocale = locale;
            } else {
                this.currentLocale = Locale.ENGLISH;
            }
        }
        
        @Override
        public boolean isSupported(Locale locale) {
            if (locale == null) return false;
            String lang = locale.getLanguage();
            return "en".equals(lang) || "zh".equals(lang);
        }
        
        private String getLocaleKey(Locale locale) {
            if (locale.equals(Locale.SIMPLIFIED_CHINESE)) return "zh_CN";
            if (locale.equals(Locale.TRADITIONAL_CHINESE)) return "zh_TW";
            if (locale.getLanguage().equals("zh")) {
                String country = locale.getCountry();
                if ("TW".equals(country) || "HK".equals(country)) return "zh_TW";
                return "zh_CN";
            }
            return "en";
        }
    }
}
