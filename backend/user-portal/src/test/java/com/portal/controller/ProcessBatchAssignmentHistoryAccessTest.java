package com.portal.controller;

import com.portal.component.ProcessComponent;
import com.portal.component.SubTableBatchAssignmentHistory;
import com.portal.dto.ProcessInstanceInfo;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessBatchAssignmentHistoryAccessTest {
    @Mock ProcessComponent processComponent;
    @Mock SubTableBatchAssignmentHistory batchAssignmentHistory;
    @Mock I18nService i18nService;
    @InjectMocks ProcessController controller;

    @org.junit.jupiter.api.BeforeEach void injectHistory() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "batchAssignmentHistory", batchAssignmentHistory);
    }

    @Test void deniedViewCannotQueryPersistedResults() {
        var detail = ProcessInstanceInfo.builder().id("synthetic-instance").build();
        when(processComponent.getProcessDetail("synthetic-instance")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("other-user", detail)).thenReturn(false);
        var response = controller.getProcessHistory("other-user", "synthetic-instance");
        assertThat(response.isSuccess()).isFalse();
        verifyNoInteractions(batchAssignmentHistory);
    }

    @Test void authorizedViewIncludesDurableObservationsEvenWithNoEngineHistory() {
        var detail = ProcessInstanceInfo.builder().id("synthetic-instance").build();
        when(processComponent.getProcessDetail("synthetic-instance")).thenReturn(detail);
        when(processComponent.canAuditProcessDetail("synthetic-user", detail)).thenReturn(true);
        when(processComponent.getProcessHistory("synthetic-instance")).thenReturn(List.of());
        List<Map<String, Object>> saved = List.of(Map.of("id", "synthetic-observation"));
        when(batchAssignmentHistory.merge("synthetic-instance", List.of())).thenReturn(saved);
        assertThat(controller.getProcessHistory("synthetic-user", "synthetic-instance").getData()).isEqualTo(saved);
    }

    @Test void missingProcessDoesNotExposeOrphanAuditRows() {
        when(processComponent.getProcessHistory("missing")).thenReturn(List.of());
        assertThat(controller.getProcessHistory("synthetic-user", "missing").getData()).isEmpty();
        verifyNoInteractions(batchAssignmentHistory);
    }
}
