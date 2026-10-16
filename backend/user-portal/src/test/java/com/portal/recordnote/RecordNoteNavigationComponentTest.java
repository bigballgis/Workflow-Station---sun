package com.portal.recordnote;

import com.portal.component.RecordNoteComponent;
import com.portal.dto.RecordNoteDestination;
import com.portal.service.RecordNoteService.RecordNoteException;
import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordNoteNavigationComponentTest {
    final RecordNoteNavigationRepository repository=mock(RecordNoteNavigationRepository.class);
    final RecordNoteComponent notes=mock(RecordNoteComponent.class);
    final RecordNoteNavigationComponent component=new RecordNoteNavigationComponent(repository,notes);
    @Test void openingAnotherUsersNotificationCannotRevealRequestOrNote() {
        when(repository.destinations("submission","stranger")).thenReturn(List.of());
        assertThrows(BusinessException.class,() -> component.destination("stranger","submission"));
        verifyNoInteractions(notes);
    }
    @Test void oldNotificationRechecksCurrentNoteAccessBeforeReturningItsDestination() {
        var destination=new RecordNoteDestination("request","title","RECORD","row","table","DW","submission");
        when(repository.destinations("submission","recipient")).thenReturn(List.of(destination));
        when(repository.noteIds("submission")).thenReturn(List.of("note"));
        when(notes.detail("recipient","note","request")).thenThrow(new RecordNoteException("FORBIDDEN","revoked"));
        assertThrows(BusinessException.class,() -> component.destination("recipient","submission"));
    }
    @Test void permittedRecipientGetsTheExactTransactionAndSubmission() {
        var destination=new RecordNoteDestination("request","title","RECORD","row","table","DW","submission");
        when(repository.destinations("submission","recipient")).thenReturn(List.of(destination));
        when(repository.noteIds("submission")).thenReturn(List.of("note"));
        assertEquals(destination,component.destination("recipient","submission"));
        verify(notes).detail("recipient","note","request");
    }
}
