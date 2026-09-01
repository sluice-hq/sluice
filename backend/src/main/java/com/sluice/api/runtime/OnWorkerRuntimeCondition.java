package com.sluice.api.runtime;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

final class OnWorkerRuntimeCondition extends AnyNestedCondition {
    OnWorkerRuntimeCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "sluice.runtime.mode", havingValue = "all", matchIfMissing = true)
    static class AllRuntime {
    }

    @ConditionalOnProperty(name = "sluice.runtime.mode", havingValue = "worker")
    static class WorkerRuntime {
    }
}
