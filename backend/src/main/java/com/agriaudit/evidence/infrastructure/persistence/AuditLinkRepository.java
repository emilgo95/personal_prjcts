package com.agriaudit.evidence.infrastructure.persistence;

import com.agriaudit.evidence.infrastructure.persistence.AuditEvidenceLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuditLinkRepository extends JpaRepository<AuditEvidenceLink, Long> {

    List<AuditEvidenceLink> findByAuditId(Long auditId);

    List<AuditEvidenceLink> findByRecordId(Long recordId);

    List<AuditEvidenceLink> findByOrganizationId(Long organizationId);

    List<AuditEvidenceLink> findByRecordIdIn(Collection<Long> recordIds);

    Optional<AuditEvidenceLink> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByAuditIdAndControlPointIdAndRecordId(Long auditId, Long controlPointId, Long recordId);
}
