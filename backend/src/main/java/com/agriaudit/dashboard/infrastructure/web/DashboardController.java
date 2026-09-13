package com.agriaudit.dashboard.infrastructure.web;

import com.agriaudit.dashboard.application.DashboardService;
import com.agriaudit.dashboard.application.DashboardDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardDto dashboard() {
        return dashboardService.load();
    }
}
