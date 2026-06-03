package com.admin.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class MfeStoreConfig {

    @Getter
    @Value("${mfe.store.path:/tmp/mfe-store}")
    private String storePath;

    public Path resolveModulePath(String moduleCode, String version) {
        return Path.of(storePath, moduleCode, version);
    }
}
