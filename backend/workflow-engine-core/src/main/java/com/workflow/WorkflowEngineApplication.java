package com.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 工作流引擎核心应用程序
 * 基于Flowable 7.0.0 + Spring Boot 3.x + PostgreSQL 16.5
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableTransactionManagement
public class WorkflowEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowEngineApplication.class, args);
    }
}