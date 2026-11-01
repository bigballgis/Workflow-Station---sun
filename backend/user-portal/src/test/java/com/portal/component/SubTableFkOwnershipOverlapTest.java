package com.portal.component;

import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SubTableFkOwnershipOverlapTest {
    @Test void explicitOwnerMayAlsoBeVisibleThroughAnotherValidatedBinding() { validate(true, "LINE"); }
    @Test void twoClaimsCannotCombineTheirForeignKeyPermissions() {
        assertThatThrownBy(() -> validate(false, "LINE")).isInstanceOf(PortalException.class)
                .hasMessageContaining("not owned by this binding");
    }
    @Test void anExplicitOwnerDoesNotBypassAnotherClaimsParentContext() {
        assertThatThrownBy(() -> validate(true, "OTHER")).isInstanceOf(PortalException.class)
                .hasMessageContaining("not allowed to write");
    }
    private void validate(boolean extra, String submittedParent) {
        String store = "dw:arbitrary_messages";
        var root = new SubTableWriteDesign.Binding("10", store, "rootRef", "MAIN", "arbitrary_root",
                List.of("messageCode"), List.of("rootKey"), "structuralFk", List.of("rootRef", "lineRef"), List.of());
        var line = new SubTableWriteDesign.Binding("20", store, "lineRef", "SUB", "arbitrary_lines",
                List.of("messageCode"), List.of("lineKey"), "structuralFk", List.of("rootRef", "lineRef"),
                extra ? List.of("rootRef") : List.of());
        var row = new LinkedHashMap<String,Object>(Map.of("messageCode", "MESSAGE", "rootRef", "ROOT", "lineRef", submittedParent));
        var submitted = new LinkedHashMap<String,Object>(Map.of(store, List.of(row)));
        var scopes = List.of(new SubTableBindingScope("10", store, List.of(Map.of("messageCode", "MESSAGE")), false),
                new SubTableBindingScope("20", store, List.of(Map.of("messageCode", "MESSAGE")), false));
        new SubTableBindingScopeGuard().assertAndApply(scopes, "unrelated-fu",
                Map.of("rootKey", "ROOT", "_currentItem", Map.of("rowKey", Map.of("lineKey", "LINE"))),
                submitted, Map.of(), Map.of("10", root, "20", line));
        assertThat(((Map<?, ?>)((List<?>)submitted.get(store)).get(0)).get("_wsRowVersion")).isEqualTo(1);
    }
}
