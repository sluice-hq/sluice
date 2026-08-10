package com.sluice.api.pipeline;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.job.domain.Job;
import java.util.HashMap;
import java.util.Map;

public class ProcessingContext {
    private final Job job;
    private final Asset asset;
    private MediaResource currentResource;
    private final Map<String, Object> attributes = new HashMap<>();

    public ProcessingContext(Job job, Asset asset, MediaResource currentResource) {
        this.job = job;
        this.asset = asset;
        this.currentResource = currentResource;
    }

    public Job getJob() { return job; }
    public Asset getAsset() { return asset; }
    
    public MediaResource getCurrentResource() { return currentResource; }
    public void setCurrentResource(MediaResource resource) { this.currentResource = resource; }
    
    public Map<String, Object> getAttributes() { return attributes; }
}
