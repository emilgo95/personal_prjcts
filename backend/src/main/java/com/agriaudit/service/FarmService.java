package com.agriaudit.service;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.Farm;
import com.agriaudit.repo.FarmRepository;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.web.dto.FarmDtos.FarmDto;
import com.agriaudit.web.dto.FarmDtos.FarmRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FarmService {

    private final FarmRepository farms;

    public FarmService(FarmRepository farms) {
        this.farms = farms;
    }

    @Transactional(readOnly = true)
    public List<FarmDto> list() {
        return farms.findByOrganizationIdOrderByLegalNameAsc(SecurityUtils.currentOrganizationId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FarmDto get(Long id) {
        return toDto(require(id));
    }

    public FarmDto create(FarmRequest request) {
        Farm farm = new Farm();
        farm.setOrganizationId(SecurityUtils.currentOrganizationId());
        apply(farm, request);
        return toDto(farms.save(farm));
    }

    public FarmDto update(Long id, FarmRequest request) {
        Farm farm = require(id);
        apply(farm, request);
        return toDto(farms.save(farm));
    }

    public void delete(Long id) {
        farms.delete(require(id));
    }

    Farm require(Long id) {
        return farms.findByIdAndOrganizationId(id, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Explotación no encontrada: " + id));
    }

    private void apply(Farm farm, FarmRequest r) {
        farm.setLegalName(r.legalName());
        farm.setTradeName(r.tradeName());
        farm.setRegistrationNumber(r.registrationNumber());
        farm.setAddress(r.address());
        farm.setCity(r.city());
        farm.setRegion(r.region());
        farm.setCountry(r.country());
        farm.setLatitude(r.latitude());
        farm.setLongitude(r.longitude());
        farm.setTotalHectares(r.totalHectares());
        farm.setCrops(r.crops());
        farm.setProductionAreas(r.productionAreas());
        farm.setContactName(r.contactName());
        farm.setContactEmail(r.contactEmail());
        farm.setContactPhone(r.contactPhone());
        farm.setNotes(r.notes());
    }

    private FarmDto toDto(Farm f) {
        return new FarmDto(f.getId(), f.getLegalName(), f.getTradeName(), f.getRegistrationNumber(),
                f.getAddress(), f.getCity(), f.getRegion(), f.getCountry(), f.getLatitude(), f.getLongitude(),
                f.getTotalHectares(), f.getCrops(), f.getProductionAreas(), f.getContactName(),
                f.getContactEmail(), f.getContactPhone(), f.getNotes());
    }
}
