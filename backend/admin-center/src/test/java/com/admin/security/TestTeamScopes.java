package com.admin.security;

import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Team scopes for tests of components that are not about team scope. */
public final class TestTeamScopes {

    private TestTeamScopes() {
    }

    /** A caller who reads and changes every team's resources (a System Administrator): no filter, every flag true. */
    public static AdminTeamScope everything() {
        // Lenient: each test uses only some of these answers.
        AdminTeamScope scope = mock(AdminTeamScope.class, withSettings().strictness(Strictness.LENIENT));
        when(scope.visibleFunctionUnitCodes()).thenReturn(null);
        when(scope.manageableFunctionUnitCodes()).thenReturn(null);
        when(scope.mayManageFunctionUnit(any())).thenReturn(true);
        when(scope.mayWrite(any(), any())).thenReturn(true);
        when(scope.mayReadRelationTableLinkedTo(any())).thenReturn(true);
        when(scope.mayWriteRelationTableLinkedTo(any())).thenReturn(true);
        return scope;
    }
}
