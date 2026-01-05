package com.platform.common.functionunit;

/**
 * Deployment strategy enumeration.
 */
public enum DeploymentStrategy {
    ROLLING,
    BLUE_GREEN,
    CANARY,
    RECREATE
}
