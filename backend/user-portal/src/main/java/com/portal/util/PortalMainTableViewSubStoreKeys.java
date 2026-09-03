package com.portal.util;

import com.platform.common.subtable.SubTableStoreKeys;

import java.util.List;

/**
 * Store keys a SUB Main Table View may read from {@code variables.__subTables__}.
 *
 * <p>Writes use one canonical key per designer table ({@code dw:<table_name>}). Historical
 * instances still sit under form binding ids. The Data View query prefers the canonical key
 * and only opens those binding ids when that key is absent on an instance.
 */
public final class PortalMainTableViewSubStoreKeys {

    private PortalMainTableViewSubStoreKeys() {
    }

    public record SliceKeys(String canonicalStoreKey, List<String> legacyBindingKeys) {
        public SliceKeys {
            canonicalStoreKey = blankToNull(canonicalStoreKey);
            legacyBindingKeys = legacyBindingKeys == null ? List.of() : List.copyOf(legacyBindingKeys);
        }

        public boolean isEmpty() {
            return canonicalStoreKey == null && legacyBindingKeys.isEmpty();
        }

        public static SliceKeys none() {
            return new SliceKeys(null, List.of());
        }
    }

    public static SliceKeys forSubView(String designerTableName, List<Long> formBindingIds) {
        List<String> legacy = formBindingIds == null
                ? List.of()
                : formBindingIds.stream().map(String::valueOf).toList();
        return new SliceKeys(SubTableStoreKeys.dwKey(designerTableName), legacy);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
