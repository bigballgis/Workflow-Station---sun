package com.platform.messaging.config;

/**
 * Kafka topic constants.
 */
public final class KafkaTopics {
    
    private KafkaTopics() {}
    
    // Main event topics
    public static final String PROCESS_EVENTS = "platform.process.events";
    public static final String TASK_EVENTS = "platform.task.events";
    public static final String PERMISSION_EVENTS = "platform.permission.events";
    public static final String DEPLOYMENT_EVENTS = "platform.deployment.events";
    
    // Dead letter topics
    public static final String PROCESS_EVENTS_DLT = "platform.process.events.dlt";
    public static final String TASK_EVENTS_DLT = "platform.task.events.dlt";
    public static final String PERMISSION_EVENTS_DLT = "platform.permission.events.dlt";
    public static final String DEPLOYMENT_EVENTS_DLT = "platform.deployment.events.dlt";
    
    // Retry topics
    public static final String PROCESS_EVENTS_RETRY = "platform.process.events.retry";
    public static final String TASK_EVENTS_RETRY = "platform.task.events.retry";
}
