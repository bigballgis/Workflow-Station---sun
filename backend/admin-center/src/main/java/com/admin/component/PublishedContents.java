package com.admin.component;

import com.admin.dto.response.ReferenceNotice;
import com.admin.repository.FunctionUnitRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The published processes and main table views of the catalog ({@code sys_function_unit_contents}), read by the
 * reference finders of roles and business units (platform access model §4.3, §8.3).
 */
final class PublishedContents {

    private static final Pattern PROPERTY_TAG = Pattern.compile("<(?:[\\w-]+:)?property\\b([^>]*)>");
    private static final Pattern ATTRIBUTE =
            Pattern.compile("(?<![\\w:-])(name|value)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    /** One published process (BPMN XML) or set of views (JSON) of a catalog version row. */
    record Content(String functionUnitCode, String functionUnitName, boolean process, String data, String where) {
    }

    private PublishedContents() {
    }

    /**
     * Contents that may name one of these identifiers: text containing one of them, plus process definitions that older
     * imports stored Base64-encoded, whose text SQL cannot search. Callers check each exactly; a process comes decoded.
     */
    static List<Content> mayName(JdbcTemplate jdbcTemplate, Collection<String> identifiers) {
        List<String> needles = List.copyOf(identifiers);
        String anyNeedle = "(" + String.join(" OR ", Collections.nCopies(needles.size(),
                "POSITION(? IN c.content_data) > 0")) + ")";
        List<Object> params = new ArrayList<>(needles);
        params.addAll(needles);
        List<Content> contents = new ArrayList<>();
        jdbcTemplate.query("SELECT fu.code, fu.name, c.id, c.content_type, c.content_data FROM sys_function_unit_contents c "
                        + "JOIN sys_function_units fu ON fu.id = c.function_unit_id "
                        + "WHERE (c.content_type = 'PROCESS' AND (" + anyNeedle + " OR c.content_data NOT LIKE '<%')) "
                        + "OR (c.content_type = 'MAIN_TABLE_VIEW' AND " + anyNeedle + ")",
                rs -> {
                    String where = rs.getString("name") + " (content " + rs.getString("id") + ")";
                    boolean process = "PROCESS".equals(rs.getString("content_type"));
                    String data = rs.getString("content_data");
                    contents.add(new Content(rs.getString("code"), rs.getString("name"), process,
                            process ? processXml(data, where) : data, where));
                },
                params.toArray());
        return contents;
    }

    /** The access rules of every view in a published views document. */
    static List<Map<?, ?>> viewAccessRules(ObjectMapper objectMapper, Content views) {
        List<Map<String, Object>> viewList;
        try {
            viewList = objectMapper.readValue(views.data(), new TypeReference<>() { });
        } catch (Exception e) {
            // The catalog only holds views that passed import validation; an unreadable one is a broken record.
            throw new IllegalStateException("Published main table views of " + views.where() + " are not valid JSON", e);
        }
        List<Map<?, ?>> rules = new ArrayList<>();
        for (Map<String, Object> view : viewList) {
            if (view.get("accessRules") instanceof List<?> list) {
                for (Object rule : list) {
                    if (rule instanceof Map<?, ?> r) {
                        rules.add(r);
                    }
                }
            }
        }
        return rules;
    }

    /** Function units by code with their current name: the name of the latest imported version row (§8.1). */
    static List<ReferenceNotice.FunctionUnit> currentNames(JdbcTemplate jdbcTemplate, Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT code, name FROM sys_function_units WHERE code IN ("
                        + String.join(",", Collections.nCopies(codes.size(), "?")) + ") ORDER BY code, "
                        + FunctionUnitRepository.LATEST_IMPORT_ORDER,
                rs -> {
                    names.putIfAbsent(rs.getString("code"), rs.getString("name"));
                },
                codes.toArray());
        return names.entrySet().stream().map(e -> new ReferenceNotice.FunctionUnit(e.getKey(), e.getValue())).toList();
    }

    /**
     * Every extension {@code <property>} of a BPMN fragment as (name, value), in document order, whatever the order or
     * quoting of its attributes.
     */
    static List<Map.Entry<String, String>> properties(String bpmn) {
        List<Map.Entry<String, String>> properties = new ArrayList<>();
        Matcher tag = PROPERTY_TAG.matcher(bpmn);
        while (tag.find()) {
            Map<String, String> attributes = new HashMap<>();
            Matcher attribute = ATTRIBUTE.matcher(tag.group(1));
            while (attribute.find()) {
                attributes.putIfAbsent(attribute.group(1),
                        attribute.group(2) != null ? attribute.group(2) : attribute.group(3));
            }
            if (attributes.containsKey("name") && attributes.containsKey("value")) {
                properties.add(Map.entry(attributes.get("name"), attributes.get("value")));
            }
        }
        return properties;
    }

    /** A published process is BPMN XML, or that XML Base64-encoded by older imports. */
    private static String processXml(String data, String where) {
        if (data.stripLeading().startsWith("<")) {
            return data;
        }
        try {
            return new String(Base64.getDecoder().decode(data.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Published process of " + where + " is neither BPMN XML nor Base64-encoded BPMN", e);
        }
    }
}
