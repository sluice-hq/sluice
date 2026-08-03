package com.sluice.api.pipeline;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.job.domain.Job;
import java.util.HashMap;
import java.util.Map;

public class ProcessingContext {
    private final Job job;
    private final Asset asset;
    private final byte[] fileBytes;
    private final Map<String, Object> attributes = new HashMap<>();

    public ProcessingContext(Job job, Asset asset, byte[] fileBytes) {
        this.job = job;
        this.asset = asset;
        this.fileBytes = fileBytes;
    }

    public Job getJob() { return job; }
    public Asset getAsset() { return asset; }
    public byte[] getFileBytes() { return fileBytes; }
    public Map<String, Object> getAttributes() { return attributes; }
}
