package com.portal.component;

import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SubTableFkOwnershipTest {
    private static final String STORE = "dw:renamed_messages";
    private static SubTableWriteDesign.Binding binding(String id, String filter, List<String> extra) {
        return new SubTableWriteDesign.Binding(id, STORE, filter, "SUB", "renamed_parent",
                List.of("messageCode", "revision"), List.of("parentCode"), "structuralFk",
                List.of("rootRef", "lineRef"), extra);
    }
    private static SubTableBindingScope claim(String id, String key) {
        return new SubTableBindingScope(id, STORE,
                List.of(Map.of("messageCode", key, "revision", 2)), false);
    }
    private SubTableFkOwnership policy(List<String> extra, List<SubTableBindingScope> scopes) {
        return new SubTableFkOwnership(Map.of("10", binding("10", "rootRef", List.of()),
                "20", binding("20", "lineRef", extra)), scopes, Map.of(7L, STORE));
    }
    @Test void separatesBindingsByCompositeRowIdentity() {
        var policy = policy(List.of(), List.of(claim("10", "ROOT"), claim("20", "LINE")));
        var root = Map.<String,Object>of("messageCode", "ROOT", "revision", 2);
        var line = Map.<String,Object>of("messageCode", "LINE", "revision", 2);
        assertThat(policy.allows(7L, root, "rootRef")).isTrue();
        assertThat(policy.allows(7L, root, "lineRef")).isFalse();
        assertThat(policy.allows(7L, line, "lineRef")).isTrue();
        assertThat(policy.allows(7L, line, "rootRef")).isFalse();
        assertThat(policy.allows(7L, Map.of("messageCode", "LINE", "revision", 3), "lineRef")).isFalse();
    }
    @Test void honorsExplicitExtraFillWithoutGrantingOtherRows() {
        var policy = policy(List.of("rootRef"), List.of(claim("20", "LINE")));
        assertThat(policy.allows(7L, Map.of("messageCode", "LINE", "revision", 2), "rootRef")).isTrue();
        assertThat(policy.allows(7L, Map.of("messageCode", "OTHER", "revision", 2), "rootRef")).isFalse();
    }
    @Test void doesNotGuessAnUnclaimedMultiBinding() {
        assertThat(policy(List.of(), List.of()).allows(7L,
                Map.of("messageCode", "LINE", "revision", 2), "lineRef")).isFalse();
    }
    @Test void rejectsForgedBindingOrStore() {
        assertThatThrownBy(() -> policy(List.of(), List.of(claim("999", "LINE"))))
                .isInstanceOf(PortalException.class);
        assertThatThrownBy(() -> policy(List.of(), List.of(new SubTableBindingScope("10", "dw:other", List.of(), false))))
                .isInstanceOf(PortalException.class);
    }
    @Test void solePublishedBindingKeepsUnscopedCompatibility() {
        var policy = new SubTableFkOwnership(Map.of("20", binding("20", "lineRef", List.of())),
                List.of(), Map.of(7L, STORE));
        assertThat(policy.allows(7L, Map.of(), "lineRef")).isTrue();
        assertThat(policy.allows(7L, Map.of(), "rootRef")).isFalse();
    }
    @Test void anotherScopedTableDoesNotDisableTheSoleParentsBinding() {
        var line = new SubTableWriteDesign.Binding("30", "dw:lines", "rootRef", "MAIN", "root",
                List.of("lineKey"), List.of("rootKey"), "structuralFk", List.of("rootRef"), List.of());
        var policy = new SubTableFkOwnership(Map.of("20", binding("20", "lineRef", List.of()), "30", line),
                List.of(claim("20", "MESSAGE")), Map.of(8L, "dw:lines"));
        assertThat(policy.allows(8L, Map.of("lineKey", "LINE"), "rootRef")).isTrue();
        policy.assertNewRows(Map.of("dw:lines", List.of(Map.of("lineKey", "LINE", "rootRef", "ROOT"))), Map.of());
    }
    @Test void platformIdentityIsUsedOnlyWhenConfiguredKeyIsUnavailable() {
        var scope = new SubTableBindingScope("20", STORE, List.of(Map.of("platformRowUuid", "ROW")), false);
        var policy = policy(List.of(), List.of(scope));
        assertThat(policy.allows(7L, Map.of("platformRowUuid", "ROW"), "lineRef")).isTrue();
        assertThat(policy.allows(7L, Map.of("messageCode", "M", "revision", 2, "platformRowUuid", "ROW"), "lineRef")).isFalse();
    }
    @Test void overlappingClaimsDoNotExpandEitherBindingsAuthority() {
        var policy = policy(List.of(), List.of(claim("10", "SAME"), claim("20", "SAME")));
        assertThat(policy.allows(7L, Map.of("messageCode", "SAME", "revision", 2, "rootRef", "ROOT", "lineRef", "LINE"), "rootRef")).isFalse();
    }
}
