package com.sluice.api.runtime;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

final class OnApiRuntimeCondition extends AnyNestedCondition {
    OnApiRuntimeCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "sluice.runtime.mode", havingValue = "all", matchIfMissing = true)
    static class AllRuntime {
    }

    @ConditionalOnProperty(name = "sluice.runtime.mode", havingValue = "api")
    static class ApiRuntime {
    }
}
