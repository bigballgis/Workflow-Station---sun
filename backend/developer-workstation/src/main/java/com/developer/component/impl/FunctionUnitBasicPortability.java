package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.IconRepository;
import com.developer.util.FunctionUnitTagUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** #1706: self-contained historical metadata, shared by ZIP import and version restore. */
@Component
@RequiredArgsConstructor
public class FunctionUnitBasicPortability {
    private final IconRepository icons;

    static Map<String, Object> iconData(Icon icon) {
        if (icon == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", icon.getName());
        value.put("category", icon.getCategory() == null ? null : icon.getCategory().name());
        value.put("svgContent", icon.getSvgContent());
        return value;
    }

    void restore(FunctionUnit fu, Map<String, Object> source) {
        // Pre-#1706 snapshots/packages omitted these fields. Preserve current values, never
        // reconstruct historical configuration from today's DB. Remove only after legacy migration.
        if (source.containsKey("tags")) fu.setTags(readTags(source.get("tags")));
        if (source.containsKey("icon")) fu.setIcon(readIcon(source.get("icon")));
    }

    private List<String> readTags(Object raw) {
        if (!(raw instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) throw invalid();
        return FunctionUnitTagUtils.normalizeTags(list.stream().map(String.class::cast).toList());
    }

    private Icon readIcon(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?> value) || !(value.get("name") instanceof String name)
                || name.isBlank() || name.length() > 100 || !(value.get("svgContent") instanceof String svg)
                || svg.isBlank() || svg.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) throw invalid();
        IconCategory category;
        try { category = IconCategory.valueOf(String.valueOf(value.get("category"))); }
        catch (IllegalArgumentException e) { throw invalid(); }
        // Do not reuse a same-name icon with different content, or a mutable local numeric ID.
        return icons.findFirstByNameAndCategoryAndSvgContentOrderByIdAsc(name, category, svg)
                .orElseGet(() -> icons.save(Icon.builder().name(name).category(category).svgContent(svg)
                        .fileSize(svg.getBytes(StandardCharsets.UTF_8).length).build()));
    }

    private DeveloperBusinessException invalid() {
        return new DeveloperBusinessException("VAL_FUNCTION_UNIT_METADATA_INVALID", "Invalid Function Unit tags or icon metadata");
    }
}
