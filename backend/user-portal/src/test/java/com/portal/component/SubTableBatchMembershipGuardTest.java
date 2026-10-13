package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchMembershipGuardTest {
    private final List<SubTableBindingScope> scopes = List.of(new SubTableBindingScope("101", "dw:lines", List.of(), false));

    @Test
    void addRejectsAnIdentityAlreadyInTheSavedStore() {
        assertThatThrownBy(() -> SubTableBatchMembershipGuard.validate(List.of(change("ADD")), scopes,
                baseline(Map.of("code", "L1", "_wsRowVersion", 2))))
                .hasMessageContaining("already exists");
    }

    @Test
    void deletingAnExistingVersionedRowIsValid() {
        assertThatCode(() -> SubTableBatchMembershipGuard.validate(List.of(change("DELETE")), scopes,
                baseline(Map.of("code", "L1", "_wsRowVersion", 2)))).doesNotThrowAnyException();
    }

    @Test
    void missingVersionsAreNotTreatedAsZero() {
        assertThatThrownBy(() -> SubTableBatchMembershipGuard.validate(List.of(change("DELETE")), scopes,
                baseline(Map.of("code", "L1")))).hasMessageContaining("no saved row version");
    }

    private static SubTableBatchChange change(String operation) {
        SubTableBatchChange.RowPatch patch = new SubTableBatchChange.RowPatch();
        patch.setRowKey(Map.of("code", "L1"));
        SubTableBatchChange change = new SubTableBatchChange();
        change.setOperation(operation);
        change.setBindingId("101");
        change.setRowPatches(List.of(patch));
        return change;
    }

    private static Map<String, Object> baseline(Map<String, Object> row) {
        return Map.of("__subTables__", Map.of("dw:lines", List.of(row)));
    }
}
