package com.platform.security.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Secret and token lifetimes have no in-code default: an unbound value must stop the service at startup. */
class JwtPropertiesTest {

    private static final String SECRET = "test-only-secret-with-at-least-32-bytes-0123456789";

    @Test
    void missingAccessLifetimeFailsStartupNamingTheVariable() {
        JwtProperties props = configured();
        props.setExpirationMs(0);

        assertThatThrownBy(props::validateRequiredSettings)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_EXPIRATION");
    }

    @Test
    void missingRefreshLifetimeFailsStartupNamingTheVariable() {
        JwtProperties props = configured();
        props.setRefreshExpirationMs(0);

        assertThatThrownBy(props::validateRequiredSettings)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_REFRESH_EXPIRATION");
    }

    @Test
    void unresolvedOrBlankSecretFailsStartupNamingTheVariable() {
        for (String secret : new String[] {"${JWT_SECRET}", "", "  "}) {
            JwtProperties props = configured();
            props.setSecret(secret);

            assertThatThrownBy(props::validateRequiredSettings)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    void configuredSettingsPassAndDriveTheSessionCookieAge() {
        JwtProperties props = configured();

        assertThatCode(props::validateRequiredSettings).doesNotThrowAnyException();
        assertThat(props.getSessionCookieMaxAgeSeconds()).isEqualTo(28_800);
    }

    private static JwtProperties configured() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpirationMs(900_000);
        props.setRefreshExpirationMs(28_800_000);
        return props;
    }
}
