package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.GlobalExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/** Only the added assignment veto uses the platform envelope; native legacy errors keep their existing handler. */
@RestControllerAdvice(basePackages={"org.flowable.rest.service.api.runtime","org.flowable.rest.service.api.management","org.flowable.rest.service.api.history","org.flowable.rest.service.api.repository"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssignmentNativeRestErrors {
    private final GlobalExceptionHandler errors;
    public AssignmentNativeRestErrors(GlobalExceptionHandler errors) { this.errors=errors; }
    @ExceptionHandler(AssignmentFailure.class)
    public ResponseEntity<ApiResponse<?>> rejected(AssignmentFailure failure,WebRequest request) {
        return errors.handlePlatformException(failure,request);
    }
}
