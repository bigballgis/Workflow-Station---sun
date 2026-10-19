package com.workflow.recordnote;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class RecordNoteInternalControllerTest {
    @Test void missingOrInvalidServiceAuthenticationIsRejectedBeforeAnyTaskLookup() {
        var targets=mock(RecordNoteTargetComponent.class);
        var controller=new RecordNoteInternalController(targets);
        ReflectionTestUtils.setField(controller,"serviceToken",java.util.UUID.randomUUID().toString());
        var error=assertThrows(BusinessException.class,() -> controller.resolve(null,"request",List.of("work")));
        assertEquals(ErrorCode.FORBIDDEN,error.getErrorCode()); verifyNoInteractions(targets);
    }
    @Test void missingServerConfigurationIsExplicitlyUnavailable() {
        var targets=mock(RecordNoteTargetComponent.class);
        var controller=new RecordNoteInternalController(targets);
        ReflectionTestUtils.setField(controller,"serviceToken","");
        var error=assertThrows(BusinessException.class,() -> controller.resolve(null,"request",List.of("work")));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,error.getErrorCode()); verifyNoInteractions(targets);
    }
}
