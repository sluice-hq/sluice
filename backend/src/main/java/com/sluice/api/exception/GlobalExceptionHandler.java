package com.sluice.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxSizeException(MaxUploadSizeExceededException exc) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "File size exceeds the configured server limit.", "payload_too_large");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exc) {
        return problem(HttpStatus.BAD_REQUEST, "The request is invalid.", "invalid_request");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException exc) {
        return problem(HttpStatus.CONFLICT, "The request conflicts with the current resource state.", "invalid_state");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exc) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is invalid.", "invalid_request_body");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exc) {
        log.error("Unhandled API exception", exc);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", "internal_error");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
