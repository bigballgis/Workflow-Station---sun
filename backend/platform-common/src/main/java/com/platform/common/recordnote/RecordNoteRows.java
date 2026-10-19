package com.platform.common.recordnote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Traversal of canonical JSON sub-table stores; business field names never determine scope. */
public final class RecordNoteRows {
    private RecordNoteRows() {}

    public static List<Map<String,Object>> rows(Object variables,String tableName) {
        List<Map<String,Object>> rows=new ArrayList<>();
        collect(variables,"dw:"+tableName.toLowerCase(java.util.Locale.ROOT),rows);
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object value,String key,List<Map<String,Object>> rows) {
        if (value instanceof Map<?,?> map) {
            if (map.get("__subTables__") instanceof Map<?,?> stores) {
                if(stores.get(key) instanceof List<?> list) {
                    for (Object row : list) if (row instanceof Map<?,?> m) rows.add((Map<String,Object>)m);
                }
                // Only canonical rows own nested tables. Approval snapshots and arbitrary JSON fields
                // may contain old __subTables__ copies and must never establish current ownership.
                for(Object child:stores.values()) if(child instanceof List<?> list) {
                    for(Object row:list) collect(row,key,rows);
                }
            }
        }
    }

    public static boolean matches(Map<String,Object> row,Map<String,Object> key) {
        return !key.isEmpty() && key.entrySet().stream().allMatch(e -> row.containsKey(e.getKey())
                && String.valueOf(row.get(e.getKey())).equals(String.valueOf(e.getValue())));
    }
}
