package com.sluice.api.worker;

public class PermanentProcessingException extends RuntimeException {
    private final String code;
    public PermanentProcessingException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
