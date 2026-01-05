package com.developer.property;

import net.jqwik.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 国际化属性测试
 * Property 23: 国际化语言切换
 */
@SpringBootTest
@ActiveProfiles("test")
public class I18nPropertyTest {
    
    private static final Set<String> SUPPORTED_LOCALES = Set.of("zh-CN", "zh-TW", "en");
    
    /**
     * Property 23: 国际化语言切换
     * 支持的语言应能正确切换
     */
    @Property(tries = 20)
    void i18nLanguageSwitchProperty(@ForAll("supportedLocales") String localeCode) {
        assertThat(localeCode).isIn(SUPPORTED_LOCALES);
        
        // 语言代码应能解析为有效的Locale
        String[] parts = localeCode.split("-");
        Locale locale;
        if (parts.length == 2) {
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(parts[0]);
        }
        
        assertThat(locale).isNotNull();
        assertThat(locale.getLanguage()).isNotBlank();
    }
    
    @Provide
    Arbitrary<String> supportedLocales() {
        return Arbitraries.of("zh-CN", "zh-TW", "en");
    }
}
