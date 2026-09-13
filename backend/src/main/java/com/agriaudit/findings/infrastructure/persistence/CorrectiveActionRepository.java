package com.agriaudit.findings.infrastructure.persistence;

import com.agriaudit.findings.domain.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {
    List<CorrectiveAction> findByFindingIdOrderByIdAsc(Long findingId);
}
