package com.platform.common.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentCallRowOutputsTest {
    final ObjectMapper json=new ObjectMapper();
    final List<AssignmentCallRowOutputs.Mapping> mappings=List.of(new AssignmentCallRowOutputs.Mapping("returned","reviewer"));
    @Test void fullCompositeKeySelectsTheExactRowAfterReorderingAndPreservesUnmappedValues() throws Exception {
        var rows=json.readTree("[{\"region\":\"HK\",\"number\":2,\"reviewer\":\"other\"},{\"region\":\"HK\",\"number\":1,\"reviewer\":\"old\",\"untouched\":3}]");
        var result=AssignmentCallRowOutputs.apply(rows,json.readTree("{\"region\":\"HK\",\"number\":1}"),mappings,json.readTree("{\"returned\":\"new\",\"private\":5}"));
        assertEquals("other",result.get(0).path("reviewer").asText()); assertEquals("new",result.get(1).path("reviewer").asText());
        assertEquals(3,result.get(1).path("untouched").asInt()); assertFalse(result.get(1).has("private"));
        assertEquals("old",rows.get(1).path("reviewer").asText());
    }
    @Test void explicitNullClearsAFieldWhileAnAbsentOutputDoesNotInventAValue() throws Exception {
        var rows=json.readTree("[{\"platformRowUuid\":\"row\",\"reviewer\":\"old\"}]"); var key=json.readTree("{\"platformRowUuid\":\"row\"}");
        assertEquals("old",AssignmentCallRowOutputs.apply(rows,key,mappings,json.readTree("{}")).get(0).path("reviewer").asText());
        assertTrue(AssignmentCallRowOutputs.apply(rows,key,mappings,json.readTree("{\"returned\":null}")).get(0).path("reviewer").isNull());
    }
    @Test void missingOrDuplicateRowsCannotFallBackToTheFirstRow() throws Exception {
        var rows=json.readTree("[{\"key\":1},{\"key\":1}]"); var child=json.readTree("{\"returned\":\"new\"}");
        assertThrows(IllegalArgumentException.class,()->AssignmentCallRowOutputs.apply(rows,json.readTree("{\"key\":1}"),mappings,child));
        assertThrows(IllegalArgumentException.class,()->AssignmentCallRowOutputs.apply(rows,json.readTree("{\"key\":2}"),mappings,child));
    }
}
