package com.workflow.email.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.workflow.email.action.ActionEmailDeliveryRepository.PendingDelivery;
import com.workflow.service.ConfiguredEmailSender;
import com.workflow.service.EmailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends due post-action emails from {@code we_action_email_deliveries}.
 *
 * <p>Transient failures (mail server / admin-center unreachable) are retried with exponential
 * backoff up to {@code maxAttempts}; configuration errors fail immediately. Failures never reach
 * the user who ran the Action — they are logged at ERROR and kept in the ledger's
 * {@code last_error}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionEmailDispatchScheduler {

    /** Small batch: the default scheduler pool is shared with the email monitor poll. */
    static final int BATCH_SIZE = 10;
    static final long BASE_BACKOFF_SECONDS = 30;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final ActionEmailDeliveryRepository deliveryRepository;
    private final ActionEmailVariableLoader variableLoader;
    private final ConfiguredEmailSender configuredEmailSender;
    private final ObjectMapper objectMapper;

    @Value("${workflow.action-email.max-attempts:3}")
    private int maxAttempts = 3;

    @Scheduled(fixedDelayString = "${workflow.action-email.dispatch-delay-ms:5000}")
    @SchedulerLock(name = "ActionEmail_dispatch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT2S")
    public void dispatch() {
        for (PendingDelivery delivery : deliveryRepository.findDue(BATCH_SIZE)) {
            if (deliveryRepository.claim(delivery.eventId())) {
                deliverOne(delivery);
            }
        }
    }

    void deliverOne(PendingDelivery delivery) {
        String eventId = delivery.eventId();
        int attempt = delivery.attempts() + 1;
        ActionEmailRequestedEvent event;
        try {
            event = objectMapper.readValue(delivery.payloadJson(), ActionEmailRequestedEvent.class);
        } catch (Exception e) {
            fail(eventId, null, "unreadable payload: " + e.getMessage());
            return;
        }
        String logContext = "action=" + event.getActionId() + " event=" + eventId
                + " process=" + event.getProcessInstanceId();
        try {
            configuredEmailSender.send(logContext, toConfig(event.getPostEmail()), variableLoader.load(event));
            deliveryRepository.markSent(eventId);
            log.info("[ACTION-EMAIL] sent {} attempt={}", logContext, attempt);
        } catch (EmailDeliveryException e) {
            handleFailure(eventId, event, attempt, e.isTransientFailure(), e.getCode() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            // Unclassified failures (engine history query, template/admin-center client) may clear up.
            handleFailure(eventId, event, attempt, true, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void handleFailure(String eventId, ActionEmailRequestedEvent event, int attempt,
                               boolean transientFailure, String error) {
        if (transientFailure && attempt < maxAttempts) {
            long delay = BASE_BACKOFF_SECONDS << (attempt - 1);
            deliveryRepository.reschedule(eventId, delay, truncate(error));
            log.warn("[ACTION-EMAIL] retry in {}s eventId={} actionId={} attempt={}/{}: {}",
                    delay, eventId, event.getActionId(), attempt, maxAttempts, error);
            return;
        }
        fail(eventId, event, error);
    }

    private void fail(String eventId, ActionEmailRequestedEvent event, String error) {
        deliveryRepository.markFailed(eventId, truncate(error));
        log.error("[ACTION-EMAIL] FAILED eventId={} actionId={} process={}: {}",
                eventId,
                event != null ? event.getActionId() : null,
                event != null ? event.getProcessInstanceId() : null,
                error);
    }

    /** Keep only the Send-Task property keys, stringified; anything else in postEmail is UI state. */
    static Map<String, String> toConfig(Map<String, Object> postEmail) {
        Map<String, String> config = new HashMap<>();
        if (postEmail == null) {
            return config;
        }
        for (String key : ConfiguredEmailSender.CONFIG_KEYS) {
            Object value = postEmail.get(key);
            if (value != null) {
                config.put(key, value.toString());
            }
        }
        return config;
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
