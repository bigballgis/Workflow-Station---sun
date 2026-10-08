package com.admin.environment;

import org.springframework.util.StringUtils;

/**
 * Maps {@code SPRING_PROFILES_ACTIVE} onto Environment catalog {@code deploy_env}.
 */
public final class DeployProfileMapper {

    private DeployProfileMapper() {
    }

    public static String toDeployEnv(String springProfilesActive) {
        if (!StringUtils.hasText(springProfilesActive)) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE is blank; cannot resolve deploy_env");
        }
        String normalized = springProfilesActive.toLowerCase();
        if (containsToken(normalized, "prod")) {
            return "prod";
        }
        if (containsToken(normalized, "uat")) {
            return "uat";
        }
        if (containsToken(normalized, "sit")) {
            return "sit";
        }
        if (containsToken(normalized, "docker") || containsToken(normalized, "dev")) {
            return "dev";
        }
        throw new IllegalStateException("Cannot map Spring profiles to deploy_env: " + springProfilesActive);
    }

    private static boolean containsToken(String csv, String token) {
        for (String part : csv.split(",")) {
            if (token.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }
}
