package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessApplicationQueryComponentDetailAccessTest {
    @Test void presentationTextDoesNotMakeAnUnrelatedUserAParticipant() {
        var detail=ProcessInstanceInfo.builder().id("p").startUserId("initiator").currentAssignee("multi")
                .candidateUsers("real-person").currentAssigneeDisplay("Finance + Manager").build();
        when(userDisplayNameResolver.resolve("outsider")).thenReturn("Finance + Manager");
        assertThat(component.isProcessParticipant("outsider",detail)).isFalse();
        assertThat(component.isProcessParticipant("real-person",detail)).isTrue();
    }
    @Test void actualHolderIdentityStillWorksWhenParallelPresentationHasStagePrefixes() {
        var detail=ProcessInstanceInfo.builder().id("p").startUserId("initiator").currentAssignee("actual-holder")
                .currentAssigneeDisplay("multi: Stage A → Alice; multi: Stage B → Bob").build();
        assertThat(component.isProcessParticipant("actual-holder",detail)).isTrue();
    }

    @Mock private ProcessInstanceRepository processInstanceRepository;
    @Mock private com.portal.client.WorkflowEngineClient workflowEngineClient;
    @Mock private EngineSubTableHydrator engineSubTableHydrator;
    @Mock private UserDisplayNameResolver userDisplayNameResolver;
    @Mock private MiOverlayComponent miOverlayComponent;
    @Mock private SubTableEnrichmentComponent subTableEnrichmentComponent;
    @Mock private MainTableViewInvolvementChecker mainTableViewInvolvementChecker;
    @Mock private MainTableViewAccessResolver mainTableViewAccessResolver;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FunctionUnitAccessComponent functionUnitAccessComponent;

    @Mock private com.portal.client.PortalBiClient dataViewClient;
    @Mock private com.portal.service.PortalMainTableViewService viewService;
    private ProcessApplicationQueryComponent component;

    @BeforeEach
    void setUp() {
        component = new ProcessApplicationQueryComponent(
                processInstanceRepository,
                workflowEngineClient,
                engineSubTableHydrator,
                userDisplayNameResolver,
                miOverlayComponent,
                subTableEnrichmentComponent,
                mainTableViewInvolvementChecker,
                mainTableViewAccessResolver,
                functionUnitAccessComponent,
                jdbcTemplate,
                org.mockito.Mockito.mock(MiOuterStepResolver.class));
        org.springframework.test.util.ReflectionTestUtils.setField(component, "taskPresentation", org.mockito.Mockito.mock(ApplicationTaskPresentation.class));
        org.springframework.test.util.ReflectionTestUtils.setField(component,"dataViewClient",dataViewClient);
        org.springframework.test.util.ReflectionTestUtils.setField(component,"viewService",viewService);
        lenient().when(workflowEngineClient.isAvailable()).thenReturn(false);
    }

    @Test
    void canAccessProcessDetail_allowsParticipant() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("pi-1")
                .startUserId("user-dev")
                .build();

        assertThat(component.canAccessProcessDetail("user-dev", detail)).isTrue();
    }

    @Test
    void canAccessProcessDetail_allowsOpenViewReaderWhenNotRestrictedToInvolved() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("pi-email")
                .startUserId("system")
                .functionUnitCode("test-20260803-xc0jmo")
                .build();
        when(dataViewClient.getDataViews("user-dev")).thenReturn(List.of(releasedView(false)));
        when(viewService.containsProcess("user-dev",1L,"pi-email")).thenReturn(true);

        assertThat(component.canAccessProcessDetail("user-dev", detail)).isTrue();
    }

    @Test
    void canAccessProcessDetail_deniesWhenRestrictedViewAndUserNotInvolved() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("pi-email")
                .startUserId("system")
                .functionUnitCode("test-20260803-xc0jmo")
                .build();
        when(dataViewClient.getDataViews("user-dev")).thenReturn(List.of(releasedView(true)));
        when(viewService.containsProcess("user-dev",1L,"pi-email")).thenReturn(false);

        assertThat(component.canAccessProcessDetail("user-dev", detail)).isFalse();
    }

    @Test
    void canAccessProcessDetail_throwsWhenAccessRulesLoadFails() {
        ProcessInstanceInfo detail = ProcessInstanceInfo.builder()
                .id("pi-email")
                .startUserId("system")
                .functionUnitCode("test-20260803-xc0jmo")
                .build();
        when(dataViewClient.getDataViews("user-dev")).thenThrow(new IllegalStateException("Admin down"));
        assertThatThrownBy(() -> component.canAccessProcessDetail("user-dev", detail))
                .isInstanceOf(IllegalStateException.class).hasMessage("Admin down");
    }
    private com.platform.common.dataview.RuntimeDataView releasedView(boolean restricted) {
        return new com.platform.common.dataview.RuntimeDataView(1L,1L,1L,"key","release","test-20260803-xc0jmo",
                "Test","1.0",null,"main","Main","MAIN",Map.of("restrictToInvolvedUsers",restricted),
                List.of(),List.of(),List.of(),false);
    }
}
