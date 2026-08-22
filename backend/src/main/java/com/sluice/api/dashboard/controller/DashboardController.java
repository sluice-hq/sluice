package com.sluice.api.dashboard.controller;

import com.sluice.api.dashboard.dto.DashboardResponse;
import com.sluice.api.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sluice.api.auth.domain.ProjectContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(@AuthenticationPrincipal ProjectContext context) {
        return ResponseEntity.ok(dashboardService.getDashboardOverview(context));
    }
}
