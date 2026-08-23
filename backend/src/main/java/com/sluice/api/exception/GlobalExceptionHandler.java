package com.sluice.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import com.sluice.api.pipeline.validation.ProcessorConfigurationException;
import com.sluice.api.pipeline.service.PipelineValidationException;
import com.sluice.api.idempotency.service.IdempotencyConflictException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProcessorConfigurationException.class)
    public ProblemDetail handleProcessorConfiguration(ProcessorConfigurationException exc) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Processor configuration validation failed.", "processor_configuration_invalid");
        problem.setProperty("processor", exc.getProcessor());
        problem.setProperty("errors", exc.getErrors());
        return problem;
    }

    @ExceptionHandler(PipelineValidationException.class)
    public ProblemDetail handlePipelineValidation(PipelineValidationException exc) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Pipeline validation failed.", "pipeline_validation_failed");
        problem.setProperty("validation", exc.getReport());
        return problem;
    }

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

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exc) {
        return problem(HttpStatus.CONFLICT, exc.getMessage(), "idempotency_key_reused");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exc) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is invalid.", "invalid_request_body");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exc) {
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.", "validation_failed");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException exc) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid email or password.", "invalid_credentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exc) {
        return problem(HttpStatus.FORBIDDEN, "You do not have permission for this operation.", "forbidden");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exc) {
        return problem(HttpStatus.CONFLICT, "The request conflicts with an existing resource.", "resource_conflict");
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
