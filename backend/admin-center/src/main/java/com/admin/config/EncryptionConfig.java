package com.admin.config;

import com.platform.security.encryption.EncryptionService;
import com.platform.security.encryption.impl.AesEncryptionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加密服务配置
 * 
 * admin-center 的 @ComponentScan 未扫描 com.platform.security.encryption 包，
 * 因此需要通过 @Bean 手动注册 AesEncryptionService。
 * 
 * 注意：AesEncryptionService 使用 @Value 注入 encryption.key 和 @PostConstruct 初始化，
 * Spring 会对 @Bean 方法返回的对象正确处理这些注解（通过 BeanPostProcessor）。
 */
@Configuration
public class EncryptionConfig {

    @Bean
    public EncryptionService encryptionService() {
        return new AesEncryptionService();
    }
}
