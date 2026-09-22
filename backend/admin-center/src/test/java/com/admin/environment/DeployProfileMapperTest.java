package com.admin.environment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeployProfileMapperTest {

    @Test
    void dockerMapsToDev() {
        assertEquals("dev", DeployProfileMapper.toDeployEnv("docker"));
    }

    @Test
    void uatWinsOverDocker() {
        assertEquals("uat", DeployProfileMapper.toDeployEnv("docker,uat"));
    }

    @Test
    void blankThrows() {
        assertThrows(IllegalStateException.class, () -> DeployProfileMapper.toDeployEnv(" "));
    }
}
