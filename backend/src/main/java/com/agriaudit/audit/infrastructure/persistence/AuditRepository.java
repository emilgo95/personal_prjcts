package com.agriaudit.audit.infrastructure.persistence;

import com.agriaudit.audit.domain.Audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuditRepository extends JpaRepository<Audit, Long> {
    List<Audit> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Optional<Audit> findByIdAndOrganizationId(Long id, Long organizationId);
    long countByOrganizationId(Long organizationId);

    /** Ids de las auditorías que creó el usuario o donde figura (por nombre) como auditor líder. */
    @Query("select a.id from Audit a where a.organizationId = :orgId and (a.createdByUserId = :userId "
            + "or (:fullName is not null and lower(a.leadAuditor) = lower(:fullName)))")
    List<Long> findIdsOwnedBy(@Param("orgId") Long organizationId, @Param("userId") Long userId,
                              @Param("fullName") String fullName);
    Page<Audit> findByOrganizationId(Long organizationId, Pageable pageable);
    Page<Audit> findByOrganizationIdAndIdIn(Long organizationId, java.util.Collection<Long> ids, Pageable pageable);
    List<Audit> findByIdInAndOrganizationId(java.util.Collection<Long> ids, Long organizationId);

    /** Pares [estado, nº de auditorías] de la organización. */
    @Query("select a.status, count(a) from Audit a where a.organizationId = :orgId group by a.status")
    List<Object[]> countByStatus(@Param("orgId") Long organizationId);
}
