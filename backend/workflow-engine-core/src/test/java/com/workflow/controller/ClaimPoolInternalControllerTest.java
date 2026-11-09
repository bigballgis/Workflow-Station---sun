package com.workflow.controller;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.security.util.ServiceTokenVerifier;
import com.workflow.component.ClaimPoolMembershipSync;
import com.workflow.dto.request.ClaimPoolResyncRequest;
import com.workflow.dto.response.ClaimPoolResyncResult;
import com.workflow.exception.AdminCenterUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Only Admin Center, by service token, asks for a resync (WP8b-2). */
class ClaimPoolInternalControllerTest {

    private static final ClaimPoolResyncRequest REQUEST = new ClaimPoolResyncRequest(
            List.of(new ClaimPoolResyncRequest.Pool("bu-1", "FIN", "role-1", "MANAGER")));

    private final ClaimPoolMembershipSync sync = mock(ClaimPoolMembershipSync.class);
    private final ClaimPoolInternalController controller =
            new ClaimPoolInternalController(new ServiceTokenVerifier("svc-token"), sync);

    @Test
    void aMissingOrWrongTokenIsUnauthorizedAndChangesNothing() {
        for (String token : new String[]{null, "", "other"}) {
            assertThatThrownBy(() -> controller.resync(token, REQUEST))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }
        verifyNoInteractions(sync);
    }

    @Test
    void theServiceTokenRunsTheResync() {
        ClaimPoolResyncResult result = new ClaimPoolResyncResult(2, 1, 0, 0, 0);
        when(sync.resync(REQUEST.pools())).thenReturn(result);

        assertThat(controller.resync("svc-token", REQUEST).getData()).isEqualTo(result);
    }

    @Test
    void adminCenterDownAnswersServiceUnavailable() {
        when(sync.resync(REQUEST.pools())).thenThrow(new AdminCenterUnavailableException("down", null));

        assertThatThrownBy(() -> controller.resync("svc-token", REQUEST))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }
}
