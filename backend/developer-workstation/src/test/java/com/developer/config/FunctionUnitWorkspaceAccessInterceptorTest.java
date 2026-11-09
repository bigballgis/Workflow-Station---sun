package com.developer.config;

import com.developer.security.WorkspaceAccessAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which unit a request works in, and which action it needs there (platform access model §6.2, D-35). */
class FunctionUnitWorkspaceAccessInterceptorTest {

    @Test
    void theUnitInThePathWinsOverTheQuery() {
        assertThat(FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId("/ai-generation/lock/9", "5"))
                .isEqualTo(9L);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId("/ai-generation/events/9", "5"))
                .isEqualTo(9L);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId("/ai-generation/studio-thread/9/messages", "5"))
                .isEqualTo(9L);
    }

    @Test
    void theQueryNamesTheUnitOnlyOnAiRoutesWithoutOne() {
        assertThat(FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId("/ai-generation/sessions", "5"))
                .isEqualTo(5L);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId("/export-import/import", "5")).isNull();
        assertThat(FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId("/ai-generation/sessions", null)).isNull();
    }

    @Test
    void aMalformedQueryUnitIsRefusedNotIgnored() {
        assertThatThrownBy(() -> FunctionUnitWorkspaceAccessInterceptor.extractFunctionUnitId(
                "/ai-generation/documents", "abc"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void aCloneOnlyReadsItsSourceAndSoDoesSimulate() {
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/clone", "POST"))
                .isEqualTo(WorkspaceAccessAction.VIEW);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/process/simulate", "POST"))
                .isEqualTo(WorkspaceAccessAction.VIEW);
        // The lookup probe reads live relation-table rows.
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/process/debug/lookup/probe", "POST"))
                .isEqualTo(WorkspaceAccessAction.MODIFY);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/process/debug/actions/run", "POST"))
                .isEqualTo(WorkspaceAccessAction.MODIFY);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/forms/3/copy", "POST"))
                .isEqualTo(WorkspaceAccessAction.MODIFY);
    }

    @Test
    void readsWritesAndTheSpecialRoutesKeepTheirActions() {
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/forms", "GET"))
                .isEqualTo(WorkspaceAccessAction.VIEW);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/api/forms/3/sub-table-views/4/or-create", "GET"))
                .isEqualTo(WorkspaceAccessAction.MODIFY);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7", "DELETE"))
                .isEqualTo(WorkspaceAccessAction.DELETE);
        assertThat(FunctionUnitWorkspaceAccessInterceptor.actionFor("/function-units/7/dev-groups", "PUT"))
                .isEqualTo(WorkspaceAccessAction.ASSIGN_DEV_GROUPS);
    }
}
