package com.admin.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPortalAuditListQueryRequestTest {

    @Test
    void rejectsNegativePageAndOutOfRangeSize() {
        IllegalArgumentException pageEx = assertThrows(
                IllegalArgumentException.class,
                () -> request(-1, 20, null, null));
        assertTrue(pageEx.getMessage().contains("page"));

        IllegalArgumentException sizeEx = assertThrows(
                IllegalArgumentException.class,
                () -> request(0, 0, null, null));
        assertTrue(sizeEx.getMessage().contains("size"));
    }

    @Test
    void rejectsSortFieldWithoutDirection() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> request(0, 20, "timestamp", null));
        assertTrue(ex.getMessage().contains("sortDirection"));
    }

    @Test
    void acceptsValidPagingAndEmptyFilters() {
        UserPortalAuditListQueryRequest body = assertDoesNotThrow(
                () -> request(0, 20, "timestamp", "DESC"));
        assertEquals(List.of(), body.filters());
    }

    @Test
    void theUnitScopeIsAdminsAndKeepsNullApartFromEmpty() {
        UserPortalAuditListQueryRequest fromBrowser = new UserPortalAuditListQueryRequest(
                0, 20, null, null, null, null, null, null, null, null, null, List.of("SOMEONE_ELSES"));

        assertEquals(List.of("FU_A"), fromBrowser.withFunctionUnitCodes(java.util.Set.of("FU_A")).functionUnitCodes());
        assertEquals(List.of(), fromBrowser.withFunctionUnitCodes(java.util.Set.of()).functionUnitCodes());
        assertEquals(null, fromBrowser.withFunctionUnitCodes(null).functionUnitCodes());
    }

    private static UserPortalAuditListQueryRequest request(
            int page, int size, String sortField, String sortDirection) {
        return new UserPortalAuditListQueryRequest(
                page, size, null, null, null, null, null, null,
                null, sortField, sortDirection, null);
    }
}
