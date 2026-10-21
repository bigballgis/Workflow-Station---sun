package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/** A task's deployed BPMN and form belong to its immutable release. */
@Component
@RequiredArgsConstructor
public class TaskFormDefinitionLoader {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.platform.common.functionunit.CatalogSnapshotReader versionSnapshots;

    private com.platform.common.functionunit.CatalogSnapshotReader snapshots() {
        return versionSnapshots != null ? versionSnapshots
                : new com.platform.common.functionunit.CatalogSnapshotReader(jdbcTemplate, objectMapper);
    }

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> fetchTaskFormByStageId(String stageId, String processInstanceId,
                                                     String developerWorkstationUrl) {
        Map<String, Object> form = CatalogPinnedTaskFormLoader.forInstance(
                jdbcTemplate, objectMapper, processInstanceId, stageId, snapshots());
        return form.isEmpty() ? null : form;
    }
}
