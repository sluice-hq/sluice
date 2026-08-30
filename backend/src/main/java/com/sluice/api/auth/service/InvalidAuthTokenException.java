package com.sluice.api.auth.service;

public class InvalidAuthTokenException extends RuntimeException {
    public InvalidAuthTokenException() {
        super("The authentication token is invalid or expired");
    }
}
