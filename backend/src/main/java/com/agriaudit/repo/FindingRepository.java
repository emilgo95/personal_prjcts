package com.agriaudit.repo;

import com.agriaudit.domain.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByAuditIdOrderByClassificationAscIdAsc(Long auditId);
    Optional<Finding> findByIdAndAuditId(Long id, Long auditId);
    long countByAuditId(Long auditId);

    @Query("select f from Finding f join Audit a on a.id = f.auditId where a.organizationId = :orgId")
    List<Finding> findByOrganizationId(@Param("orgId") Long orgId);
}
