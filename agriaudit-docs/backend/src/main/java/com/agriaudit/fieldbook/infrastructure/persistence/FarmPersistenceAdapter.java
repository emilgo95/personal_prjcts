package com.agriaudit.fieldbook.infrastructure.persistence;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.fieldbook.domain.Farm;
import com.agriaudit.fieldbook.domain.FarmProfile;
import com.agriaudit.fieldbook.port.FarmRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adaptador JPA/PostgreSQL del puerto {@link FarmRepository}. */
@Component
class FarmPersistenceAdapter implements FarmRepository {

    private final FarmJpaRepository jpa;

    FarmPersistenceAdapter(FarmJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Farm save(Farm farm) {
        FarmJpaEntity entity;
        if (farm.id() == null) {
            entity = new FarmJpaEntity();
            entity.setOrganizationId(farm.organizationId());
        } else {
            entity = jpa.findByIdAndOrganizationId(farm.id(), farm.organizationId())
                    .orElseThrow(() -> new NotFoundException("Explotación no encontrada: " + farm.id()));
        }
        copyProfile(farm.profile(), entity);
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<Farm> findById(long id, long organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId).map(FarmPersistenceAdapter::toDomain);
    }

    @Override
    public List<Farm> findByOrganization(long organizationId) {
        return jpa.findByOrganizationIdOrderByLegalNameAsc(organizationId).stream()
                .map(FarmPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Farm> findByIds(long organizationId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByOrganizationIdAndIdIn(organizationId, ids).stream()
                .map(FarmPersistenceAdapter::toDomain).toList();
    }

    @Override
    public long countByOrganization(long organizationId) {
        return jpa.countByOrganizationId(organizationId);
    }

    @Override
    public boolean existsAny() {
        return jpa.count() > 0;
    }

    @Override
    public void delete(Farm farm) {
        jpa.findByIdAndOrganizationId(farm.id(), farm.organizationId()).ifPresent(jpa::delete);
    }

    private static void copyProfile(FarmProfile p, FarmJpaEntity e) {
        e.setLegalName(p.legalName());
        e.setTradeName(p.tradeName());
        e.setRegistrationNumber(p.registrationNumber());
        e.setAddress(p.address());
        e.setCity(p.city());
        e.setRegion(p.region());
        e.setCountry(p.country());
        e.setLatitude(p.latitude());
        e.setLongitude(p.longitude());
        e.setTotalHectares(p.totalHectares());
        e.setCrops(p.crops());
        e.setProductionAreas(p.productionAreas());
        e.setContactName(p.contactName());
        e.setContactEmail(p.contactEmail());
        e.setContactPhone(p.contactPhone());
        e.setNotes(p.notes());
    }

    private static Farm toDomain(FarmJpaEntity e) {
        FarmProfile profile = new FarmProfile(e.getLegalName(), e.getTradeName(), e.getRegistrationNumber(),
                e.getAddress(), e.getCity(), e.getRegion(), e.getCountry(), e.getLatitude(), e.getLongitude(),
                e.getTotalHectares(), e.getCrops(), e.getProductionAreas(), e.getContactName(),
                e.getContactEmail(), e.getContactPhone(), e.getNotes());
        return Farm.restore(e.getId(), e.getOrganizationId(), profile, e.getCreatedAt(), e.getUpdatedAt());
    }
}
