package com.sluice.api.job.domain;

public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    REVIEW_REQUIRED,
    COMPLETED,
    FAILED
}
