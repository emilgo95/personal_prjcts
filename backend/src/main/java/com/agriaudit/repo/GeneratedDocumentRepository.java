package com.agriaudit.repo;

import com.agriaudit.domain.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {
    List<GeneratedDocument> findByAuditIdOrderByGeneratedAtDesc(Long auditId);
    List<GeneratedDocument> findByOrganizationIdOrderByGeneratedAtDesc(Long organizationId);
    Optional<GeneratedDocument> findByIdAndOrganizationId(Long id, Long organizationId);
}
