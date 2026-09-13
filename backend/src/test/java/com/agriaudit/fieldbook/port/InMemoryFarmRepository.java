package com.agriaudit.fieldbook.port;

import com.agriaudit.fieldbook.domain.Farm;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Adaptador en memoria del puerto, para tests rápidos de la capa de aplicación. */
public class InMemoryFarmRepository implements FarmRepository {

    private final Map<Long, Farm> farms = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public Farm save(Farm farm) {
        Farm stored = farm.id() == null
                ? Farm.restore(ids.incrementAndGet(), farm.organizationId(), farm.profile(), null, null)
                : Farm.restore(farm.id(), farm.organizationId(), farm.profile(), farm.createdAt(), farm.updatedAt());
        farms.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<Farm> findById(long id, long organizationId) {
        return Optional.ofNullable(farms.get(id)).filter(f -> f.belongsTo(organizationId));
    }

    @Override
    public List<Farm> findByOrganization(long organizationId) {
        return farms.values().stream().filter(f -> f.belongsTo(organizationId))
                .sorted(Comparator.comparing(f -> f.profile().legalName())).toList();
    }

    @Override
    public List<Farm> findByIds(long organizationId, Collection<Long> ids) {
        return ids.stream().map(farms::get).filter(f -> f != null && f.belongsTo(organizationId)).toList();
    }

    @Override
    public long countByOrganization(long organizationId) {
        return farms.values().stream().filter(f -> f.belongsTo(organizationId)).count();
    }

    @Override
    public boolean existsAny() {
        return !farms.isEmpty();
    }

    @Override
    public void delete(Farm farm) {
        farms.computeIfPresent(farm.id(), (id, f) -> f.belongsTo(farm.organizationId()) ? null : f);
    }
}
