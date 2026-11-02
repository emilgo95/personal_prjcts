package com.agriaudit.findings.infrastructure.web;

import com.agriaudit.findings.FindingDtos.FindingListDto;
import com.agriaudit.findings.application.FindingListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** No conformidades de toda la organización (Auditorías › No conformidades). */
@RestController
@RequestMapping("/api/findings")
public class FindingListController {

    private final FindingListService findings;

    public FindingListController(FindingListService findings) {
        this.findings = findings;
    }

    /**
     * {@code view}: OPEN (por defecto), OVERDUE, DUE_2, DUE_7, DUE_14, CLOSED_12M, CLOSED_LATE o ALL; con las mismas
     * reglas que las cifras del panel.
     */
    @GetMapping
    public FindingListDto list(@RequestParam(required = false) String view) {
        return findings.list(view);
    }
}
