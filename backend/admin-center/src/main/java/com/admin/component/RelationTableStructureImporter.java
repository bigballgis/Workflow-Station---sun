package com.admin.component;

import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RelationTableDefinitionRepository;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import com.platform.common.relationtable.RelationTableStructureDiff;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports relation-table (rt_) structures carried in a function-unit package into admin-center.
 *
 * <p>Per requirement: a relation table whose name does not exist is created as {@code INIT}
 * (current_version bumped to 1); an existing one has its structure replaced, and only if the
 * incoming structure actually differs from what's stored is it set to {@code UPDATED} with
 * current_version incremented (a no-op re-import leaves status/version untouched). Returns
 * {@code source rt id → new rt id} so the caller can remap RELATED form-binding references.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationTableStructureImporter {

    private final RelationTableDefinitionRepository relationTableDefinitionRepository;
    private final RelationTableFieldMapper relationTableFieldMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<Long, Long> importRelationTables(List<Map<String, Object>> relationTables, String operator) {
        Map<Long, Long> sourceIdToNewId = new LinkedHashMap<>();
        Map<String, Long> nameToId = new LinkedHashMap<>();
        if (relationTables == null || relationTables.isEmpty()) {
            return sourceIdToNewId;
        }

        // Pass 1: upsert definitions + fields (ref_table_id resolved in pass 2).
        for (Map<String, Object> table : relationTables) {
            String tableName = (String) table.get("tableName");
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            RelationTableDefinition saved = upsert(table, operator);
            nameToId.put(tableName, saved.getId());
            Object srcId = table.get("relationTableId");
            if (srcId instanceof Number num) {
                sourceIdToNewId.put(num.longValue(), saved.getId());
            }
        }

        // Pass 2: resolve each field's refTableName → ref_table_id now that all tables exist.
        for (Map<String, Object> table : relationTables) {
            String tableName = (String) table.get("tableName");
            Long id = nameToId.get(tableName);
            if (id == null) {
                continue;
            }
            resolveFieldRefs(id, table, nameToId);
        }
        return sourceIdToNewId;
    }

    private RelationTableDefinition upsert(Map<String, Object> table, String operator) {
        String tableName = (String) table.get("tableName");
        String displayName = (String) table.get("displayName");
        String description = (String) table.get("description");

        RelationTableDefinition existing = relationTableDefinitionRepository.findByTableName(tableName).orElse(null);
        if (existing == null) {
            // Absent → create as INIT, version 1 (bump 0 → 1 per requirement).
            RelationTableDefinition def = RelationTableDefinition.builder()
                    .tableName(tableName)
                    .displayName(displayName)
                    .deployedDisplayName(displayName)
                    .description(description)
                    .status(RelationTableStatus.INIT)
                    .enabled(true)
                    .portalVisible(false)
                    .currentVersion(1)
                    .createdBy(operator)
                    .updatedBy(operator)
                    .build();
            def.setFieldDefinitions(buildFields(def, table));
            return relationTableDefinitionRepository.save(def);
        }

        // Present → compare against current structure before touching anything; only a real change
        // should flip status to UPDATED and bump current_version (re-importing identical content
        // must be a no-op for status/version, otherwise every re-deploy round-trip looks like a change).
        List<Map<String, Object>> incomingFieldMaps = fieldMapsFromPayload(table);
        List<Map<String, Object>> currentFieldMaps = relationTableFieldMapper.fromEntities(existing.getFieldDefinitions());
        boolean unchanged = RelationTableStructureDiff.unchanged(
                existing.getDisplayName(), existing.getDescription(), currentFieldMaps,
                displayName, description, incomingFieldMaps);

        existing.setDisplayName(displayName);
        existing.setDescription(description);
        existing.getFieldDefinitions().clear(); // orphanRemoval deletes the old field rows
        existing.getFieldDefinitions().addAll(buildFields(existing, table));
        if (!unchanged) {
            existing.setStatus(RelationTableStatus.UPDATED);
            existing.setCurrentVersion((existing.getCurrentVersion() == null ? 0 : existing.getCurrentVersion()) + 1);
            existing.setUpdatedBy(operator);
        }
        return relationTableDefinitionRepository.save(existing);
    }

    /** Normalizes the import payload's field list into the shape {@link RelationTableStructureDiff} compares. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldMapsFromPayload(Map<String, Object> table) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object fieldsObj = table.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return result;
        }
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", f.get("fieldName"));
            m.put("dataType", f.get("dataType"));
            m.put("length", asInt(f.get("length")));
            m.put("precision", asInt(f.get("precision")));
            m.put("scale", asInt(f.get("scale")));
            m.put("nullable", asBool(f.get("nullable"), true));
            m.put("isPrimaryKey", asBool(f.get("isPrimaryKey"), false));
            m.put("defaultValue", f.get("defaultValue"));
            m.put("displayName", f.get("displayName"));
            m.put("isForeignKey", asBool(f.get("isForeignKey"), false));
            m.put("refTableName", f.get("refTableName"));
            m.put("refPrimaryKeyFields", parseStringList(f.get("refPrimaryKeyFields")));
            m.put("pkGenerationJson", parseJsonMap(f.get("pkGenerationJson")));
            m.put("fkDisplayMode", f.get("fkDisplayMode") != null ? f.get("fkDisplayMode") : "readonly");
            boolean computed = Boolean.TRUE.equals(f.get("isComputed"));
            m.put("isComputed", computed);
            m.put("computedField", computed ? parseJsonMap(f.get("computedField")) : null);
            result.add(m);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<RelationFieldDefinition> buildFields(RelationTableDefinition def, Map<String, Object> table) {
        List<RelationFieldDefinition> result = new ArrayList<>();
        Object fieldsObj = table.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return result;
        }
        int order = 0;
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            Integer sortOrder = f.get("sortOrder") instanceof Number num ? num.intValue() : order;
            boolean computed = Boolean.TRUE.equals(f.get("isComputed"));
            RelationFieldDefinition field = RelationFieldDefinition.builder()
                    .tableDefinition(def)
                    .fieldName((String) f.get("fieldName"))
                    .dataType(parseDataType(f.get("dataType")))
                    .length(asInt(f.get("length")))
                    .precision(asInt(f.get("precision")))
                    .scale(asInt(f.get("scale")))
                    .nullable(asBool(f.get("nullable"), true))
                    .isPrimaryKey(asBool(f.get("isPrimaryKey"), false))
                    .defaultValue((String) f.get("defaultValue"))
                    .displayName((String) f.get("displayName"))
                    .isForeignKey(asBool(f.get("isForeignKey"), false))
                    .refTableId(null) // resolved in pass 2
                    .refPrimaryKeyFields(parseStringList(f.get("refPrimaryKeyFields")))
                    .pkGenerationJson(parseJsonMap(f.get("pkGenerationJson")))
                    .fkDisplayMode(f.get("fkDisplayMode") != null ? (String) f.get("fkDisplayMode") : "readonly")
                    .isComputed(computed)
                    .computedFieldJson(computed
                            ? requireComputedDefinition(f.get("fieldName"), f.get("computedField"))
                            : null)
                    .sortOrder(sortOrder)
                    .build();
            result.add(field);
            order++;
        }
        return result;
    }

    private void resolveFieldRefs(Long tableId, Map<String, Object> table, Map<String, Long> nameToId) {
        Object fieldsObj = table.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return;
        }
        RelationTableDefinition def = relationTableDefinitionRepository.findByIdWithFields(tableId).orElse(null);
        if (def == null) {
            return;
        }
        Map<String, RelationFieldDefinition> byName = new HashMap<>();
        for (RelationFieldDefinition fd : def.getFieldDefinitions()) {
            byName.put(fd.getFieldName(), fd);
        }
        boolean dirty = false;
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> f = (Map<String, Object>) raw;
            String refName = (String) f.get("refTableName");
            if (refName == null) {
                continue;
            }
            Long refId = nameToId.get(refName);
            if (refId == null) {
                refId = relationTableDefinitionRepository.findByTableName(refName)
                        .map(RelationTableDefinition::getId).orElse(null);
            }
            RelationFieldDefinition fd = byName.get((String) f.get("fieldName"));
            if (refId != null && fd != null) {
                fd.setRefTableId(refId);
                dirty = true;
            } else if (fd != null) {
                log.warn("Relation field {}.{} references unknown table '{}'; ref_table_id left null",
                        tableId, f.get("fieldName"), refName);
            }
        }
        if (dirty) {
            relationTableDefinitionRepository.save(def);
        }
    }

    private RelationDataType parseDataType(Object v) {
        if (v instanceof String s && !s.isBlank()) {
            try {
                return RelationDataType.valueOf(s);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown relation data type '{}', defaulting to VARCHAR", s);
            }
        }
        return RelationDataType.VARCHAR;
    }

    private Integer asInt(Object v) {
        return v instanceof Number num ? num.intValue() : null;
    }

    private Boolean asBool(Object v, boolean dflt) {
        return v instanceof Boolean b ? b : dflt;
    }

    private List<String> parseStringList(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return objectMapper.readValue(s, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse refPrimaryKeyFields '{}': {}", s, e.getMessage());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?> m) {
            return new HashMap<>((Map<String, Object>) m);
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse pkGenerationJson '{}': {}", s, e.getMessage());
            }
        }
        return null;
    }

    /**
     * A computed column must carry formula JSON (Map or JSON object string). Dropping the flag
     * and inserting a blank {@code computed_field_json} would make re-import look successful
     * while the portal write path has nothing to evaluate.
     */
    private Map<String, Object> requireComputedDefinition(Object fieldName, Object raw) {
        Map<String, Object> definition = parseJsonMap(raw);
        if (definition == null || definition.isEmpty()) {
            throw new AdminBusinessException("COMPUTED_FIELD_IMPORT_INVALID",
                    "Field '" + fieldName + "' is marked computed but has no usable formula JSON");
        }
        return definition;
    }
}
