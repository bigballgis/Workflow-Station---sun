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

class VersionFormCompareFixTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionCompareEngine engine = new VersionCompareEngine(new VersionSnapshotNormalizer(mapper));

    @Test
    void renamePreservesParentChildrenAndSubFormIdentity() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        form(b).put("formName", "Renamed");
        var d = diff(a, b);
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), d.counts());
        assertEquals("FORM", d.items().get(0).objectType());
        assertEquals("Case", field(d.items().get(0), "formName").oldValue());
        assertEquals("Renamed", field(d.items().get(0), "formName").newValue());
        assertReverse(a, b);
    }

    @Test
    void topLevelReorderShowsPositionsNotDeletion() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ArrayNode rules = rules(b); var first = rules.remove(0); rules.add(first);
        var d = diff(a, b);
        assertEquals(new VersionCompareResponse.Counts(0, 2, 0), d.counts());
        assertTrue(d.items().stream().allMatch(i -> i.fields().stream().anyMatch(f -> f.field().equals("position"))));
        assertReverse(a, b);
    }

    @Test
    void nestedReorderKeepsChildIdentity() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ArrayNode children = (ArrayNode) rules(b).get(1).path("children");
        var first = children.remove(0); children.add(first);
        assertEquals(new VersionCompareResponse.Counts(0, 2, 0), diff(a, b).counts());
        assertReverse(a, b);
    }

    @Test
    void fieldRenameUsesSavedControlIdentity() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ((ObjectNode) rules(b).get(0)).put("field", "renamed_field");
        var d = diff(a, b);
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), d.counts());
        assertEquals("title", field(d.items().get(0), "field").oldValue());
        assertEquals("renamed_field", field(d.items().get(0), "field").newValue());
        assertReverse(a, b);
    }

    @Test
    void bindingRemovalDoesNotRecreateUnchangedOrphanControls() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ((ArrayNode) form(b).path("tableBindings")).removeAll();
        var d = diff(a, b);
        assertEquals(new VersionCompareResponse.Counts(0, 0, 1), d.counts());
        assertEquals("FORM_BINDING", d.items().get(0).objectType());
        assertEquals("PARTIAL", d.scope());
        assertReverse(a, b);
    }

    @Test
    void changedOrphanContentIsStillReported() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ((ArrayNode) form(b).path("tableBindings")).removeAll();
        ((ObjectNode) form(b).path("configJson").path("subForms").path("41").path("rule").get(0)).put("title", "Changed");
        var d = diff(a, b);
        assertEquals(new VersionCompareResponse.Counts(0, 1, 1), d.counts());
        assertEquals("Changed", field(d.items().stream().filter(i -> i.objectType().equals("FORM_CONTROL"))
                .findFirst().orElseThrow(), "title").newValue());
    }

    @Test
    void duplicateFormNamesRemainDistinctAndOnlyOneChanges() {
        ObjectNode a = snapshot();
        ObjectNode second = form(a).deepCopy().put("formId", 8);
        second.putArray("tableBindings"); ((ObjectNode) second.path("configJson")).remove("subForms");
        ((ArrayNode) a.path("forms")).add(second);
        ObjectNode b = a.deepCopy();
        ((ObjectNode) b.path("forms").get(1)).put("description", "Changed");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b).counts());
        assertReverse(a, b);
    }

    @Test
    void renamingFormDoesNotAlterReferencesToAnotherFormWhoseNameSharesItsPrefix() {
        ObjectNode a = snapshot();
        ObjectNode foreign = form(a).deepCopy().put("formId", 8).put("formName", "Case/Other");
        ((ObjectNode) foreign.path("tableBindings").get(0)).put("bindingId", 42);
        foreign.putObject("configJson").putArray("rule"); ((ArrayNode) a.path("forms")).add(foreign);
        ((ObjectNode) rules(a).get(0)).putObject("props").put("bindingId", 42);
        ObjectNode b = a.deepCopy(); form(b).put("formName", "Renamed");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b).counts());
        assertReverse(a, b);
    }

    @Test
    void sameIdAcrossFunctionUnitsCannotRecognizeRename() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy(); form(b).put("formName", "Other");
        Version target = version(2, b); target.setFunctionUnit(FunctionUnit.builder().id(2L).build());
        var result = engine.compare(version(1, a), target).modules().stream()
                .filter(m -> m.key().equals("FORMS")).findFirst().orElseThrow().semantic();
        assertTrue(result.counts().added() > 0); assertTrue(result.counts().removed() > 0);
    }

    @Test
    void legacyFormRenameDoesNotInventIdentity() {
        ObjectNode a = snapshot(); form(a).remove("formId"); ObjectNode b = a.deepCopy(); form(b).put("formName", "Other");
        var d = diff(a, b);
        assertTrue(d.counts().added() > 0); assertTrue(d.counts().removed() > 0); assertEquals("PARTIAL", d.scope());
    }

    @Test
    void regeneratedFormAndBindingIdsRemainComparable() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy(); form(b).put("formId", 70);
        ((ObjectNode) form(b).path("tableBindings").get(0)).put("bindingId", 410);
        ObjectNode subForms = (ObjectNode) form(b).path("configJson").path("subForms");
        var sub = subForms.remove("41"); subForms.set("410", sub);
        ((ObjectNode) sub.path("rule").get(0).path("props")).put("bindingId", 410);
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), diff(a, b).counts());
    }

    @Test
    void duplicateSavedFormIdsAreRejected() {
        ObjectNode a = snapshot(); ((ArrayNode) a.path("forms")).add(form(a).deepCopy().put("formName", "Other"));
        assertThrows(DeveloperBusinessException.class, () -> diff(a, a));
    }

    @Test
    void fcIdMatchesAFieldRenameWhenControlNameIsAbsent() {
        ObjectNode a = snapshot(); ((ObjectNode) rules(a).get(0)).remove("name");
        ObjectNode b = a.deepCopy(); ((ObjectNode) rules(b).get(0)).put("field", "changed");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b).counts());
    }

    @Test
    void fcIdStillMatchesWhenBothNameAndFieldChange() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ((ObjectNode) rules(b).get(0)).put("field", "changed").put("name", "changed-name");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b).counts());
        assertReverse(a, b);
    }

    @Test
    void orphanEvidenceIsNeverBorrowedFromAnotherFunctionUnit() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        ((ArrayNode) form(b).path("tableBindings")).removeAll();
        Version target = version(2, b); target.setFunctionUnit(FunctionUnit.builder().id(2L).build());
        var d = engine.compare(version(1, a), target).modules().stream()
                .filter(m -> m.key().equals("FORMS")).findFirst().orElseThrow().semantic();
        assertEquals("PARTIAL", d.scope());
        assertTrue(d.items().stream().anyMatch(i -> i.type().equals("ADDED") && i.objectType().equals("FORM_CONTROL")));
    }

    @Test
    void duplicateFieldNamesUseDistinctSavedControlNames() {
        ObjectNode a = snapshot(); ((ObjectNode) rules(a).get(1)).put("field", "title");
        ObjectNode b = a.deepCopy(); ((ObjectNode) rules(b).get(1)).put("title", "Layout Changed");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b).counts());
        assertReverse(a, b);
    }

    @Test
    void bothNestedCollectionsAreScopedIndependently() {
        ObjectNode a = snapshot();
        ((ObjectNode) rules(a).get(1)).putArray("rule").addObject().put("name", "nested-a").put("field", "child-a");
        ObjectNode b = a.deepCopy();
        ((ObjectNode) rules(b).get(1).path("rule").get(0)).put("title", "Changed");
        assertEquals(new VersionCompareResponse.Counts(0, 1, 0), diff(a, b).counts());
    }

    @Test
    void unresolvedOrphanWithoutPairedEvidenceIsPartialNotGuessed() {
        ObjectNode a = snapshot(); ((ArrayNode) form(a).path("tableBindings")).removeAll();
        ObjectNode b = a.deepCopy();
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), diff(a, b).counts());
        assertEquals("PARTIAL", diff(a, b).scope());
    }

    @Test
    void additionAndDeletionRemainDirectionalAndNoopIsZero() {
        ObjectNode a = snapshot(); ObjectNode b = a.deepCopy();
        rules(b).addObject().put("name", "new-control").put("field", "new-field");
        assertEquals(new VersionCompareResponse.Counts(1, 0, 0), diff(a, b).counts());
        assertEquals(new VersionCompareResponse.Counts(0, 0, 1), diff(b, a).counts());
        assertEquals(new VersionCompareResponse.Counts(0, 0, 0), diff(a, a.deepCopy()).counts());
    }

    @Test
    void legacyControlFieldRenameWithoutIdentityIsConservative() {
        ObjectNode a = snapshot(); ((ObjectNode) rules(a).get(0)).remove(java.util.List.of("name", "_fc_id"));
        ObjectNode b = a.deepCopy(); ((ObjectNode) rules(b).get(0)).put("field", "changed");
        assertEquals(new VersionCompareResponse.Counts(1, 0, 1), diff(a, b).counts());
    }

    private void assertReverse(ObjectNode a, ObjectNode b) {
        var f = diff(a, b); var r = diff(b, a);
        assertEquals(new VersionCompareResponse.Counts(f.counts().removed(), f.counts().modified(), f.counts().added()), r.counts());
        for (var i : f.items()) {
            var back = r.items().stream().filter(j -> j.objectKey().equals(i.objectKey())).findFirst().orElseThrow();
            for (var v : i.fields()) {
                var swap = field(back, v.field());
                assertEquals(v.oldValue(), swap.newValue()); assertEquals(v.newValue(), swap.oldValue());
            }
        }
    }

    private VersionCompareResponse.FieldChange field(VersionCompareResponse.SemanticChange item, String name) {
        return item.fields().stream().filter(f -> f.field().equals(name)).findFirst().orElseThrow();
    }

    private VersionCompareResponse.SemanticDiff diff(ObjectNode a, ObjectNode b) {
        return engine.compare(version(1, a), version(2, b)).modules().stream()
                .filter(m -> m.key().equals("FORMS")).findFirst().orElseThrow().semantic();
    }

    private Version version(long id, ObjectNode snapshot) {
        return Version.builder().id(id).versionNumber("1.0." + id).functionUnit(FunctionUnit.builder().id(1L).build())
                .snapshotData(snapshot.toString().getBytes(StandardCharsets.UTF_8)).build();
    }

    private ObjectNode form(ObjectNode root) { return (ObjectNode) root.path("forms").get(0); }
    private ArrayNode rules(ObjectNode root) { return (ArrayNode) form(root).path("configJson").path("rule"); }

    private ObjectNode snapshot() {
        ObjectNode root = mapper.createObjectNode().put("snapshotSchemaVersion", 2);
        for (String key : new String[]{"tables", "forms", "actions", "decisions", "mainTableViews"}) root.putArray(key);
        ObjectNode f = ((ArrayNode) root.path("forms")).addObject().put("formId", 7).put("formName", "Case");
        f.putArray("tableBindings").addObject().put("bindingId", 41).put("bindingType", "SUB")
                .put("tableName", "detail").put("filterFkFieldName", "parent");
        ObjectNode config = f.putObject("configJson"); ArrayNode rules = config.putArray("rule");
        rules.addObject().put("type", "input").put("field", "title").put("name", "control-a").put("_fc_id", "fc-a");
        ArrayNode children = rules.addObject().put("type", "card").put("name", "layout").putArray("children");
        children.addObject().put("field", "child-a").put("name", "nested-a");
        children.addObject().put("field", "child-b").put("name", "nested-b");
        config.putObject("subForms").putObject("41").putArray("rule").addObject()
                .put("field", "note").put("name", "sub-note").put("title", "Note").putObject("props").put("bindingId", 41);
        return root;
    }
}
