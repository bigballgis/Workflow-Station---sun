package com.platform.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.platform.messaging.config.KafkaTopics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Request to send the email configured on a Developer Workstation Action ({@code config_json.postEmail})
 * after that Action succeeded in the User Portal.
 *
 * <p>Published by user-portal once the Action's binding to the current node has been verified;
 * consumed by workflow-engine-core, which resolves recipients/template against the process
 * variables and delivers at most once per {@link #getEventId() eventId}.</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionEmailRequestedEvent extends BaseEvent {

    public static final String TOPIC = KafkaTopics.ACTION_EMAIL_REQUESTS;

    /** Recipients, template placeholders and the Function Unit id/code are read from this instance's variables. */
    private String processInstanceId;
    private String taskId;
    private String actionId;
    private String actionName;
    private String actionType;
    private String operatorId;
    private String comment;
    /** The Action's {@code postEmail} block, same keys as the BPMN Send Task email properties. */
    private Map<String, Object> postEmail;

    @Override
    @JsonIgnore
    public String getTopic() {
        return TOPIC;
    }
}
