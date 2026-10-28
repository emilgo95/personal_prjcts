package com.agriaudit.fieldbook.application;

import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.fieldbook.FarmDtos.FarmRequest;
import com.agriaudit.fieldbook.domain.Farm;
import com.agriaudit.fieldbook.domain.FarmProfile;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ChangeRecorder trace;

    public FarmService(FarmRepository farms, ChangeRecorder trace) {
        this.trace = trace;
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
        requireUniqueRegistration(farm.organizationId(), request.registrationNumber(), farm.id());
        FarmProfile before = farm.profile();
        farm.updateProfile(toProfile(request));
        Farm saved = farms.save(farm);
        Changes c = profileChanges(before, saved.profile());
        if (!c.isEmpty()) {
            trace.record(ChangeEntry.of(TraceEntity.FARM, saved.id(), saved.profile().legalName(), null,
                    TraceAction.UPDATE, "Modificó la explotación «" + saved.profile().legalName() + "»", c.list()));
        }
        return toDto(saved);
    }

    public void delete(Long id) {
        Farm farm = require(id);
        farms.delete(farm);
        trace.record(ChangeEntry.of(TraceEntity.FARM, farm.id(), farm.profile().legalName(), null,
                TraceAction.DELETE, "Eliminó la explotación «" + farm.profile().legalName() + "»",
                Changes.create().track("legalName", "Razón social", farm.profile().legalName(), null).list()));
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
        requireUniqueRegistration(organizationId, request.registrationNumber(), null);
        Farm saved = farms.save(Farm.register(organizationId, toProfile(request)));
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            trace.record(ChangeEntry.of(TraceEntity.FARM, saved.id(), saved.profile().legalName(), null,
                    TraceAction.CREATE, "Creó la explotación «" + saved.profile().legalName() + "»",
                    profileChanges(null, saved.profile()).list()));
        }
        return toDto(saved);
    }

    private static Changes profileChanges(FarmProfile before, FarmProfile after) {
        FarmProfile b = before;
        Changes c = Changes.create();
        c.track("legalName", "Razón social", b == null ? null : b.legalName(), after.legalName())
                .track("tradeName", "Nombre comercial", b == null ? null : b.tradeName(), after.tradeName())
                .track("registrationNumber", "GGN / Nº de registro", b == null ? null : b.registrationNumber(),
                        after.registrationNumber())
                .track("address", "Dirección", b == null ? null : b.address(), after.address())
                .track("city", "Localidad", b == null ? null : b.city(), after.city())
                .track("region", "Región", b == null ? null : b.region(), after.region())
                .track("country", "País", b == null ? null : b.country(), after.country())
                .track("totalHectares", "Hectáreas", b == null ? null : b.totalHectares(), after.totalHectares())
                .track("crops", "Cultivos", b == null ? null : b.crops(), after.crops())
                .track("productionAreas", "Áreas de producción", b == null ? null : b.productionAreas(),
                        after.productionAreas())
                .track("contactName", "Contacto", b == null ? null : b.contactName(), after.contactName())
                .track("contactEmail", "Email de contacto", b == null ? null : b.contactEmail(),
                        after.contactEmail())
                .track("contactPhone", "Teléfono de contacto", b == null ? null : b.contactPhone(),
                        after.contactPhone())
                .track("notes", "Notas", b == null ? null : b.notes(), after.notes());
        return c;
    }

    // ---- helpers ----

    /** El GGN o nº de registro identifica a una explotación: no puede repetirse en la misma organización. */
    private void requireUniqueRegistration(Long organizationId, String registrationNumber, Long excludeFarmId) {
        if (registrationNumber == null || registrationNumber.isBlank()) {
            return;
        }
        String wanted = registrationNumber.trim();
        boolean duplicated = farms.findByOrganization(organizationId).stream()
                .filter(f -> excludeFarmId == null || !f.id().equals(excludeFarmId))
                .map(f -> f.profile().registrationNumber())
                .anyMatch(n -> n != null && n.trim().equalsIgnoreCase(wanted));
        if (duplicated) {
            throw new ConflictException("Ya existe otra explotación con el número de registro «" + wanted + "».");
        }
    }

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
