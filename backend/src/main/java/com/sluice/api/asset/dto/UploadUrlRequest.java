package com.sluice.api.asset.dto;

public class UploadUrlRequest {
    private String filename;
    private String contentType;
    private long size;
    private String externalSubjectId;
    private String externalReference;

    public UploadUrlRequest() {}

    public UploadUrlRequest(String filename, String contentType, long size) {
        this(filename, contentType, size, null, null);
    }

    public UploadUrlRequest(String filename, String contentType, long size,
                            String externalSubjectId, String externalReference) {
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
        this.externalSubjectId = externalSubjectId;
        this.externalReference = externalReference;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Caller-supplied opaque subject correlation ID. Not authorization; do not send personal data or secrets.",
            example = "user_123", maxLength = 128, pattern = "[A-Za-z0-9][A-Za-z0-9._:/-]*")
    public String getExternalSubjectId() { return externalSubjectId; }
    public void setExternalSubjectId(String externalSubjectId) { this.externalSubjectId = externalSubjectId; }

    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Caller-supplied opaque media or grouping reference. Not authorization; do not send personal data or secrets.",
            example = "avatar_2026_08", maxLength = 255, pattern = "[A-Za-z0-9][A-Za-z0-9._:/-]*")
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
}
