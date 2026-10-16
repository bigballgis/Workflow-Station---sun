package com.portal.recordnote;

import com.portal.component.RecordNoteComponent;
import com.portal.service.RecordNoteService.RecordNoteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Notification ownership is checked first; normal note access is checked again on every open. */
@Component
@RequiredArgsConstructor
public class RecordNoteNavigationComponent {
    private final RecordNoteNavigationRepository repository;
    private final RecordNoteComponent notes;

    public com.portal.dto.RecordNoteDestination destination(String user,String submissionId) {
        var destinations=repository.destinations(submissionId,user);
        if(destinations.size()!=1) throw new com.platform.common.exception.BusinessException(com.platform.common.enums.ErrorCode.FORBIDDEN);
        var result=destinations.get(0);
        List<String> noteIds=repository.noteIds(submissionId);
        if(noteIds.size()!=1) throw new com.platform.common.exception.BusinessException(com.platform.common.enums.ErrorCode.RESOURCE_NOT_FOUND);
        try { notes.detail(user,noteIds.get(0),result.processInstanceId()); }
        catch(RecordNoteException error) {
            throw new com.platform.common.exception.BusinessException(com.platform.common.enums.ErrorCode.FORBIDDEN);
        }
        return result;
    }
}
