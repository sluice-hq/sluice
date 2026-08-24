package com.sluice.api.pipeline;

import java.io.InputStream;

public class StreamMediaResource implements MediaResource {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StreamMediaResource.class);
    private final InputStream inputStream;
    private final long size;

    public StreamMediaResource(InputStream inputStream, long size) {
        this.inputStream = inputStream;
        this.size = size;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return inputStream;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void cleanup() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception e) {
            log.warn("stream_resource_cleanup_failed", e);
        }
    }
}
