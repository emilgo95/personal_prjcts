package com.agriaudit.fieldbook.application;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.fieldbook.FarmDtos.FarmRequest;
import com.agriaudit.fieldbook.domain.Farm;
import com.agriaudit.fieldbook.domain.FarmProfile;
import com.agriaudit.fieldbook.port.FarmRepository;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Casos de uso de explotaciones: los del usuario autenticado y los de {@link FarmDirectory}. */
@Service
@Transactional
public class FarmService implements FarmDirectory {

    private final FarmRepository farms;

    public FarmService(FarmRepository farms) {
        this.farms = farms;
    }

    @Transactional(readOnly = true)
    public List<FarmDto> list() {
        return farms.findByOrganization(SecurityUtils.currentOrganizationId()).stream().map(FarmService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FarmDto get(Long id) {
        return toDto(require(id));
    }

    public FarmDto create(FarmRequest request) {
        return registerFarm(SecurityUtils.currentOrganizationId(), request);
    }

    public FarmDto update(Long id, FarmRequest request) {
        Farm farm = require(id);
        farm.updateProfile(toProfile(request));
        return toDto(farms.save(farm));
    }

    public void delete(Long id) {
        farms.delete(require(id));
    }

    // ---- FarmDirectory ----

    @Override
    @Transactional(readOnly = true)
    public Optional<FarmDto> findFarm(Long organizationId, Long farmId) {
        return farms.findById(farmId, organizationId).map(FarmService::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FarmDto> findFarms(Long organizationId, Collection<Long> farmIds) {
        return farms.findByIds(organizationId, farmIds).stream()
                .map(FarmService::toDto)
                .collect(Collectors.toMap(FarmDto::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public long countFarms(Long organizationId) {
        return farms.countByOrganization(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean anyFarmExists() {
        return farms.existsAny();
    }

    @Override
    public FarmDto registerFarm(Long organizationId, FarmRequest request) {
        return toDto(farms.save(Farm.register(organizationId, toProfile(request))));
    }

    // ---- helpers ----

    private Farm require(Long id) {
        return farms.findById(id, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Explotación no encontrada: " + id));
    }

    private static FarmProfile toProfile(FarmRequest r) {
        return new FarmProfile(r.legalName(), r.tradeName(), r.registrationNumber(), r.address(), r.city(),
                r.region(), r.country(), r.latitude(), r.longitude(), r.totalHectares(), r.crops(),
                r.productionAreas(), r.contactName(), r.contactEmail(), r.contactPhone(), r.notes());
    }

    private static FarmDto toDto(Farm farm) {
        FarmProfile p = farm.profile();
        return new FarmDto(farm.id(), p.legalName(), p.tradeName(), p.registrationNumber(), p.address(), p.city(),
                p.region(), p.country(), p.latitude(), p.longitude(), p.totalHectares(), p.crops(),
                p.productionAreas(), p.contactName(), p.contactEmail(), p.contactPhone(), p.notes());
    }
}
