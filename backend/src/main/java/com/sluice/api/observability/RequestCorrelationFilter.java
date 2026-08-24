package com.sluice.api.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Adds a safe request identifier and records low-cardinality API metrics. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private final MeterRegistry registry;

    public RequestCorrelationFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = safeRequestId(request.getHeader(HEADER));
        long started = System.nanoTime();
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedNanos = System.nanoTime() - started;
            String method = request.getMethod();
            String api = request.getRequestURI().startsWith("/api/") ? "api" : "other";
            registry.timer("sluice.http.server.requests", "method", method, "api", api,
                    "status", Integer.toString(response.getStatus())).record(elapsedNanos, TimeUnit.NANOSECONDS);
            log.info("request_completed requestId={} method={} pathGroup={} status={} durationMs={}",
                    requestId, method, api, response.getStatus(), elapsedNanos / 1_000_000);
            MDC.remove(MDC_KEY);
        }
    }

    private String safeRequestId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}")) return supplied;
        return UUID.randomUUID().toString();
    }
}
