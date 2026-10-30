package com.platform.common.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Applies only declared output fields to the exact frozen call-row identity, independent of row order or binding aliases. */
public final class AssignmentCallRowOutputs {
    private AssignmentCallRowOutputs() { }
    public record Mapping(String from,String to) { }
    public static ArrayNode apply(JsonNode source,JsonNode key,List<Mapping> mappings,JsonNode child) {
        int selectedIndex=index(source,key);
        if (child==null || !child.isObject()) throw invalid();
        var result=((ArrayNode)source).deepCopy(); var selected=(ObjectNode)result.get(selectedIndex);
        for (var mapping:mappings) {
            if (mapping.from()==null || mapping.from().isBlank() || mapping.to()==null || mapping.to().isBlank()) throw invalid();
            if (child.has(mapping.from())) selected.set(mapping.to(),child.get(mapping.from()).deepCopy());
        }
        return result;
    }
    public static ObjectNode read(JsonNode source,JsonNode key) { return ((ObjectNode)source.get(index(source,key))).deepCopy(); }
    private static int index(JsonNode source,JsonNode key) {
        if (!(source instanceof ArrayNode rows) || key==null || !key.isObject() || key.isEmpty() || key.size()>32) throw invalid();
        for (var value:key) if (value.isNull() || !value.isValueNode() || value.asText().isBlank()) throw invalid();
        int selected=-1;
        for (int at=0;at<rows.size();at++) {
            var row=rows.get(at);
            if (!row.isObject()) throw invalid();
            boolean matches=true; var fields=key.fields();
            while (fields.hasNext()) { var field=fields.next(); if (!field.getValue().equals(row.get(field.getKey()))) { matches=false; break; } }
            if (matches) { if (selected!=-1) throw invalid(); selected=at; }
        }
        if (selected<0) throw invalid();
        return selected;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Configured call output row or mapping is unavailable"); }
}
