package com.platform.messaging.service;

import com.platform.messaging.event.*;

import java.util.concurrent.CompletableFuture;

/**
 * Event publisher interface for publishing events to Kafka.
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4
 */
public interface EventPublisher {
    
    /**
     * Publish a process event.
     * 
     * @param event Process event to publish
     * @return CompletableFuture that completes when the event is sent
     */
    CompletableFuture<Void> publishProcessEvent(ProcessEvent event);
    
    /**
     * Publish a task event.
     * 
     * @param event Task event to publish
     * @return CompletableFuture that completes when the event is sent
     */
    CompletableFuture<Void> publishTaskEvent(TaskEvent event);
    
    /**
     * Publish a permission event.
     * 
     * @param event Permission event to publish
     * @return CompletableFuture that completes when the event is sent
     */
    CompletableFuture<Void> publishPermissionEvent(PermissionEvent event);
    
    /**
     * Publish a deployment event.
     * 
     * @param event Deployment event to publish
     * @return CompletableFuture that completes when the event is sent
     */
    CompletableFuture<Void> publishDeploymentEvent(DeploymentEvent event);
    
    /**
     * Publish a generic event.
     * 
     * @param event Event to publish
     * @return CompletableFuture that completes when the event is sent
     */
    CompletableFuture<Void> publish(BaseEvent event);
}
