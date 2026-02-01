package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Messaging Configuration
 * 
 * Externalized messaging settings for Kafka and other messaging systems
 * 
 * @author Platform Team
 * @version 1.0
 */
public class MessagingConfig {
    
    @NotBlank(message = "Kafka bootstrap servers is required")
    private String kafkaBootstrapServers = "localhost:9092";
    
    @NotBlank(message = "Kafka producer key serializer is required")
    private String kafkaProducerKeySerializer = "org.apache.kafka.common.serialization.StringSerializer";
    
    @NotBlank(message = "Kafka producer value serializer is required")
    private String kafkaProducerValueSerializer = "org.springframework.kafka.support.serializer.JsonSerializer";
    
    @NotBlank(message = "Kafka consumer group ID is required")
    private String kafkaConsumerGroupId = "platform-consumer";
    
    @NotBlank(message = "Kafka consumer auto offset reset is required")
    private String kafkaConsumerAutoOffsetReset = "earliest";
    
    @NotBlank(message = "Kafka consumer key deserializer is required")
    private String kafkaConsumerKeyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
    
    @NotBlank(message = "Kafka consumer value deserializer is required")
    private String kafkaConsumerValueDeserializer = "org.springframework.kafka.support.serializer.JsonDeserializer";
    
    @Min(value = 1, message = "Kafka producer retries must be at least 1")
    @Max(value = 100, message = "Kafka producer retries cannot exceed 100")
    private int kafkaProducerRetries = 3;
    
    @Min(value = 1, message = "Kafka producer batch size must be at least 1")
    @Max(value = 1048576, message = "Kafka producer batch size cannot exceed 1MB")
    private int kafkaProducerBatchSize = 16384;
    
    @Min(value = 0, message = "Kafka producer linger ms cannot be negative")
    @Max(value = 60000, message = "Kafka producer linger ms cannot exceed 1 minute")
    private int kafkaProducerLingerMs = 1;
    
    @Min(value = 1024, message = "Kafka producer buffer memory must be at least 1KB")
    @Max(value = 134217728, message = "Kafka producer buffer memory cannot exceed 128MB")
    private long kafkaProducerBufferMemory = 33554432;
    
    @Min(value = 1000, message = "Kafka consumer session timeout must be at least 1 second")
    @Max(value = 300000, message = "Kafka consumer session timeout cannot exceed 5 minutes")
    private int kafkaConsumerSessionTimeoutMs = 30000;
    
    @Min(value = 1, message = "Kafka consumer max poll records must be at least 1")
    @Max(value = 10000, message = "Kafka consumer max poll records cannot exceed 10000")
    private int kafkaConsumerMaxPollRecords = 500;
    
    private boolean enableKafka = true;
    private boolean enableKafkaMetrics = true;
    private boolean enableKafkaHealthCheck = true;
    
    // Topic Configuration
    @NotBlank(message = "Audit events topic is required")
    private String auditEventsTopic = "audit-events";
    
    @NotBlank(message = "Notification events topic is required")
    private String notificationEventsTopic = "notification-events";
    
    @NotBlank(message = "Workflow events topic is required")
    private String workflowEventsTopic = "workflow-events";
    
    @NotBlank(message = "User events topic is required")
    private String userEventsTopic = "user-events";
    
    @NotBlank(message = "System events topic is required")
    private String systemEventsTopic = "system-events";
    
    // Email Configuration
    @NotBlank(message = "Email host is required")
    private String emailHost = "localhost";
    
    @Min(value = 1, message = "Email port must be at least 1")
    @Max(value = 65535, message = "Email port cannot exceed 65535")
    private int emailPort = 587;
    
    private String emailUsername = "";
    private String emailPassword = "";
    
    private boolean emailEnableTls = true;
    private boolean emailEnableAuth = true;
    
    @NotBlank(message = "Email from address is required")
    private String emailFromAddress = "noreply@platform.com";
    
    @NotBlank(message = "Email from name is required")
    private String emailFromName = "Platform System";
    
    // SMS Configuration
    private boolean enableSms = false;
    
    @NotBlank(message = "SMS provider URL is required")
    private String smsProviderUrl = "http://localhost:8084/sms";
    
    private String smsApiKey = "";
    private String smsApiSecret = "";
    
    @Min(value = 1000, message = "SMS timeout must be at least 1 second")
    @Max(value = 60000, message = "SMS timeout cannot exceed 1 minute")
    private long smsTimeoutMs = 10000;
    
    // Getters and Setters
    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }
    
    public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }
    
    public String getKafkaProducerKeySerializer() {
        return kafkaProducerKeySerializer;
    }
    
    public void setKafkaProducerKeySerializer(String kafkaProducerKeySerializer) {
        this.kafkaProducerKeySerializer = kafkaProducerKeySerializer;
    }
    
    public String getKafkaProducerValueSerializer() {
        return kafkaProducerValueSerializer;
    }
    
    public void setKafkaProducerValueSerializer(String kafkaProducerValueSerializer) {
        this.kafkaProducerValueSerializer = kafkaProducerValueSerializer;
    }
    
    public String getKafkaConsumerGroupId() {
        return kafkaConsumerGroupId;
    }
    
    public void setKafkaConsumerGroupId(String kafkaConsumerGroupId) {
        this.kafkaConsumerGroupId = kafkaConsumerGroupId;
    }
    
    public String getKafkaConsumerAutoOffsetReset() {
        return kafkaConsumerAutoOffsetReset;
    }
    
    public void setKafkaConsumerAutoOffsetReset(String kafkaConsumerAutoOffsetReset) {
        this.kafkaConsumerAutoOffsetReset = kafkaConsumerAutoOffsetReset;
    }
    
    public String getKafkaConsumerKeyDeserializer() {
        return kafkaConsumerKeyDeserializer;
    }
    
    public void setKafkaConsumerKeyDeserializer(String kafkaConsumerKeyDeserializer) {
        this.kafkaConsumerKeyDeserializer = kafkaConsumerKeyDeserializer;
    }
    
    public String getKafkaConsumerValueDeserializer() {
        return kafkaConsumerValueDeserializer;
    }
    
    public void setKafkaConsumerValueDeserializer(String kafkaConsumerValueDeserializer) {
        this.kafkaConsumerValueDeserializer = kafkaConsumerValueDeserializer;
    }
    
    public int getKafkaProducerRetries() {
        return kafkaProducerRetries;
    }
    
    public void setKafkaProducerRetries(int kafkaProducerRetries) {
        this.kafkaProducerRetries = kafkaProducerRetries;
    }
    
    public int getKafkaProducerBatchSize() {
        return kafkaProducerBatchSize;
    }
    
    public void setKafkaProducerBatchSize(int kafkaProducerBatchSize) {
        this.kafkaProducerBatchSize = kafkaProducerBatchSize;
    }
    
    public int getKafkaProducerLingerMs() {
        return kafkaProducerLingerMs;
    }
    
    public void setKafkaProducerLingerMs(int kafkaProducerLingerMs) {
        this.kafkaProducerLingerMs = kafkaProducerLingerMs;
    }
    
    public long getKafkaProducerBufferMemory() {
        return kafkaProducerBufferMemory;
    }
    
    public void setKafkaProducerBufferMemory(long kafkaProducerBufferMemory) {
        this.kafkaProducerBufferMemory = kafkaProducerBufferMemory;
    }
    
    public int getKafkaConsumerSessionTimeoutMs() {
        return kafkaConsumerSessionTimeoutMs;
    }
    
    public void setKafkaConsumerSessionTimeoutMs(int kafkaConsumerSessionTimeoutMs) {
        this.kafkaConsumerSessionTimeoutMs = kafkaConsumerSessionTimeoutMs;
    }
    
    public int getKafkaConsumerMaxPollRecords() {
        return kafkaConsumerMaxPollRecords;
    }
    
    public void setKafkaConsumerMaxPollRecords(int kafkaConsumerMaxPollRecords) {
        this.kafkaConsumerMaxPollRecords = kafkaConsumerMaxPollRecords;
    }
    
    public boolean isEnableKafka() {
        return enableKafka;
    }
    
    public void setEnableKafka(boolean enableKafka) {
        this.enableKafka = enableKafka;
    }
    
    public boolean isEnableKafkaMetrics() {
        return enableKafkaMetrics;
    }
    
    public void setEnableKafkaMetrics(boolean enableKafkaMetrics) {
        this.enableKafkaMetrics = enableKafkaMetrics;
    }
    
    public boolean isEnableKafkaHealthCheck() {
        return enableKafkaHealthCheck;
    }
    
    public void setEnableKafkaHealthCheck(boolean enableKafkaHealthCheck) {
        this.enableKafkaHealthCheck = enableKafkaHealthCheck;
    }
    
    public String getAuditEventsTopic() {
        return auditEventsTopic;
    }
    
    public void setAuditEventsTopic(String auditEventsTopic) {
        this.auditEventsTopic = auditEventsTopic;
    }
    
    public String getNotificationEventsTopic() {
        return notificationEventsTopic;
    }
    
    public void setNotificationEventsTopic(String notificationEventsTopic) {
        this.notificationEventsTopic = notificationEventsTopic;
    }
    
    public String getWorkflowEventsTopic() {
        return workflowEventsTopic;
    }
    
    public void setWorkflowEventsTopic(String workflowEventsTopic) {
        this.workflowEventsTopic = workflowEventsTopic;
    }
    
    public String getUserEventsTopic() {
        return userEventsTopic;
    }
    
    public void setUserEventsTopic(String userEventsTopic) {
        this.userEventsTopic = userEventsTopic;
    }
    
    public String getSystemEventsTopic() {
        return systemEventsTopic;
    }
    
    public void setSystemEventsTopic(String systemEventsTopic) {
        this.systemEventsTopic = systemEventsTopic;
    }
    
    public String getEmailHost() {
        return emailHost;
    }
    
    public void setEmailHost(String emailHost) {
        this.emailHost = emailHost;
    }
    
    public int getEmailPort() {
        return emailPort;
    }
    
    public void setEmailPort(int emailPort) {
        this.emailPort = emailPort;
    }
    
    public String getEmailUsername() {
        return emailUsername;
    }
    
    public void setEmailUsername(String emailUsername) {
        this.emailUsername = emailUsername;
    }
    
    public String getEmailPassword() {
        return emailPassword;
    }
    
    public void setEmailPassword(String emailPassword) {
        this.emailPassword = emailPassword;
    }
    
    public boolean isEmailEnableTls() {
        return emailEnableTls;
    }
    
    public void setEmailEnableTls(boolean emailEnableTls) {
        this.emailEnableTls = emailEnableTls;
    }
    
    public boolean isEmailEnableAuth() {
        return emailEnableAuth;
    }
    
    public void setEmailEnableAuth(boolean emailEnableAuth) {
        this.emailEnableAuth = emailEnableAuth;
    }
    
    public String getEmailFromAddress() {
        return emailFromAddress;
    }
    
    public void setEmailFromAddress(String emailFromAddress) {
        this.emailFromAddress = emailFromAddress;
    }
    
    public String getEmailFromName() {
        return emailFromName;
    }
    
    public void setEmailFromName(String emailFromName) {
        this.emailFromName = emailFromName;
    }
    
    public boolean isEnableSms() {
        return enableSms;
    }
    
    public void setEnableSms(boolean enableSms) {
        this.enableSms = enableSms;
    }
    
    public String getSmsProviderUrl() {
        return smsProviderUrl;
    }
    
    public void setSmsProviderUrl(String smsProviderUrl) {
        this.smsProviderUrl = smsProviderUrl;
    }
    
    public String getSmsApiKey() {
        return smsApiKey;
    }
    
    public void setSmsApiKey(String smsApiKey) {
        this.smsApiKey = smsApiKey;
    }
    
    public String getSmsApiSecret() {
        return smsApiSecret;
    }
    
    public void setSmsApiSecret(String smsApiSecret) {
        this.smsApiSecret = smsApiSecret;
    }
    
    public long getSmsTimeoutMs() {
        return smsTimeoutMs;
    }
    
    public void setSmsTimeoutMs(long smsTimeoutMs) {
        this.smsTimeoutMs = smsTimeoutMs;
    }
}