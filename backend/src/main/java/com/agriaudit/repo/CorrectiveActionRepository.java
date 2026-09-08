package com.agriaudit.repo;

import com.agriaudit.domain.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {
    List<CorrectiveAction> findByFindingIdOrderByIdAsc(Long findingId);
    List<CorrectiveAction> findByFindingIdInOrderByIdAsc(List<Long> findingIds);
}
