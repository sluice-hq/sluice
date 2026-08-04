package com.sluice.api.asset.dto;

public class UploadUrlRequest {
    private String filename;
    private String contentType;
    private long size;

    public UploadUrlRequest() {}

    public UploadUrlRequest(String filename, String contentType, long size) {
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
}
