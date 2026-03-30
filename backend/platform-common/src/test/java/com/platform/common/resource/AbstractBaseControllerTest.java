package com.platform.common.resource;

import com.platform.common.dto.ApiResponse;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit and property-based tests for {@link AbstractBaseController}.
 *
 * <p><b>Validates: Requirements 5.2, 5.3, 5.4, 32.1, 32.2, 32.3</b>
 */
@DisplayName("AbstractBaseController")
class AbstractBaseControllerTest {

    /** Concrete subclass used only for testing the abstract base. */
    private static class TestController extends AbstractBaseController { }

    private TestController controller;

    @BeforeEach
    void setUp() {
        controller = new TestController();
    }

    // ── Unit tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("handleRequest success returns HTTP 200 with success=true")
    void handleRequest_success_returns200() {
        ResponseEntity<ApiResponse<String>> response =
                controller.handleRequest(() -> "hello");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("handleRequest with BusinessException returns HTTP 400")
    void handleRequest_businessException_returns400() {
        ResponseEntity<ApiResponse<String>> response =
                controller.handleRequest(() -> {
                    throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "bad input");
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("handleRequest with ResourceNotFoundException returns HTTP 404")
    void handleRequest_resourceNotFound_returns404() {
        ResponseEntity<ApiResponse<String>> response =
                controller.handleRequest(() -> {
                    throw new ResourceNotFoundException("Unit", "42");
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("handleRequest with generic Exception returns HTTP 500")
    void handleRequest_genericException_returns500() {
        ResponseEntity<ApiResponse<String>> response =
                controller.handleRequest(() -> {
                    throw new RuntimeException("unexpected");
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("handleRequest with null result returns HTTP 200 with null data")
    void handleRequest_nullResult_returns200() {
        ResponseEntity<ApiResponse<Object>> response =
                controller.handleRequest(() -> null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNull();
    }

    // ── Property-based tests (jqwik) ────────────────────────────────────

    /**
     * **Validates: Requirements 5.2, 32.1**
     *
     * Property: For any successful processor result, handleRequest always
     * returns HTTP 200 with success=true and the original data preserved.
     */
    @Property(tries = 100)
    @Label("handleRequest success → HTTP 200 + ApiResponse(success=true)")
    void successAlwaysReturns200(@ForAll String data) {
        TestController ctrl = new TestController();

        ResponseEntity<ApiResponse<String>> response =
                ctrl.handleRequest(() -> data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(data);
    }

    /**
     * **Validates: Requirements 5.3, 32.2**
     *
     * Property: BusinessException always maps to HTTP 400 with success=false.
     */
    @Property(tries = 100)
    @Label("BusinessException → HTTP 400")
    void businessExceptionAlwaysReturns400(@ForAll String message) {
        TestController ctrl = new TestController();

        ResponseEntity<ApiResponse<Object>> response =
                ctrl.handleRequest(() -> {
                    throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, message);
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    /**
     * **Validates: Requirements 5.4, 32.3**
     *
     * Property: ResourceNotFoundException always maps to HTTP 404 with success=false.
     */
    @Property(tries = 100)
    @Label("ResourceNotFoundException → HTTP 404")
    void resourceNotFoundAlwaysReturns404(
            @ForAll String resourceType,
            @ForAll String identifier) {
        TestController ctrl = new TestController();

        ResponseEntity<ApiResponse<Object>> response =
                ctrl.handleRequest(() -> {
                    throw new ResourceNotFoundException(resourceType, identifier);
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    /**
     * **Validates: Requirements 5.4, 32.3**
     *
     * Property: Any generic (non-business, non-not-found) exception maps to HTTP 500.
     */
    @Property(tries = 100)
    @Label("generic Exception → HTTP 500")
    void genericExceptionAlwaysReturns500(@ForAll String message) {
        TestController ctrl = new TestController();

        ResponseEntity<ApiResponse<Object>> response =
                ctrl.handleRequest(() -> {
                    throw new RuntimeException(message);
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }
}
