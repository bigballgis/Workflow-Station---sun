package com.developer.exception;

import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class WorkspaceExceptionHandler {

    @ExceptionHandler(FunctionUnitWorkspaceAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onWorkspaceDenied(FunctionUnitWorkspaceAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "WORKSPACE_FORBIDDEN",
                        "message", ex.getMessage()));
    }
}
