package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationTaskPresentationTest {
    @Test void parallelStagesKeepTheirAssignmentsPairedWithoutChangingIdentityOrBusinessStage() {
        var info=ProcessInstanceInfo.builder().id("p").currentNode("sub form1").currentAssignee("original-holder")
                .candidateUsers("candidate-a,candidate-b").build();
        ApplicationTaskPresentation.apply(info,List.of(
                new TaskPresentationLabels.Display("t1","p","multi: sub form1","Alice",List.of("person-a")),
                new TaskPresentationLabels.Display("t2","p","multi: sub form2","Finance + Manager",List.of("person-b"))));
        assertThat(info.getCurrentStepName()).isEqualTo("multi: sub form1; multi: sub form2");
        assertThat(info.getCurrentAssigneeDisplay()).isEqualTo("multi: sub form1 → Alice; multi: sub form2 → Finance + Manager");
        assertThat(info.getCurrentAssignee()).isEqualTo("original-holder");
        assertThat(info.getCurrentNode()).isEqualTo("sub form1");
        assertThat(info.getCandidateUsers()).isEqualTo("candidate-a,candidate-b");
        assertThat(info.getCurrentAssigneeUserIds()).containsExactly("person-a","person-b");
    }
    @Test void duplicateStagesAreNotRepeatedAndAnEmptyPoolIsAuthoritative() {
        var info=new ProcessInstanceInfo();
        ApplicationTaskPresentation.apply(info,List.of(
                new TaskPresentationLabels.Display("t1","p","multi: Review","",List.of()),
                new TaskPresentationLabels.Display("t2","p","multi: Review","",List.of())));
        assertThat(info.getCurrentStepName()).isEqualTo("multi: Review");
        assertThat(info.getCurrentAssigneeDisplay()).isEmpty();
    }
    @Test void aLargeApplicationPageIsReadInBoundedBatches() {
        var labels=mock(TaskPresentationLabels.class);
        when(labels.read(anyList(),anyList())).thenReturn(List.of());
        var page=new ArrayList<ProcessInstanceInfo>();
        for(int i=0;i<205;i++) page.add(ProcessInstanceInfo.builder().id("p"+i).status("RUNNING").build());
        new ApplicationTaskPresentation(labels).annotate(page);
        var batches=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(labels,times(3)).read(batches.capture(),eq(List.of()));
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(100,100,5);
    }
}
