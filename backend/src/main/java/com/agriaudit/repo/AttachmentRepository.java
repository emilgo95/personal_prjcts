package com.agriaudit.repo;

import com.agriaudit.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);
    Optional<Attachment> findByIdAndOrganizationId(Long id, Long organizationId);
}
