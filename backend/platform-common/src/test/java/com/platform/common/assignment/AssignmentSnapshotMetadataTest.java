package com.platform.common.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentSnapshotMetadataTest {
    @Test void engineInputRetainsAllRuntimeMetadataButNoFormArchives() {
        var source=Map.<String,Object>of("_snapshot_old",Map.of("large","history"),"_snapshot_current",Map.of("draft",true),
                "__subTables__",Map.of("dw:renamed",java.util.List.of()),"_batchRuntime",Map.of("version",1),"__fuCatalogId","frozen","business_key","value");
        var result=AssignmentSnapshotMetadata.engineInput(source);
        assertEquals(4,result.size()); assertEquals("frozen",result.get("__fuCatalogId"));
        assertEquals(source.get("__subTables__"),result.get("__subTables__")); assertEquals(6,source.size());
    }
    @Test void narrowPatchKeepsExistingCurrentSnapshotAndNeverDeletesHistoricalArchives() throws Exception {
        var json=new ObjectMapper();
        var actual=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"caption\":\"before\",\"_snapshot_old\":{\"age\":20},\"_snapshot_current\":{\"draft\":true}}");
        var before=AssignmentSnapshotMetadata.baseline(actual,"current");
        var accepted=before.deepCopy().put("caption","after");accepted.putObject("_snapshot_current").put("completed",true);
        var patch=AssignmentVariablePatch.between(before,accepted);
        var applied=AssignmentVariablePatch.apply(actual,patch);
        assertEquals(actual.get("_snapshot_old"),applied.get("_snapshot_old"));
        assertTrue(applied.path("_snapshot_current").path("completed").asBoolean());
        assertEquals("after",applied.path("caption").asText());
        assertEquals(applied,AssignmentVariablePatch.apply(applied,patch));
        assertFalse(before.has("_snapshot_old"));assertTrue(before.has("_snapshot_current"));
    }
}
