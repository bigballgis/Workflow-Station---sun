package com.portal.client;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.util.JsonUtils;
import org.springframework.web.client.RestClientResponseException;

/** Preserve typed protection denials when an existing engine client handles legacy errors. */
final class AssignmentRemoteFailure {
    private AssignmentRemoteFailure() { }
    static void rethrowKnown(Exception error) {
        if(error instanceof AssignmentFailure failure) throw failure;
        if(!(error instanceof RestClientResponseException response)) return;
        String reason;
        try {
            var body=JsonUtils.getObjectMapper().readTree(response.getResponseBodyAsString());
            if(body==null) return;
            var value=body.path("error").path("details").path("reason");
            if(!value.isTextual()) return;
            reason=value.textValue();
        } catch(java.io.IOException malformedLegacyResponse) {
            return; // FALLBACK(migration): untyped legacy engine errors retain their existing client handling.
        }
        if(!reason.startsWith("ASSIGNMENT_")) return;
        AssignmentFailure.Reason typed;
        try {typed=AssignmentFailure.Reason.valueOf(reason);}
        catch(IllegalArgumentException unknownContract) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
        }
        throw new AssignmentFailure(typed);
    }
}
