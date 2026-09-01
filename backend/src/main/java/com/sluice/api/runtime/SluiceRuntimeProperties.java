package com.sluice.api.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sluice.runtime")
public class SluiceRuntimeProperties {
    private Mode mode = Mode.ALL;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public enum Mode {
        ALL,
        API,
        WORKER
    }
}
