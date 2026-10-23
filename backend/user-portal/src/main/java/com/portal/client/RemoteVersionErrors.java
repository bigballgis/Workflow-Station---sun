package com.portal.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

/**
 * Admin Center and the workflow engine report a version-routing failure as 409 with a {@code VERSION_*}
 * reason in {@code error.details}. The Portal rebuilds it so the page shows the version error, not a 500.
 */
public final class RemoteVersionErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RemoteVersionErrors() {
    }

    /** The remote service's version-routing failure; null for any other conflict. */
    public static FunctionUnitVersionException versionErrorOf(HttpClientErrorException conflict) {
        Map<String, Object> body;
        try {
            body = JSON.readValue(conflict.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() { });
        } catch (Exception notJson) {
            return null;
        }
        if (body.get("error") instanceof Map<?, ?> error && error.get("details") instanceof Map<?, ?> details
                && details.get("reason") instanceof String reason && reason.startsWith("VERSION_")) {
            Object reference = details.get("reference");
            return new FunctionUnitVersionException(reason, reference == null ? null : String.valueOf(reference));
        }
        return null;
    }

    /** The version error when the conflict is one, otherwise the conflict itself. */
    public static RuntimeException versionErrorOr(HttpClientErrorException conflict) {
        FunctionUnitVersionException versionError = versionErrorOf(conflict);
        return versionError != null ? versionError : conflict;
    }
}
