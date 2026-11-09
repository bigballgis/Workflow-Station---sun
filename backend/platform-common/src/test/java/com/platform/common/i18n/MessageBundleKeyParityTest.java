package com.platform.common.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every message of the shared bundle exists in all three languages; a key missing from one bundle makes that
 * language fall back to the key or to another language's text.
 */
class MessageBundleKeyParityTest {

    @Test
    void shouldDefineTheSameKeysInEnglishSimplifiedAndTraditionalChinese() throws IOException {
        Set<String> en = load("i18n/messages_en.properties").stringPropertyNames();

        assertThat(load("i18n/messages_zh_CN.properties").stringPropertyNames()).isEqualTo(en);
        assertThat(load("i18n/messages_zh_TW.properties").stringPropertyNames()).isEqualTo(en);
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleKeyParityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
