package com.agriaudit.web;

import com.agriaudit.service.CatalogService;
import com.agriaudit.web.dto.CatalogDtos.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/standards")
    public List<StandardSummaryDto> standards() {
        return catalogService.listStandards();
    }

    @GetMapping("/standards/{code}")
    public StandardDetailDto standard(@PathVariable String code) {
        return catalogService.getStandard(code);
    }

    @GetMapping("/standards/{code}/controls")
    public List<ControlPointDto> controls(@PathVariable String code) {
        return catalogService.listControlPoints(code);
    }

    @GetMapping("/regulations")
    public List<RegulationDto> regulations() {
        return catalogService.listRegulations();
    }

    @GetMapping("/regulations/{code}")
    public RegulationDto regulation(@PathVariable String code) {
        return catalogService.getRegulation(code);
    }
}
