package com.agriaudit.evidence.infrastructure.persistence;

import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, Long> {

    List<EvidenceRecord> findByOrganizationIdOrderByIssuedOnDescIdDesc(Long organizationId);

    List<EvidenceRecord> findByOrganizationIdAndFarmIdOrderByIssuedOnDescIdDesc(Long organizationId, Long farmId);

    Optional<EvidenceRecord> findByIdAndOrganizationId(Long id, Long organizationId);
}
