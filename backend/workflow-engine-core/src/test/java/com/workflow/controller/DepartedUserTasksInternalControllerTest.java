package com.workflow.controller;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.security.util.ServiceTokenVerifier;
import com.workflow.component.DepartedHolderReassignment;
import com.workflow.dto.request.DepartedUsersRequest;
import com.workflow.dto.response.DepartedReassignResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Only Admin Center, by service token, reports users who left (D-42). */
class DepartedUserTasksInternalControllerTest {

    private static final DepartedUsersRequest REQUEST = new DepartedUsersRequest(List.of("b"));

    private final DepartedHolderReassignment reassignment = mock(DepartedHolderReassignment.class);
    private final DepartedUserTasksInternalController controller =
            new DepartedUserTasksInternalController(new ServiceTokenVerifier("svc-token"), reassignment);

    @Test
    void aMissingOrWrongTokenIsUnauthorizedAndChangesNothing() {
        for (String token : new String[]{null, "", "other"}) {
            assertThatThrownBy(() -> controller.reassign(token, REQUEST))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }
        verifyNoInteractions(reassignment);
    }

    @Test
    void theServiceTokenHandsTheTasksOn() {
        DepartedReassignResult result = new DepartedReassignResult(2, 1, 1, 0);
        when(reassignment.reassign(REQUEST.userIds())).thenReturn(result);

        assertThat(controller.reassign("svc-token", REQUEST).getData()).isEqualTo(result);
    }
}
