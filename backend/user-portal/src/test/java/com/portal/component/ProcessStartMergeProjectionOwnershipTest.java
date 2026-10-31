package com.portal.component;

import com.platform.common.i18n.I18nService;
import com.portal.entity.ProcessHistory;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProcessStartMergeProjectionOwnershipTest {
    private final ProcessStartComponent component=mock(ProcessStartComponent.class,CALLS_REAL_METHODS);
    private final ProcessInstanceRepository instances=mock(ProcessInstanceRepository.class);
    private final ProcessHistoryRepository history=mock(ProcessHistoryRepository.class);
    private final OwnerFieldComponent owners=mock(OwnerFieldComponent.class);

    @ParameterizedTest
    @ValueSource(strings={"READY","PENDING_SYNC","SYNC_CONFLICT"})
    void acceptedCompletionOwnsProjectionEvenWhenLiveOutcomeHasANode(String state) throws Exception {
        Object outcome=outcome("Live successor");
        ReflectionTestUtils.setField(outcome,"initialCompletion",
                new AssignmentCompletionOrchestrator.Result(UUID.randomUUID(),state,null));
        record(outcome);
        verifyNoInteractions(instances,owners);
        assertStartHistory();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={"  "})
    void blankLegacyOutcomePreservesTheStartPayloadProjection(String node) throws Exception {
        record(outcome(node));
        verifyNoInteractions(instances,owners);
        assertStartHistory();
    }

    private Object outcome(String node) throws Exception {
        var type=Class.forName(ProcessStartComponent.class.getName()+"$FirstTaskOutcome");
        var constructor=type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object result=constructor.newInstance();
        ReflectionTestUtils.setField(result,"currentNodeName",node);
        return result;
    }

    private void record(Object outcome) {
        ReflectionTestUtils.setField(component,"processInstanceRepository",instances);
        ReflectionTestUtils.setField(component,"processHistoryRepository",history);
        ReflectionTestUtils.setField(component,"ownerFieldComponent",owners);
        ReflectionTestUtils.setField(component,"i18nService",mock(I18nService.class));
        ReflectionTestUtils.invokeMethod(component,"updateInstanceNodeAndRecordStartHistory",
                "instance",outcome,"actor","Actor");
    }

    private void assertStartHistory() {
        var saved=ArgumentCaptor.forClass(ProcessHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getProcessInstanceId()).isEqualTo("instance");
        assertThat(saved.getValue().getOperationType()).isEqualTo("SUBMIT");
        assertThat(saved.getValue().getOperatorId()).isEqualTo("actor");
    }
}
