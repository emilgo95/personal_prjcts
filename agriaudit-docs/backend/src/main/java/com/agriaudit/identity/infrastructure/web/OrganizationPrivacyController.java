package com.agriaudit.identity.infrastructure.web;

import com.agriaudit.identity.application.OrganizationPrivacyService;
import com.agriaudit.identity.application.OrganizationPrivacyService.BlockedUserRow;
import com.agriaudit.identity.application.OrganizationPrivacyService.PrivacySettings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organization/privacy")
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationPrivacyController {

    public record RetentionRequest(Integer blockedRetentionMonths) {
    }

    private final OrganizationPrivacyService privacy;

    public OrganizationPrivacyController(OrganizationPrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping
    public PrivacySettings get() {
        return privacy.get();
    }

    @GetMapping("/blocked")
    public List<BlockedUserRow> blocked() {
        return privacy.blockedUsers();
    }

    @PutMapping
    public PrivacySettings update(@RequestBody RetentionRequest request) {
        return privacy.setRetention(request.blockedRetentionMonths());
    }
}
