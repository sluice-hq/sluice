package com.sluice.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    @Test
    void doesNotExposeIllegalArgumentDetails() {
        ProblemDetail response = new GlobalExceptionHandler()
                .handleIllegalArgument(new IllegalArgumentException("internal storage URL"));

        assertEquals(400, response.getStatus());
        assertEquals("The request is invalid.", response.getDetail());
        assertEquals("invalid_request", response.getProperties().get("code"));
        assertFalse(response.getDetail().contains("storage URL"));
    }
}
