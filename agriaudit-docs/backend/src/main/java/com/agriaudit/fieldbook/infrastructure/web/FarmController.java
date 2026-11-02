package com.agriaudit.fieldbook.infrastructure.web;

import com.agriaudit.fieldbook.application.FarmService;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.fieldbook.FarmDtos.FarmRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/farms")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @GetMapping
    public List<FarmDto> list() {
        return farmService.list();
    }

    @GetMapping("/{id}")
    public FarmDto get(@PathVariable Long id) {
        return farmService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public FarmDto create(@Valid @RequestBody FarmRequest request) {
        return farmService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public FarmDto update(@PathVariable Long id, @Valid @RequestBody FarmRequest request) {
        return farmService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        farmService.delete(id);
    }
}
