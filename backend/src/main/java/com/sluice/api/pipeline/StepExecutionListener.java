package com.sluice.api.pipeline;

import java.util.Map;

public interface StepExecutionListener {
    StepExecutionListener NO_OP = new StepExecutionListener() {};

    default void beforeStep(ConfiguredStep step, MediaResource input) {}
    default void afterStep(ConfiguredStep step, MediaResource output, Map<String, Object> metadata,
                           boolean resourceChanged) {}
    default void onFailure(ConfiguredStep step, Exception exception) {}
}
