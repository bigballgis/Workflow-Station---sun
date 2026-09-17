package com.developer.util;

import com.developer.dto.FkFillSource;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Copy / remap / ZIP portability for {@link FormTableBinding#getFkFillSources()}.
 */
public final class FkFillSourcesSupport {

    private FkFillSourcesSupport() {
    }

    public static List<FkFillSource> copy(List<FkFillSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        List<FkFillSource> out = new ArrayList<>(sources.size());
        for (FkFillSource source : sources) {
            if (source == null) {
                continue;
            }
            out.add(copyOne(source));
        }
        return out.isEmpty() ? null : out;
    }

    private static FkFillSource copyOne(FkFillSource source) {
        return FkFillSource.builder()
                .fieldId(source.getFieldId())
                .fieldName(source.getFieldName())
                .kind(source.getKind())
                .ancestorBindingId(source.getAncestorBindingId())
                .build();
    }

    public static List<FkFillSource> stampFieldNames(List<FkFillSource> sources, TableDefinition table) {
        List<FkFillSource> copied = copy(sources);
        if (copied == null || table == null) {
            return copied;
        }
        Map<Long, String> names = new LinkedHashMap<>();
        if (table.getFieldDefinitions() != null) {
            for (FieldDefinition field : table.getFieldDefinitions()) {
                if (field.getId() != null && field.getFieldName() != null) {
                    names.put(field.getId(), field.getFieldName());
                }
            }
        }
        for (FkFillSource source : copied) {
            if (source.getFieldName() == null && source.getFieldId() != null) {
                source.setFieldName(names.get(source.getFieldId()));
            }
        }
        return copied;
    }

    public static List<FkFillSource> remapFieldIds(List<FkFillSource> sources, Map<Long, Long> fieldIdMapping) {
        if (sources == null || sources.isEmpty() || fieldIdMapping == null) {
            return copy(sources);
        }
        List<FkFillSource> out = new ArrayList<>();
        for (FkFillSource source : sources) {
            if (source == null || source.getFieldId() == null) {
                continue;
            }
            Long nextFieldId = fieldIdMapping.get(source.getFieldId());
            if (nextFieldId == null) {
                continue;
            }
            out.add(FkFillSource.builder()
                    .fieldId(nextFieldId)
                    .fieldName(source.getFieldName())
                    .kind(source.getKind())
                    .ancestorBindingId(source.getAncestorBindingId())
                    .build());
        }
        return out.isEmpty() ? null : out;
    }

    public static List<FkFillSource> remapAncestorBindingIds(
            List<FkFillSource> sources, Map<Long, Long> bindingIdMapping) {
        if (sources == null || sources.isEmpty() || bindingIdMapping == null) {
            return copy(sources);
        }
        List<FkFillSource> out = new ArrayList<>();
        for (FkFillSource source : sources) {
            if (source == null) {
                continue;
            }
            Long nextAncestor = source.getAncestorBindingId() == null
                    ? null
                    : bindingIdMapping.get(source.getAncestorBindingId());
            if (FkFillSource.KIND_ANCESTOR.equals(source.getKind()) && nextAncestor == null) {
                continue;
            }
            out.add(FkFillSource.builder()
                    .fieldId(source.getFieldId())
                    .fieldName(source.getFieldName())
                    .kind(source.getKind())
                    .ancestorBindingId(nextAncestor)
                    .build());
        }
        return out.isEmpty() ? null : out;
    }

    public static List<Map<String, Object>> toPortable(
            FormTableBinding binding,
            List<FormTableBinding> formBindings,
            Map<Long, String> tableIdToName) {
        List<FkFillSource> sources = binding.getFkFillSources();
        if (sources == null || sources.isEmpty() || binding.getTable() == null) {
            return List.of();
        }
        Map<Long, FieldDefinition> fieldsById = fieldsById(binding.getTable());
        Map<Long, FormTableBinding> siblingsById = siblingsById(formBindings);
        List<Map<String, Object>> out = new ArrayList<>();
        for (FkFillSource source : sources) {
            if (source == null || source.getFieldId() == null || source.getKind() == null) {
                continue;
            }
            FieldDefinition field = fieldsById.get(source.getFieldId());
            if (field == null || field.getFieldName() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fieldName", field.getFieldName());
            row.put("kind", source.getKind());
            if (source.getAncestorBindingId() != null) {
                FormTableBinding ancestor = siblingsById.get(source.getAncestorBindingId());
                if (ancestor != null && ancestor.getTable() != null) {
                    row.put("ancestorTableName", ancestor.getTable().getTableName());
                    row.put("ancestorFilterFkFieldName", filterFkFieldName(ancestor));
                    if (tableIdToName != null && ancestor.getTable().getId() != null) {
                        String mapped = tableIdToName.get(ancestor.getTable().getId());
                        if (mapped != null) {
                            row.put("ancestorTableName", mapped);
                        }
                    }
                }
            }
            out.add(row);
        }
        return out;
    }

    public static List<FkFillSource> fromPortable(
            Object raw,
            TableDefinition table,
            Map<String, Long> ancestorKeyToBindingId) {
        if (!(raw instanceof List<?> list) || table == null) {
            return null;
        }
        Map<String, Long> fieldIdByName = new LinkedHashMap<>();
        if (table.getFieldDefinitions() != null) {
            for (FieldDefinition field : table.getFieldDefinitions()) {
                if (field.getFieldName() != null && field.getId() != null) {
                    fieldIdByName.put(field.getFieldName(), field.getId());
                }
            }
        }
        List<FkFillSource> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object nameObj = map.get("fieldName");
            Object kindObj = map.get("kind");
            if (!(nameObj instanceof String fieldName) || fieldName.isBlank()
                    || !(kindObj instanceof String kind) || kind.isBlank()) {
                continue;
            }
            Long fieldId = fieldIdByName.get(fieldName);
            if (fieldId == null) {
                continue;
            }
            Long ancestorId = null;
            if (FkFillSource.KIND_ANCESTOR.equals(kind)) {
                String tableName = asString(map.get("ancestorTableName"));
                String filterName = asString(map.get("ancestorFilterFkFieldName"));
                if (tableName != null) {
                    ancestorId = ancestorKeyToBindingId.get(tableName + "\0" + Objects.toString(filterName, ""));
                }
                if (ancestorId == null) {
                    continue;
                }
            }
            out.add(FkFillSource.builder()
                    .fieldId(fieldId)
                    .fieldName(fieldName)
                    .kind(kind)
                    .ancestorBindingId(ancestorId)
                    .build());
        }
        return out.isEmpty() ? null : out;
    }

    public static String ancestorKey(FormTableBinding binding) {
        if (binding.getTable() == null || binding.getTable().getTableName() == null) {
            return null;
        }
        return binding.getTable().getTableName() + "\0" + Objects.toString(filterFkFieldName(binding), "");
    }

    private static String filterFkFieldName(FormTableBinding binding) {
        if (binding.getTable() == null || binding.getTable().getFieldDefinitions() == null
                || binding.getFilterFkFieldId() == null) {
            return null;
        }
        return binding.getTable().getFieldDefinitions().stream()
                .filter(f -> binding.getFilterFkFieldId().equals(f.getId()))
                .map(FieldDefinition::getFieldName)
                .findFirst()
                .orElse(null);
    }

    private static Map<Long, FieldDefinition> fieldsById(TableDefinition table) {
        Map<Long, FieldDefinition> out = new LinkedHashMap<>();
        if (table.getFieldDefinitions() == null) {
            return out;
        }
        for (FieldDefinition field : table.getFieldDefinitions()) {
            if (field.getId() != null) {
                out.put(field.getId(), field);
            }
        }
        return out;
    }

    private static Map<Long, FormTableBinding> siblingsById(List<FormTableBinding> formBindings) {
        Map<Long, FormTableBinding> out = new LinkedHashMap<>();
        if (formBindings == null) {
            return out;
        }
        for (FormTableBinding sibling : formBindings) {
            if (sibling.getId() != null) {
                out.put(sibling.getId(), sibling);
            }
        }
        return out;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
