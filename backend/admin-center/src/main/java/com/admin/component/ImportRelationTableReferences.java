package com.admin.component;

import com.admin.enums.ContentType;
import com.admin.exception.AdminBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.functionunit.CatalogSnapshotReader;
import java.util.List;
import java.util.Map;

/** Rewrites only declared relation/Lookup references, never arbitrary business tableId fields. */
public final class ImportRelationTableReferences {
    private ImportRelationTableReferences() { }

    public static void remap(List<FunctionUnitManagerComponent.ContentInfo> contents,
                             Map<Long,Long> identities,ObjectMapper mapper) {
        if(contents==null) return;
        for(var content:contents) {
            if(content.getContentType()!=ContentType.FORM) continue;
            try {
                JsonNode root=mapper.readTree(content.getContentData());
                JsonNode bindings=root.path("tableBindings");
                if(bindings.isArray()) for(var binding:bindings) {
                    if("RELATED".equals(binding.path("bindingType").asText()) && binding.hasNonNull("relationTableId")) {
                        ((ObjectNode)binding).put("relationTableId",target(binding.get("relationTableId").asLong(),identities));
                    }
                }
                JsonNode config=root.get("configJson");
                if(config!=null && !config.isNull()) {
                    boolean text=config.isTextual();
                    JsonNode form=text ? mapper.readTree(config.asText()) : config;
                    walk(form,identities,mapper);
                    if(text) ((ObjectNode)root).put("configJson",mapper.writeValueAsString(form));
                }
                content.setContentData(mapper.writeValueAsString(root));
            } catch(AdminBusinessException ex) { throw ex; }
            catch(java.io.IOException ex) {
                throw new AdminBusinessException("FU_IMPORT_FORM_REFERENCES_INVALID","Invalid published form references",ex);
            }
        }
    }

    private static void walk(JsonNode node,Map<Long,Long> identities,ObjectMapper mapper) throws java.io.IOException {
        if(node==null) return;
        if(node.isObject()) {
            if("lookup".equalsIgnoreCase(node.path("type").asText())) {
                JsonNode props=node.path("props");
                JsonNode raw=props.get("lookupConfig");
                if(raw!=null && !raw.isNull()) {
                    boolean text=raw.isTextual();
                    JsonNode lookup=text ? mapper.readTree(raw.asText()) : raw;
                    if(lookup.hasNonNull("tableId")) {
                        ((ObjectNode)lookup).put("tableId",target(lookup.get("tableId").asLong(),identities));
                        if(text) ((ObjectNode)props).put("lookupConfig",mapper.writeValueAsString(lookup));
                    }
                }
            }
            var values=node.elements();
            while(values.hasNext()) walk(values.next(),identities,mapper);
        } else if(node.isArray()) {
            for(var child:node) walk(child,identities,mapper);
        }
    }

    private static long target(long source,Map<Long,Long> identities) {
        if(source==CatalogSnapshotReader.SYSTEM_USERS_RELATION_TABLE_ID) return source;
        Long target=identities.get(source);
        if(target==null) throw new AdminBusinessException("FU_IMPORT_RELATION_REFERENCE_UNRESOLVED",
                "Form refers to a relation table missing from this package");
        return target;
    }
}
