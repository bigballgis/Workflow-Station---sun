package com.developer.component.impl;

import com.developer.entity.Version;
import com.developer.repository.VersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionLegacyComparePrivacyTest {
    @Mock private VersionRepository repository;
    @Spy private ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @InjectMocks private VersionComponentImpl component;

    @Test
    void oldCompareApiAlsoScrubsJsonTextPayloadsWithoutResolvingIds() throws Exception {
        String before = "{\"actions\":[{\"actionId\":7,\"actionName\":\"API\",\"actionType\":\"API_CALL\",\"configJson\":{\"body\":\"\"}}]}";
        String after = """
                {"actions":[{"actionId":7,"actionName":"API","actionType":"API_CALL",
                "configJson":{"headers":"{\\"Authorization\\":\\"QA_LEGACY_SECRET\\"}",
                "body":"{\\"password\\":\\"QA_LEGACY_SECRET\\",\\"phase\\":\\"beta\\"}"}}]}
                """;
        when(repository.findById(1L)).thenReturn(Optional.of(version(1, before)));
        when(repository.findById(2L)).thenReturn(Optional.of(version(2, after)));
        String json = mapper.writeValueAsString(component.compare(1L, 2L));
        assertFalse(json.contains("QA_LEGACY_SECRET"));
        assertTrue(json.contains("[REDACTED]"));
        assertTrue(json.contains("beta"));
        assertTrue(json.contains("actionId"));
    }

    @Test
    void oldCompareKeepsEncodedConfigTypeAndNeverReturnsOpaqueDigests() throws Exception {
        var before = mapper.readTree("""
                {"actionDefinitions":[{"actionId":7,"actionName":"API","actionType":"API_CALL","configJson":"{}"}]}
                """);
        var after = before.deepCopy();
        var config = mapper.createObjectNode();
        config.put("headers", "{\"Authorization\":\"QA_LEGACY_ENCODED_SECRET\"}");
        config.put("body", "password=QA_LEGACY_RAW_SECRET");
        ((com.fasterxml.jackson.databind.node.ObjectNode) after.path("actionDefinitions").get(0))
                .put("configJson", mapper.writeValueAsString(config));
        Version oldVersion = version(1, mapper.writeValueAsString(before));
        Version newVersion = version(2, mapper.writeValueAsString(after));
        when(repository.findById(1L)).thenReturn(Optional.of(oldVersion));
        when(repository.findById(2L)).thenReturn(Optional.of(newVersion));
        var response = mapper.valueToTree(component.compare(1L, 2L));
        String json = mapper.writeValueAsString(response);
        assertFalse(json.contains("QA_LEGACY_"));
        assertFalse(json.contains("!HIDDEN_ACTION_PAYLOAD:"));
        assertTrue(json.contains("[hidden configuration]"));
        assertTrue(response.path("differences").path("actionDefinitions").path("newValue").get(0).path("configJson").isTextual());
        assertTrue(json.contains("actionId"));
    }

    private Version version(long id, String snapshot) {
        return Version.builder().id(id).versionNumber("1.0." + id).publishedAt(Instant.parse("2026-10-08T00:00:00Z"))
                .snapshotData(snapshot.getBytes(StandardCharsets.UTF_8)).build();
    }
}
