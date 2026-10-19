package com.portal.recordnote;

import com.portal.component.ProcessApplicationQueryComponent;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecordNoteRecipientAccessTest {
    @Test void permissionProbesReuseTheNormalReadPolicyAndExcludeRejectedUsers() {
        var repository=mock(ProcessInstanceRepository.class);
        var policy=mock(ProcessApplicationQueryComponent.class);
        var access=new RecordNoteRecipientAccess(repository,policy,mock(RecordNoteParticipantRepository.class));
        when(repository.findById("request")).thenReturn(Optional.of(ProcessInstance.builder().id("request").functionUnitCode("arbitrary").build()));
        when(policy.canAuditProcessDetail(eq("allowed"),any())).thenReturn(true);
        try { assertEquals(Set.of("allowed"),access.allowed("request",Set.of("allowed","denied"))); }
        finally { access.close(); }
        verify(repository,times(1)).findById("request");
        verify(policy).canAuditProcessDetail(eq("denied"),any());
    }
    @Test void backgroundDeliveryUsesTrustedParticipantProofWithoutAnUploaderSession() {
        var repository=mock(ProcessInstanceRepository.class);
        var policy=mock(ProcessApplicationQueryComponent.class);
        var participants=mock(RecordNoteParticipantRepository.class);
        var access=new RecordNoteRecipientAccess(repository,policy,participants);
        when(repository.findById("request")).thenReturn(Optional.of(ProcessInstance.builder().id("request").build()));
        when(participants.assignedReaders("request",Set.of("handler"))).thenReturn(Set.of("handler"));
        try { assertEquals(Set.of("handler"),access.allowed("request",Set.of("handler"))); }
        finally { access.close(); }
        verifyNoInteractions(policy);
    }

}
