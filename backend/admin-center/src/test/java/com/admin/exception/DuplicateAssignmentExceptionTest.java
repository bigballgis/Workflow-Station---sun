package com.admin.exception;

import com.platform.common.dto.ApiResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateAssignmentExceptionTest {

    private final AdminApiExceptionHandler handler = new AdminApiExceptionHandler();

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ROLE", "BUSINESS_UNIT"})
    void duplicateAssignmentUsesEnglishConflictResponseForEveryTargetType(String targetType) {
        DuplicateAssignmentException exception =
                new DuplicateAssignmentException("dashboard-1", targetType, "target-1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bi/assignments");

        ResponseEntity<ApiResponse<Void>> response = handler.handleConflict(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("DUPLICATE_ASSIGNMENT");
        assertThat(response.getBody().getError().getMessage())
                .isEqualTo("Assignment already exists: dashboardId=dashboard-1, targetType="
                        + targetType + ", targetId=target-1");
    }
}
