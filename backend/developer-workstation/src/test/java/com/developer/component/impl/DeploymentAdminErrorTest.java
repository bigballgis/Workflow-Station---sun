package com.developer.component.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The deploy hint shows Admin's own message, whichever of Admin's error bodies came back (platform access model WP7). */
class DeploymentAdminErrorTest {

    private static String messageOf(HttpStatus status, String body) {
        return DeploymentComponentImpl.extractAdminError(HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));
    }

    @Test
    void aTeamRefusalShowsItsMessage() {
        assertThat(messageOf(HttpStatus.FORBIDDEN, "{\"success\":false,\"error\":{\"code\":\"FU_TEAM_FORBIDDEN\","
                + "\"message\":\"You are not on the lists of team WP7 Team B\",\"traceId\":\"ab12\"}}"))
                .isEqualTo("You are not on the lists of team WP7 Team B");
    }

    @Test
    void anImportFailureShowsItsTopLevelMessage() {
        assertThat(messageOf(HttpStatus.BAD_REQUEST, "{\"success\":false,\"message\":\"Package team WP7 is disabled\"}"))
                .isEqualTo("Package team WP7 is disabled");
    }

    @Test
    void anAccessInterceptorRefusalShowsItsMessage() {
        assertThat(messageOf(HttpStatus.FORBIDDEN,
                "{\"success\":false,\"error\":{\"code\":\"PERM_ACCESS_DENIED\",\"message\":\"No permission\"}}"))
                .isEqualTo("No permission");
    }

    @Test
    void aBodyThatIsNotJsonIsShownAsItIs() {
        assertThat(messageOf(HttpStatus.BAD_GATEWAY, "<html>Bad Gateway</html>")).isEqualTo("<html>Bad Gateway</html>");
    }
}
