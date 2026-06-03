package com.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.platform.gateway",
        "com.admin.controller.gateway",
        "com.admin.service.gateway",
        "com.admin.repository.gateway",
        "com.admin.entity.gateway",
        "com.admin.adapter.gateway",
        "com.admin.audit"
})
@EnableScheduling
public class GatewayManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayManagementServiceApplication.class, args);
    }
}
