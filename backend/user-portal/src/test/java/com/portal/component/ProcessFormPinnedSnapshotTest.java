package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessFormPinnedSnapshotTest {
    @Test void historicalRequestUsesItsPublishedNameAndRequestScene() {
        verifyScene("COMPLETED", true, false);
    }
    @Test void returnedRequestUsesItsPublishedSubmissionScene() {
        verifyScene("RETURN_TO_REQUESTER", false, true);
    }
    private void verifyScene(String status, boolean requestScene, boolean editable) {
        var repo = mock(ProcessInstanceRepository.class);
        var snapshots = mock(CatalogSnapshotReader.class);
        var jdbc = mock(JdbcTemplate.class);
        var requests = mock(RequestIdEnricher.class);
        var form = new ProcessFormComponent(repo, mock(ChangeHistoryComponent.class), mock(RestTemplate.class),
                new ObjectMapper(), jdbc, com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
        var instance = ProcessInstance.builder().id("old").functionUnitCatalogId("v1").functionUnitCode("purchase")
                .functionUnitVersionLabel("1.0.0").status(status).variables(Map.of("value", 100)).build();
        when(repo.findById("old")).thenReturn(Optional.of(instance));
        when(snapshots.processForm("v1", requestScene)).thenReturn(new java.util.HashMap<>(Map.of(
                "formName", "Published V1", "configJson", Map.of("rule", List.of()), "tableBindings", List.of())));
        ReflectionTestUtils.setField(form, "catalogSnapshots", snapshots);
        ReflectionTestUtils.setField(form, "requestIdEnricher", requests);
        var response = form.getProcessFormData("old");
        assertThat(response.getFormName()).isEqualTo("Published V1");
        assertThat(response.isEditable()).isEqualTo(editable);
        assertThat(response.getFieldValues()).containsEntry("value", 100);
        verifyNoInteractions(jdbc);
    }
}
