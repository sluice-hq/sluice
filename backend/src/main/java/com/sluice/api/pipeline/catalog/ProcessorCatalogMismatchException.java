package com.sluice.api.pipeline.catalog;

public class ProcessorCatalogMismatchException extends IllegalStateException {
    public ProcessorCatalogMismatchException(String message) {
        super(message);
    }
}
