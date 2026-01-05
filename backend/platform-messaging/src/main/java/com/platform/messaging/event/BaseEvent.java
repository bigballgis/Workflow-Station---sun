package com.platform.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all platform events.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String traceId;
    private String sourceService;
    private int retryCount;
    
    /**
     * Initialize event with default values.
     */
    public void initializeDefaults() {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (retryCount < 0) {
            retryCount = 0;
        }
    }
    
    /**
     * Increment retry count.
     */
    public void incrementRetry() {
        retryCount++;
    }
    
    /**
     * Get the topic name for this event.
     */
    public abstract String getTopic();
}
