package com.developer.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Function Unit 标签规范化：trim、去空、去重、长度与数量上限。
 */
public final class FunctionUnitTagUtils {

    private static final int MAX_TAGS = 20;
    private static final int MAX_TAG_LENGTH = 50;

    private FunctionUnitTagUtils() {
    }

    public static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_TAG_LENGTH) {
                continue;
            }
            unique.add(trimmed);
            if (unique.size() >= MAX_TAGS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }
}
