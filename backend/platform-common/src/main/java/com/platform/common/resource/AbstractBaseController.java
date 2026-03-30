package com.platform.common.resource;

import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Abstract base controller providing unified request handling and error mapping
 * for all platform modules.
 *
 * <p>Provides two layers of functionality:
 * <ul>
 *   <li>{@link #handleRequest(RequestProcessor)} — full request wrapping using
 *       platform-common's {@link ApiResponse}. Used directly by admin-center.</li>
 *   <li>{@link #mapExceptionToStatus(Exception)} — exception-to-HTTP-status mapping
 *       utility. Used by developer-workstation's BaseController which has its own
 *       ApiResponse type.</li>
 * </ul>
 *
 * <p>Exception mapping:
 * <ul>
 *   <li>{@link BusinessException} → HTTP 400 Bad Request</li>
 *   <li>{@link ResourceNotFoundException} → HTTP 404 Not Found</li>
 *   <li>Generic {@link Exception} → HTTP 500 Internal Server Error</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.1, 5.2, 5.3, 5.4, 32.1, 32.2, 32.3</b>
 */
@Slf4j
public abstract class AbstractBaseController {

    /**
     * Functional interface for request processing logic.
     */
    @FunctionalInterface
    public interface RequestProcessor<T> {
        T process() throws Exception;
    }

    /**
     * Executes the given processor and wraps the result in a unified
     * {@link ApiResponse}. Exceptions are mapped to appropriate HTTP status
     * codes via {@link #handleError(Exception)}.
     */
    protected <T> ResponseEntity<ApiResponse<T>> handleRequest(RequestProcessor<T> processor) {
        try {
            T result = processor.process();
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return handleError(e);
        }
    }

    /**
     * Maps an exception to the appropriate HTTP status code and error response
     * using platform-common's ApiResponse.
     */
    protected <T> ResponseEntity<ApiResponse<T>> handleError(Exception e) {
        if (e instanceof BusinessException be) {
            log.warn("Business error: {}", be.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("BUSINESS_ERROR", be.getMessage()));
        }

        if (e instanceof ResourceNotFoundException rnfe) {
            log.warn("Resource not found: {}", rnfe.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", rnfe.getMessage()));
        }

        log.error("Internal error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Internal server error"));
    }

    /**
     * Maps an exception to an HTTP status code. Utility method for subclasses
     * that use their own ApiResponse type (e.g., developer-workstation).
     *
     * @return the appropriate HttpStatus for the exception
     */
    protected HttpStatus mapExceptionToStatus(Exception e) {
        if (e instanceof BusinessException) return HttpStatus.BAD_REQUEST;
        if (e instanceof ResourceNotFoundException) return HttpStatus.NOT_FOUND;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
