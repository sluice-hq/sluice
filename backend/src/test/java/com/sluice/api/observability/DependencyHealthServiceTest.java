package com.sluice.api.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyHealthServiceTest {

    @Test
    void dashboardReadsCachedSnapshotWithoutRunningRemoteProbes() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        DependencyHealthService service = new DependencyHealthService(endpoint, mock(SluiceMetrics.class));

        assertEquals("DEGRADED", service.current().get(0).status());
        verify(endpoint, never()).healthForPath(org.mockito.ArgumentMatchers.any(String[].class));
    }

    @Test
    void scheduledRefreshReplacesTheSnapshot() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        CompositeHealthDescriptor up = mock(CompositeHealthDescriptor.class);
        when(up.getStatus()).thenReturn(Status.UP);
        when(endpoint.healthForPath(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(up);
        DependencyHealthService service = new DependencyHealthService(endpoint, mock(SluiceMetrics.class));

        service.refresh();

        assertEquals(3, service.current().size());
        assertEquals("HEALTHY", service.current().get(0).status());
    }
}
