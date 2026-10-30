package com.agriaudit.evidence.infrastructure.persistence;

import com.agriaudit.evidence.infrastructure.persistence.EvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceTypeRepository extends JpaRepository<EvidenceType, String> {

    List<EvidenceType> findAllByOrderBySortOrderAsc();
}
