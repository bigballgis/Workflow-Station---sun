package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.RelationTableFunctionUnit;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationTableFunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the many-to-many Relation Table <-> Function Unit links for display, shared by every
 * service that returns a {@code RelationTableResponse} (Table Structure CRUD, Table Data, Deploy).
 * A table with no rows in {@code rt_table_function_units} is Common (visible to all Function Units).
 */
@Component
@RequiredArgsConstructor
public class RelationTableFunctionUnitResolver {

    private final RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;
    private final FunctionUnitRepository functionUnitRepository;

    /** Resolves the linked Function Units for a single table (two lookups: links, then units). */
    public List<FunctionUnit> resolveOne(Long tableId) {
        List<RelationTableFunctionUnit> links = relationTableFunctionUnitRepository.findByRelationTableId(tableId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<String, FunctionUnit> byId = functionUnitRepository
                .findAllById(links.stream().map(RelationTableFunctionUnit::getFunctionUnitId).distinct().toList())
                .stream().collect(Collectors.toMap(FunctionUnit::getId, Function.identity()));
        return resolve(links, byId);
    }

    /** Batch-loads the link rows for a set of tables, avoiding N+1 lookups. */
    public Map<Long, List<RelationTableFunctionUnit>> loadLinksByTable(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return new HashMap<>();
        }
        return relationTableFunctionUnitRepository.findByRelationTableIdIn(tableIds).stream()
                .collect(Collectors.groupingBy(RelationTableFunctionUnit::getRelationTableId));
    }

    /** Batch-resolves every Function Unit referenced by the given links, avoiding N+1 lookups. */
    public Map<String, FunctionUnit> loadFunctionUnitsById(Map<Long, List<RelationTableFunctionUnit>> linksByTable) {
        List<String> ids = linksByTable.values().stream()
                .flatMap(List::stream)
                .map(RelationTableFunctionUnit::getFunctionUnitId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return functionUnitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FunctionUnit::getId, Function.identity()));
    }

    public List<FunctionUnit> resolve(List<RelationTableFunctionUnit> links, Map<String, FunctionUnit> functionUnitsById) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .map(l -> functionUnitsById.get(l.getFunctionUnitId()))
                .filter(Objects::nonNull)
                .toList();
    }
}
