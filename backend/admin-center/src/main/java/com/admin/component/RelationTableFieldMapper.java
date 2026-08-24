package com.admin.component;

import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.repository.RelationTableDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes {@link RelationFieldDefinition} entities into the {@code Map<String, Object>} shape
 * {@code RelationTableStructureDiff} compares, shared by {@link RelationTableStructureImporter}
 * and the Table Structure CRUD service so both stay in sync on what "unchanged" means.
 */
@Component
@RequiredArgsConstructor
public class RelationTableFieldMapper {

    private final RelationTableDefinitionRepository relationTableDefinitionRepository;

    public List<Map<String, Object>> fromEntities(List<RelationFieldDefinition> fields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RelationFieldDefinition fd : fields) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", fd.getFieldName());
            m.put("dataType", fd.getDataType() != null ? fd.getDataType().name() : null);
            m.put("length", fd.getLength());
            m.put("precision", fd.getPrecision());
            m.put("scale", fd.getScale());
            m.put("nullable", fd.getNullable());
            m.put("isPrimaryKey", fd.getIsPrimaryKey());
            m.put("defaultValue", fd.getDefaultValue());
            m.put("displayName", fd.getDisplayName());
            m.put("isForeignKey", fd.getIsForeignKey());
            m.put("refTableName", fd.getRefTableId() != null
                    ? relationTableDefinitionRepository.findById(fd.getRefTableId())
                            .map(RelationTableDefinition::getTableName).orElse(null)
                    : null);
            m.put("refPrimaryKeyFields", fd.getRefPrimaryKeyFields());
            m.put("pkGenerationJson", fd.getPkGenerationJson());
            m.put("fkDisplayMode", fd.getFkDisplayMode());
            m.put("isComputed", fd.getIsComputed());
            m.put("computedField", Boolean.TRUE.equals(fd.getIsComputed()) ? fd.getComputedFieldJson() : null);
            result.add(m);
        }
        return result;
    }
}
