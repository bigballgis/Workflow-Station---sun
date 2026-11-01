package com.portal.component;

import com.portal.exception.PortalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableUnchangedMiStoreTest {
    private static final String STORE = "dw:renamed_correspondence";
    private SubTableWriteIsolation isolation() {
        var design = mock(SubTableWriteDesign.class);
        when(design.resolve("pin", "review")).thenReturn(Map.of());
        when(design.tableFacts("pin")).thenReturn(Map.of(STORE, new SubTableWriteDesign.TableFacts(
                false, false, List.of("MAIN", "SUB"), List.of("rootRef", "lineRef"))));
        return new SubTableWriteIsolation(new MiSubTaskSubTableRowMerger(mock(JdbcTemplate.class)),
                new SubTableBindingScopeGuard(), design);
    }
    private List<Map<String,Object>> originalRows() {
        return List.of(Map.of("messageCode", "MESSAGE-A", "lineRef", "LINE-A", "caption", "ours", "_wsRowVersion", 2),
                Map.of("messageCode", "MESSAGE-B", "lineRef", "LINE-B", "caption", "peer", "_wsRowVersion", 3),
                Map.of("messageCode", "MESSAGE-ROOT", "rootRef", "ROOT", "caption", "shared", "_wsRowVersion", 4));
    }
    private SubTableWriteIsolation.Request request(Map<String,Object> inbound, List<Map<String,Object>> baseline) {
        return new SubTableWriteIsolation.Request(Map.of("_currentItem", Map.of("rowKey", Map.of("lineKey", "LINE-A"))),
                inbound, Map.of("__subTables__", Map.of(STORE, baseline)), List.of(), List.of(), "renamed-fu", "pin", "review");
    }
    @Test void unchangedMixedLevelStoreKeepsExactPersistedOrderAndVersions() {
        var before = originalRows();
        var inbound = new LinkedHashMap<String,Object>(Map.of("__subTables__", Map.of(STORE, before)));
        assertThatCode(() -> isolation().apply(request(inbound, before))).doesNotThrowAnyException();
        assertThat(((Map<?,?>)inbound.get("__subTables__")).get(STORE)).isEqualTo(before);
    }
    @Test void omittedVersionedStoreIsNotTurnedIntoAMiWrite() {
        var before = originalRows();
        var inbound = new LinkedHashMap<String,Object>(Map.of("__subTables__", Map.of()));
        assertThatCode(() -> isolation().apply(request(inbound, before))).doesNotThrowAnyException();
        assertThat(((Map<?,?>)inbound.get("__subTables__")).get(STORE)).isEqualTo(before);
    }
    @Test void changedVersionedStoreStillRequiresItsOwnScope() {
        var before = originalRows();
        var changed = new LinkedHashMap<>(before.get(0)); changed.put("caption", "forged-change");
        var inbound = new LinkedHashMap<String,Object>(Map.of("__subTables__", Map.of(STORE,
                List.of(changed, before.get(1), before.get(2)))));
        assertThatThrownBy(() -> isolation().apply(request(inbound, before)))
                .isInstanceOf(PortalException.class).hasMessageContaining("Versioned sub-table writes require binding scopes");
    }
}
