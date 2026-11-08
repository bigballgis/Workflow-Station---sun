package com.portal.automation;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomationAccessRulesTest {

    @Test
    void requiresWorkspaceAndFlow() {
        assertThrows(BusinessException.class, () -> AutomationAccessRules.requireRunContext("", "flow-1"));
        assertThrows(BusinessException.class, () -> AutomationAccessRules.requireRunContext("hermes-dg-1", " "));
    }

    @Test
    void rejectsACallerChosenActor() {
        assertThrows(BusinessException.class, () -> AutomationAccessRules.rejectActor(Map.of("actor", "林可")));
        assertThrows(BusinessException.class, () -> AutomationAccessRules.rejectActor(Map.of("userId", "u-1")));
        assertThrows(BusinessException.class, () -> AutomationAccessRules.rejectActor(Map.of("startUserId", "u-1")));
        AutomationAccessRules.rejectActor(Map.of("fields", Map.of("status", "OPEN")));
    }

    @Test
    void refusesAnotherCatalogOrMissingCapability() {
        assertThrows(BusinessException.class, () -> AutomationAccessRules.requireCatalog("letter", "purchase"));
        assertThrows(BusinessException.class, () -> AutomationAccessRules.requireCapability(Set.of("read"), "write"));
        AutomationAccessRules.requireCatalog("letter", "letter");
    }

    @Test
    void queryNeedsAWhitelistedFilterAndABoundedPage() {
        assertThrows(BusinessException.class, () -> AutomationAccessRules.requireFilters(List.of(), Set.of("status")));
        assertThrows(BusinessException.class, () -> AutomationAccessRules.requireFilters(
                List.of(Map.of("field", "secret", "value", "x")), Set.of("status")));
        AutomationAccessRules.requireFilters(List.of(Map.of("field", "status", "value", "OPEN")), Set.of("status"));
        assertThrows(BusinessException.class, () -> AutomationAccessRules.pageSize(51));
        assertEquals(50, AutomationAccessRules.pageSize(null));
    }
}
