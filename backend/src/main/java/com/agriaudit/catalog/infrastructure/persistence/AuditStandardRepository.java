package com.agriaudit.catalog.infrastructure.persistence;

import com.agriaudit.catalog.domain.AuditStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditStandardRepository extends JpaRepository<AuditStandard, Long> {
    Optional<AuditStandard> findByCode(String code);
    List<AuditStandard> findByActiveTrueOrderByName();
}
