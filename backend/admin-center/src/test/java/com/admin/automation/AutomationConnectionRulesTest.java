package com.admin.automation;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomationConnectionRulesTest {

    @Test
    void rejectsBlankAndAllWorkspaces() {
        assertThrows(BusinessException.class, () -> AutomationConnectionRules.workspaceKey(" "));
        assertThrows(BusinessException.class, () -> AutomationConnectionRules.workspaceKey("__ALL__"));
    }

    @Test
    void publicBindingIsSystemAdministratorOnly() {
        assertThrows(BusinessException.class, () -> AutomationConnectionRules.assertCanBind(false, true));
        AutomationConnectionRules.assertCanBind(true, true);
    }

    @Test
    void teamBindingMatchesPublish() {
        assertThrows(BusinessException.class, () -> AutomationConnectionRules.assertCanBind(false, false));
        AutomationConnectionRules.assertCanBind(true, false);
    }

    @Test
    void enabledCatalogMustBeExactlyOne() {
        assertEquals("cat-1", AutomationConnectionRules.requireSingleEnabledCatalog(List.of("cat-1")));
        assertThrows(BusinessException.class,
                () -> AutomationConnectionRules.requireSingleEnabledCatalog(List.of()));
        assertThrows(BusinessException.class,
                () -> AutomationConnectionRules.requireSingleEnabledCatalog(List.of("cat-1", "cat-2")));
    }

    @Test
    void unregisteredAccountIsRejected() {
        assertThrows(BusinessException.class, () -> AutomationConnectionRules.requireRegistered(false));
        AutomationConnectionRules.requireRegistered(true);
    }

    @Test
    void flowAccountWinsOtherwiseWorkspaceThenMismatchIsForbidden() {
        assertEquals("flow-user", AutomationConnectionRules.flowRuntimeAccount("letters", "letters", "flow-user", "group-user"));
        assertEquals("group-user", AutomationConnectionRules.flowRuntimeAccount("letters", "letters", null, "group-user"));
        assertEquals("group-user", AutomationConnectionRules.flowRuntimeAccount(null, "letters", null, "group-user"));
        assertThrows(BusinessException.class,
                () -> AutomationConnectionRules.flowRuntimeAccount("other", "letters", "flow-user", "group-user"));
    }

    @Test
    void scopeCannotNameAnotherCatalog() {
        assertThrows(BusinessException.class,
                () -> AutomationConnectionRules.assertSingleCatalog(List.of("other"), "letter"));
        AutomationConnectionRules.assertSingleCatalog(List.of("letter"), "letter");
    }

    @Test
    void capabilitiesStayInsideTheKnownSet() {
        assertEquals(List.of("read", "query"), AutomationConnectionRules.capabilities(List.of("read", "query", "read")));
        assertEquals(List.of("start"), AutomationConnectionRules.capabilities(List.of("start")));
        assertThrows(BusinessException.class, () -> AutomationConnectionRules.capabilities(List.of("admin")));
    }
}
