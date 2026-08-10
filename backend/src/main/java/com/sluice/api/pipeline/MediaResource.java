package com.sluice.api.pipeline;

import java.io.InputStream;

public interface MediaResource {
    /**
     * Get the input stream for reading the resource.
     * The caller is responsible for closing the stream if they open it directly, 
     * but the resource itself should handle overall lifecycle cleanup.
     */
    InputStream getInputStream() throws Exception;
    
    /**
     * Get the size of the resource in bytes, if known.
     */
    long getSize() throws Exception;
    
    /**
     * Clean up any temporary resources (files, open streams, etc.) held by this MediaResource.
     * This method must be safe to call multiple times (idempotent).
     */
    void cleanup();
}
