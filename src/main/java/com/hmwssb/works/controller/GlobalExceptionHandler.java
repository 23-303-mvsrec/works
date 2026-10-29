package com.hmwssb.works.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Resource not found: " + ex.getResourcePath());
    }

    @ExceptionHandler(org.apache.catalina.connector.ClientAbortException.class)
    public void handleClientAbort(org.apache.catalina.connector.ClientAbortException ex) {
        log.debug("Client aborted connection: {}", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected during async response: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception ex) {
        // Check for broken pipe or client abort in cause chain
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.apache.catalina.connector.ClientAbortException ||
                (cause.getMessage() != null && cause.getMessage().contains("An established connection was aborted"))) {
                log.debug("Client disconnected: {}", cause.getMessage());
                return null;
            }
            cause = cause.getCause();
        }

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        String message = ex.getMessage();
        if (message == null || message.isEmpty()) {
            message = ex.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Server error: " + message);
    }
}
