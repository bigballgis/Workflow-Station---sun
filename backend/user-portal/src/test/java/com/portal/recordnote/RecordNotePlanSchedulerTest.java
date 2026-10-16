package com.portal.recordnote;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;

class RecordNotePlanSchedulerTest {
    @Test void pausedWorkerDoesNotReadOrConsumeAcceptedSubmissions() {
        var repository=mock(RecordNoteSubmissionRepository.class);var delivery=mock(RecordNoteDeliveryComponent.class);
        var scheduler=new RecordNoteDeliveryScheduler(repository,delivery);ReflectionTestUtils.setField(scheduler,"paused",true);
        scheduler.dispatch();verifyNoInteractions(repository,delivery);
    }
    @Test void resumeProcessesDurablePendingIds() {
        var repository=mock(RecordNoteSubmissionRepository.class);var delivery=mock(RecordNoteDeliveryComponent.class);
        var scheduler=new RecordNoteDeliveryScheduler(repository,delivery);ReflectionTestUtils.setField(scheduler,"paused",true);scheduler.dispatch();
        when(repository.due()).thenReturn(List.of("accepted-before-pause"));ReflectionTestUtils.setField(scheduler,"paused",false);scheduler.dispatch();
        verify(delivery).deliver("accepted-before-pause");
    }
    @Test void failedDeliverySchedulesRetryAndDoesNotPreventTheNextId() {
        var repository=mock(RecordNoteSubmissionRepository.class);var delivery=mock(RecordNoteDeliveryComponent.class);
        when(repository.due()).thenReturn(List.of("fails","next"));doThrow(new IllegalStateException("controlled test failure")).when(delivery).deliver("fails");
        new RecordNoteDeliveryScheduler(repository,delivery).dispatch();verify(repository).retry("fails");verify(delivery).deliver("next");
    }
}
