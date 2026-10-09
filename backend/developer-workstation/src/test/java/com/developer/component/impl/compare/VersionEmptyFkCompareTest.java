package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class VersionEmptyFkCompareTest {
    private final VersionCompareEngine engine = new VersionCompareEngine(
            new VersionSnapshotNormalizer(new ObjectMapper()));

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]"})
    void missingNullAndEmptyReferencesOnNonFkAreEquivalentInBothDirections(String empty) {
        String before = snapshot("\"isForeignKey\":false");
        String after = snapshot("\"isForeignKey\":false,\"refPrimaryKeyFields\":" + empty);
        assertNoTableChanges(before, after);
        assertNoTableChanges(after, before);
        assertNoTableChanges(snapshot("\"isForeignKey\":false,\"refPrimaryKeyFields\":null"), after);
    }

    @Test
    void appliesToLegacyFieldDefinitionsAndAuditFieldsWithoutChangingSavedBytes() {
        String before = """
                {"tableDefinitions":[{"tableName":"orders","fieldDefinitions":[
                {"fieldName":"created_at","isForeignKey":false,"refPrimaryKeyFields":null}]}]}
                """;
        String after = before.replace("null", "[]");
        Version old = version(1, before);
        Version next = version(2, after);
        assertEquals(0, count(tables(engine.compare(old, next)).semantic().counts()));
        assertEquals(before, new String(old.getSnapshotData(), StandardCharsets.UTF_8));
        assertEquals(after, new String(next.getSnapshotData(), StandardCharsets.UTF_8));
    }

    @Test
    void realFkTargetsCompositeOrderAndFlagChangesRemainVisible() {
        String fk = "\"isForeignKey\":true,\"refTableName\":\"parent\","
                + "\"refPrimaryKeyFields\":[\"tenant\",\"number\"]";
        assertChanged(snapshot(fk), snapshot(fk.replace("parent", "other")));
        assertChanged(snapshot(fk), snapshot(fk.replace("[\"tenant\",\"number\"]", "[\"number\",\"tenant\"]")));
        assertChanged(snapshot(fk), snapshot("\"isForeignKey\":false,\"refPrimaryKeyFields\":[]"));
        assertChanged(snapshot("\"isForeignKey\":true,\"refPrimaryKeyFields\":null"),
                snapshot("\"isForeignKey\":true,\"refPrimaryKeyFields\":[]"));
        // Non-empty metadata is not an empty-state alias, even on an inconsistent non-FK field.
        assertChanged(snapshot("\"isForeignKey\":false,\"refPrimaryKeyFields\":[]"),
                snapshot("\"isForeignKey\":false,\"refPrimaryKeyFields\":[\"number\"]"));
    }

    private void assertNoTableChanges(String before, String after) {
        var module = tables(engine.compare(version(1, before), version(2, after)));
        assertEquals(0, count(module.counts()));
        assertEquals(0, count(module.semantic().counts()));
        assertTrue(module.semantic().items().isEmpty());
    }

    private void assertChanged(String before, String after) {
        var module = tables(engine.compare(version(1, before), version(2, after)));
        assertTrue(count(module.counts()) > 0);
        assertTrue(count(module.semantic().counts()) > 0);
    }

    private int count(VersionCompareResponse.Counts counts) {
        return counts.added() + counts.modified() + counts.removed();
    }

    private VersionCompareResponse.ModuleDiff tables(VersionCompareResponse response) {
        return response.modules().stream().filter(m -> m.key().equals("TABLES")).findFirst().orElseThrow();
    }

    private String snapshot(String metadata) {
        return "{\"snapshotSchemaVersion\":2,\"tables\":[{\"tableName\":\"orders\",\"fields\":["
                + "{\"fieldName\":\"arbitrary_reference\"," + metadata + "}]}],"
                + "\"forms\":[],\"actions\":[],\"decisions\":[]}";
    }

    private Version version(long id, String snapshot) {
        return Version.builder().id(id).versionNumber("1.0." + id)
                .snapshotData(snapshot.getBytes(StandardCharsets.UTF_8)).build();
    }
}
