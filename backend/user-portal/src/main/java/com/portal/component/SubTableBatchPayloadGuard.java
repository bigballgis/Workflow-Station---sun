package com.portal.component;

/** One canonical-payload validation boundary shared by Save and Complete. */
final class SubTableBatchPayloadGuard {
    private SubTableBatchPayloadGuard() { }

    static void validate(SubTableBatchAddedRowGuard.Request request) {
        SubTableBatchChangeGuard.validateSubmittedRows(request.changes(), request.scopes(), request.submitted());
        SubTableBatchMembershipGuard.validate(request.changes(), request.scopes(), request.variables());
        SubTableBatchDeleteReferenceGuard.validate(request.variables(), request.form(), request.changes(), request.mapper());
        SubTableBatchAddedRowGuard.validate(request);
        SubTableBatchChangeGuard.validateSubmittedAssignments(request.variables(), request.form(),
                request.changes(), request.scopes(), request.submitted(), request.mapper());
    }
}
