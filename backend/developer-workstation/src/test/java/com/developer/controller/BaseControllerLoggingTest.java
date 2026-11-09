package com.developer.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An expected refusal (4xx) is one WARN line without a stack trace; only an unexpected failure (5xx) is logged as
 * ERROR with its stack trace (out-of-scope list row 46: WP6 made refusals such as WORKSPACE_EXACTLY_ONE common, and
 * each one used to print a full ERROR stack).
 */
class BaseControllerLoggingTest {

    private final BaseController controller = new BaseController() {
    };
    private final Logger logger = (Logger) LoggerFactory.getLogger(BaseController.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void aBusinessRefusalIsAWarningWithoutStackTrace() {
        HttpStatus status = statusOf(new DeveloperBusinessException("WORKSPACE_EXACTLY_ONE", "Pick exactly one team"));

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
        assertOnlyWarningsWithoutStackTrace();
    }

    @Test
    void aWorkspaceRefusalIsAWarningWithoutStackTrace() {
        HttpStatus status = statusOf(new FunctionUnitWorkspaceAccessDeniedException("Not a member of this workspace"));

        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN);
        assertOnlyWarningsWithoutStackTrace();
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("Not a member of this workspace"));
    }

    @Test
    void aMissingResourceIsAWarningWithoutStackTrace() {
        HttpStatus status = statusOf(new ResourceNotFoundException("Form", 7L));

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
        assertOnlyWarningsWithoutStackTrace();
    }

    @Test
    void anUnexpectedFailureIsStillAnErrorWithItsStackTrace() {
        HttpStatus status = statusOf(new IllegalStateException("boom"));

        assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        List<ILoggingEvent> errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getThrowableProxy()).isNotNull();
        assertThat(errors.get(0).getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    private HttpStatus statusOf(Exception thrown) {
        return HttpStatus.valueOf(controller.handleRequest(() -> {
            throw thrown;
        }).getStatusCode().value());
    }

    private void assertOnlyWarningsWithoutStackTrace() {
        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
