package com.developer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 开发者工作站应用程序入口
 */
@SpringBootApplication
@EnableJpaAuditing
public class DeveloperWorkstationApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DeveloperWorkstationApplication.class, args);
    }
}
