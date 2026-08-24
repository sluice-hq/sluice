package com.sluice.api.pipeline;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;

public class FileMediaResource implements MediaResource {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileMediaResource.class);
    private final File file;
    private final String contentType;

    public FileMediaResource(File file, String contentType) {
        this.file = file;
        this.contentType = contentType;
    }

    public File getFile() {
        return file;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return new FileInputStream(file);
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public void cleanup() {
        try {
            if (file != null && file.exists()) {
                Files.deleteIfExists(file.toPath());
            }
        } catch (Exception e) {
            log.warn("media_resource_cleanup_failed path={}", file, e);
        }
    }
}
