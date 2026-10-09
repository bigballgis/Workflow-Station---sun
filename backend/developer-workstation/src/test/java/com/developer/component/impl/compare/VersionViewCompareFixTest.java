package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class VersionViewCompareFixTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test
    void savedIdRenameChangesOnlyViewAndPreservesChildren() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "A", "main", "Title");
        ObjectNode b = a.deepCopy(); ((ObjectNode) b.path("mainTableViews").get(0)).put("viewName", "B");
        var f = diff(a, b); var r = diff(b, a);
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), f.counts());
        assertEquals(f.items().get(0).objectKey(), r.items().get(0).objectKey());
        assertEquals("main/B", f.items().get(0).label());
        assertEquals("A", field(f.items().get(0), "viewName").oldValue());
        assertEquals("B", field(f.items().get(0), "viewName").newValue());
        assertEquals("A", field(r.items().get(0), "viewName").newValue());
    }

    @Test
    void duplicateNamesRetainBothRecordsAndModifyOnlyOne() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "Same", "main", "One"); view(a, 8, "Same", "main", "Two");
        ObjectNode b = a.deepCopy(); ((ObjectNode) b.path("mainTableViews").get(1).path("fields").get(0)).put("displayLabel", "Changed");
        var d = diff(a, b);
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), d.counts());
        assertEquals("VIEW_FIELD", d.items().get(0).objectType());
        assertEquals("Two", field(d.items().get(0), "displayLabel").oldValue());
        assertEquals("Changed", field(d.items().get(0), "displayLabel").newValue());
    }

    @Test
    void identicalDuplicateAdditionAndRemovalAreNotDropped() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "Same", "main", "Title");
        ObjectNode b = a.deepCopy(); view(b, 8, "Same", "main", "Title");
        assertEquals(new VersionCompareResponse.Counts(2, 0, 0), diff(a, b).counts());
        assertEquals(new VersionCompareResponse.Counts(0, 0, 2), diff(b, a).counts());
    }

    @Test
    void legacyDuplicatesRemainComparableAndMultisetReorderIsZero() throws Exception {
        ObjectNode a = snapshot(); view(a, 0, "Same", "main", "One"); view(a, 0, "Same", "main", "Two");
        ObjectNode b = snapshot(); view(b, 0, "Same", "main", "Two"); view(b, 0, "Same", "main", "One");
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), diff(a, b).counts());
        assertEquals("PARTIAL", diff(a, b).scope());
        view(b, 0, "Same", "main", "Three");
        assertEquals(new VersionCompareResponse.Counts(2, 0, 0), diff(a, b).counts());
        assertEquals(new VersionCompareResponse.Counts(0, 0, 2), diff(b, a).counts());
    }

    @Test
    void ambiguousLegacyDuplicatesAreNotArbitrarilyPaired() throws Exception {
        ObjectNode a = snapshot(); view(a, 0, "Same", "main", "One"); view(a, 0, "Same", "main", "Two");
        ObjectNode b = snapshot(); view(b, 0, "Same", "main", "Three"); view(b, 0, "Same", "main", "Four");
        assertEquals(new VersionCompareResponse.Counts(4, 0, 4), diff(a, b).counts());
        assertEquals("PARTIAL", diff(a, b).scope());
    }

    @Test
    void oldSnapshotsDoNotInventRenameIdentity() throws Exception {
        ObjectNode a = snapshot(); view(a, 0, "A", "main", "Title");
        ObjectNode b = snapshot(); view(b, 0, "B", "main", "Title");
        assertEquals(new VersionCompareResponse.Counts(2, 0, 2), diff(a, b).counts());
        assertEquals("PARTIAL", diff(a, b).scope());
    }

    @Test
    void savedIdsWinDuringNameSwapAndNameReuse() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "A", "main", "Title"); view(a, 8, "B", "main", "Title");
        ObjectNode b = snapshot(); view(b, 7, "B", "main", "Title"); view(b, 8, "A", "main", "Title");
        assertEquals(new VersionCompareResponse.Counts(0, 2, 0), diff(a, b).counts());
        view(b, 9, "A", "main", "Title");
        assertEquals(new VersionCompareResponse.Counts(2, 2, 0), diff(a, b).counts());
    }

    @Test
    void regeneratedIdsAndDifferentTableSameNamesRemainPortable() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "Same", "main", "One"); view(a, 8, "Same", "sub", "Two");
        ObjectNode b = snapshot(); view(b, 70, "Same", "main", "One"); view(b, 80, "Same", "sub", "Two");
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), diff(a, b).counts());
        assertFalse(mapper.writeValueAsString(engine.compare(version(1, a, 1), version(2, b, 1))).contains("viewId"));
    }

    @Test
    void capturedDuplicateIdsAreInvalidButNotDuplicateNames() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "A", "main", "One"); view(a, 7, "B", "main", "Two");
        assertThrows(DeveloperBusinessException.class, () -> diff(a, a));
    }

    @Test
    void viewIdentityMetadataDoesNotStripSameNamedFormConfiguration() throws Exception {
        ObjectNode a = snapshot();
        ((ArrayNode) a.path("forms")).addObject().put("formName", "Form")
                .putObject("configJson").put("viewId", 7);
        ObjectNode b = a.deepCopy();
        ((ObjectNode) b.path("forms").get(0).path("configJson")).put("viewId", 8);
        var semantic = engine.compare(version(1, a, 1), version(2, b, 1)).modules().stream()
                .filter(m -> m.key().equals("FORMS")).findFirst().orElseThrow().semantic();
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), semantic.counts());
        assertEquals("7", field(semantic.items().get(0), "config.viewId").oldValue());
        assertEquals("8", field(semantic.items().get(0), "config.viewId").newValue());
    }

    @Test
    void sameNumericIdAcrossFunctionUnitsDoesNotMatchRename() throws Exception {
        ObjectNode a = snapshot(); view(a, 7, "A", "main", "Title");
        ObjectNode b = snapshot(); view(b, 7, "B", "main", "Title");
        var response = engine.compare(version(1, a, 1), version(2, b, 2));
        assertEquals(new VersionCompareResponse.Counts(2, 0, 2), response.modules().stream()
                .filter(m -> m.key().equals("VIEWS")).findFirst().orElseThrow().semantic().counts());
    }

    private VersionCompareResponse.SemanticDiff diff(ObjectNode a, ObjectNode b) throws Exception {
        return engine.compare(version(1, a, 1), version(2, b, 1)).modules().stream()
                .filter(m -> m.key().equals("VIEWS")).findFirst().orElseThrow().semantic();
    }

    private VersionCompareResponse.FieldChange field(VersionCompareResponse.SemanticChange i, String key) {
        return i.fields().stream().filter(f -> f.field().equals(key)).findFirst().orElseThrow();
    }

    private Version version(long id, ObjectNode snapshot, long fu) throws Exception {
        return Version.builder().id(id).versionNumber("1.0." + id).functionUnit(FunctionUnit.builder().id(fu).build())
                .snapshotData(snapshot.toString().getBytes(StandardCharsets.UTF_8)).build();
    }

    private ObjectNode snapshot() {
        ObjectNode root = mapper.createObjectNode().put("snapshotSchemaVersion", 2);
        for (String key : new String[]{"tables", "forms", "actions", "decisions", "mainTableViews"}) root.putArray(key);
        return root;
    }

    private void view(ObjectNode root, long id, String name, String table, String title) {
        ObjectNode v = ((ArrayNode) root.path("mainTableViews")).addObject().put("mainTableName", table).put("viewName", name);
        if (id > 0) v.put("viewId", id);
        v.putArray("fields").addObject().put("fieldName", "title").put("displayLabel", title).put("sortOrder", 0);
        v.putArray("accessRules");
    }
}
