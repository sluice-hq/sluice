package com.sluice.api.pipeline;

import java.util.List;

public class Pipeline {
    private final List<ConfiguredStep> steps;

    public Pipeline(List<ConfiguredStep> steps) {
        this.steps = steps;
    }

    public List<ConfiguredStep> getSteps() {
        return steps;
    }
}
