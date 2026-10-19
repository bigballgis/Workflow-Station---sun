package com.developer.util;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class FormFieldPermissionBindingIdRewriterTest {
    @Test void rewritesOnlyExactDeclaredBindingPrefixesAndPreservesPermissionValues() {
        var source = Map.of("101:caption", "EDITABLE", "102:caption", "HIDDEN", "mainField", "READONLY",
                "1010:caption", "READONLY", "caption101", "EDITABLE");
        assertThat(FormFieldPermissionBindingIdRewriter.remap(source, Map.of(101L, 501L, 102L, 502L)))
                .isEqualTo(Map.of("501:caption", "EDITABLE", "502:caption", "HIDDEN", "mainField", "READONLY",
                        "1010:caption", "READONLY", "caption101", "EDITABLE"));
        assertThat(source).containsKey("101:caption").doesNotContainKey("501:caption");
    }
    @Test void chainedMappingsAreAppliedOnceNotRepeatedlyToTheNewId() {
        assertThat(FormFieldPermissionBindingIdRewriter.remap(Map.of("11:field", "READONLY"), Map.of(11L, 22L, 22L, 33L)))
                .isEqualTo(Map.of("22:field", "READONLY"));
    }
    @Test void collidingTargetsAreRejectedRatherThanBroadeningOrOverwritingPermissions() {
        assertThatThrownBy(() -> FormFieldPermissionBindingIdRewriter.remap(
                Map.of("11:field", "READONLY", "22:field", "EDITABLE"), Map.of(11L, 22L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Field permission binding remap is ambiguous");
    }
    @Test void legacyNullEmptyAndUnscopedMapsRemainIndependentWithoutChangingTheirMeaning() {
        assertThat(FormFieldPermissionBindingIdRewriter.remap(null, Map.of())).isNull();
        assertThat(FormFieldPermissionBindingIdRewriter.remap(Map.of(), Map.of())).isEmpty();
        Map<String, String> source = new LinkedHashMap<>(Map.of("caption", "HIDDEN"));
        var copy = FormFieldPermissionBindingIdRewriter.remap(source, Map.of());
        copy.put("caption", "EDITABLE");
        assertThat(source).containsEntry("caption", "HIDDEN");
    }
}
