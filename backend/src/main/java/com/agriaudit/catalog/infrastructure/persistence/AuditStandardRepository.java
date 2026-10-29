package com.agriaudit.catalog.infrastructure.persistence;

import com.agriaudit.catalog.domain.AuditStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditStandardRepository extends JpaRepository<AuditStandard, Long> {
    Optional<AuditStandard> findByCode(String code);
    List<AuditStandard> findByActiveTrueOrderByName();

    /** El estándar con ese código visible para la organización: el común o el suyo propio. */
    @org.springframework.data.jpa.repository.Query("""
            select s from AuditStandard s where s.code = :code
            and (s.organizationId is null or s.organizationId = :orgId)
            """)
    Optional<AuditStandard> findVisibleByCode(@org.springframework.data.repository.query.Param("code") String code,
                                              @org.springframework.data.repository.query.Param("orgId") Long organizationId);

    /** Estándares activos comunes y propios de la organización. */
    @org.springframework.data.jpa.repository.Query("""
            select s from AuditStandard s where s.active = true
            and (s.organizationId is null or s.organizationId = :orgId) order by s.name, s.version
            """)
    List<AuditStandard> findVisible(@org.springframework.data.repository.query.Param("orgId") Long organizationId);

    boolean existsByCodeAndOrganizationIdIsNull(String code);

    boolean existsByCodeAndOrganizationId(String code, Long organizationId);
}
